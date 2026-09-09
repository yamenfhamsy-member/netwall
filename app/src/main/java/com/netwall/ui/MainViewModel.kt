package com.netwall.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netwall.data.AppInventory
import com.netwall.data.InstalledApp
import com.netwall.data.RulesStore
import com.netwall.data.VpnState
import com.netwall.vpn.FirewallController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppFilter { ALL, BLOCKED, SYSTEM }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RulesStore(application)
    private val inventory = AppInventory(application)

    val snapshot = store.snapshot

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Live number of apps discovered during a scan (for the loading counter). */
    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()

    private val _scanTotal = MutableStateFlow(0)
    val scanTotal: StateFlow<Int> = _scanTotal.asStateFlow()

    /** True when the last scan failed (shows retry instead of an empty list). */
    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(AppFilter.ALL)
    val filter: StateFlow<AppFilter> = _filter.asStateFlow()

    private var restartJob: Job? = null

    init {
        // Clear a stale CONNECTING left behind by a killed process.
        viewModelScope.launch {
            try {
                if (store.current().vpnState == VpnState.CONNECTING) {
                    store.setVpnState(VpnState.IDLE)
                }
            } catch (_: Exception) {
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val firstLoad = _apps.value.isEmpty()
            // Full-screen loader only on first load; later refreshes are silent
            // (icons stream in lazily per row via IconLoader).
            if (firstLoad) {
                _loading.value = true
            }
            _loadError.value = false
            try {
                val names = inventory.loadNames()
                _scanTotal.value = names.size
                // Publish progressively so the counter feels alive.
                val batch = ArrayList<InstalledApp>(names.size)
                for ((index, app) in names.withIndex()) {
                    batch.add(app)
                    if (index % 40 == 39) {
                        _apps.value = batch.toList()
                        _scanCount.value = index + 1
                    }
                }
                _apps.value = names
                _scanCount.value = names.size
                store.prune(names.map { it.packageName }.toSet())
            } catch (_: Exception) {
                if (firstLoad) {
                    _loadError.value = true
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setFilter(value: AppFilter) {
        _filter.value = value
    }

    fun toggleWifi(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            if (isConnecting()) return@launch
            store.setWifiBlocked(packageName, blocked)
            restartDebounced()
        }
    }

    fun toggleData(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            if (isConnecting()) return@launch
            store.setDataBlocked(packageName, blocked)
            restartDebounced()
        }
    }

    fun setWhitelistMode(enabled: Boolean) {
        viewModelScope.launch {
            if (isConnecting()) return@launch
            store.setWhitelistMode(enabled)
            restartDebounced()
        }
    }

    fun setShowSystem(show: Boolean) {
        viewModelScope.launch { store.setShowSystem(show) }
    }

    /** Called when the master switch is turned on and VPN permission is ready. */
    fun enableProtection() {
        viewModelScope.launch {
            store.setMasterEnabled(true)
            FirewallController.start(getApplication())
        }
    }

    fun disableProtection() {
        // Cancel any pending debounced restart first: otherwise a toggle made
        // just before switching off could resurrect the service afterwards.
        restartJob?.cancel()
        viewModelScope.launch {
            store.setMasterEnabled(false)
        }
        FirewallController.stop(getApplication())
    }

    fun finishOnboarding() {
        viewModelScope.launch { store.setOnboardingDone(true) }
    }

    /** Retry after FAILED: same path as enabling (clears fatal shutdown). */
    fun retryConnection() {
        enableProtection()
    }

    private suspend fun isConnecting(): Boolean {
        return try {
            store.current().vpnState == VpnState.CONNECTING
        } catch (_: Exception) {
            false
        }
    }
    private fun restartDebounced() {
        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            delay(800)
            val current = try {
                store.current()
            } catch (_: Exception) {
                return@launch
            }
            if (current.masterEnabled) {
                try {
                    FirewallController.start(getApplication())
                } catch (_: Exception) {
                }
            }
        }
    }
}

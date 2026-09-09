package com.netwall.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netwall.data.AppInventory
import com.netwall.data.InstalledApp
import com.netwall.data.RulesStore
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

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(AppFilter.ALL)
    val filter: StateFlow<AppFilter> = _filter.asStateFlow()

    private var restartJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val list = inventory.load()
                _apps.value = list
                store.prune(list.map { it.packageName }.toSet())
            } catch (_: Exception) {
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
            store.setWifiBlocked(packageName, blocked)
            restartDebounced()
        }
    }

    fun toggleData(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            store.setDataBlocked(packageName, blocked)
            restartDebounced()
        }
    }

    fun setWhitelistMode(enabled: Boolean) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            store.setMasterEnabled(false)
        }
        FirewallController.stop(getApplication())
    }

    fun finishOnboarding() {
        viewModelScope.launch { store.setOnboardingDone(true) }
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

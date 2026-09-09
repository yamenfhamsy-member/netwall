package com.netwall.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.rulesDataStore: DataStore<Preferences> by preferencesDataStore(name = "rules")

/**
 * Live VPN tunnel state, written by the service and observed by the UI.
 * IDLE = off · CONNECTING = establishing · CONNECTED = up · FAILED = gave up.
 */
enum class VpnState { IDLE, CONNECTING, CONNECTED, FAILED }

/**
 * Stores per-app firewall rules and global settings.
 * Uses DataStore only (no database) to keep v1 lean and reliable.
 */
class RulesStore(private val context: Context) {

    private object Keys {
        val WIFI_BLOCKED = stringSetPreferencesKey("wifi_blocked")
        val DATA_BLOCKED = stringSetPreferencesKey("data_blocked")
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val WHITELIST_MODE = booleanPreferencesKey("whitelist_mode")
        val SHOW_SYSTEM = booleanPreferencesKey("show_system")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val VPN_STATE = stringPreferencesKey("vpn_state")
    }

    data class Snapshot(
        val wifiBlocked: Set<String> = emptySet(),
        val dataBlocked: Set<String> = emptySet(),
        val masterEnabled: Boolean = false,
        val whitelistMode: Boolean = false,
        val showSystem: Boolean = false,
        val onboardingDone: Boolean = false,
        val vpnState: VpnState = VpnState.IDLE,
    )

    val snapshot: Flow<Snapshot> = context.rulesDataStore.data.map { p ->
        Snapshot(
            wifiBlocked = p[Keys.WIFI_BLOCKED] ?: emptySet(),
            dataBlocked = p[Keys.DATA_BLOCKED] ?: emptySet(),
            masterEnabled = p[Keys.MASTER_ENABLED] ?: false,
            whitelistMode = p[Keys.WHITELIST_MODE] ?: false,
            showSystem = p[Keys.SHOW_SYSTEM] ?: false,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            vpnState = runCatching {
                VpnState.valueOf(p[Keys.VPN_STATE] ?: VpnState.IDLE.name)
            }.getOrDefault(VpnState.IDLE),
        )
    }

    suspend fun current(): Snapshot = snapshot.first()

    suspend fun setWifiBlocked(packageName: String, blocked: Boolean) {
        context.rulesDataStore.edit { p ->
            val set = (p[Keys.WIFI_BLOCKED] ?: emptySet()).toMutableSet()
            if (blocked) set.add(packageName) else set.remove(packageName)
            p[Keys.WIFI_BLOCKED] = set
        }
    }

    suspend fun setDataBlocked(packageName: String, blocked: Boolean) {
        context.rulesDataStore.edit { p ->
            val set = (p[Keys.DATA_BLOCKED] ?: emptySet()).toMutableSet()
            if (blocked) set.add(packageName) else set.remove(packageName)
            p[Keys.DATA_BLOCKED] = set
        }
    }

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.rulesDataStore.edit { it[Keys.MASTER_ENABLED] = enabled }
    }

    suspend fun setWhitelistMode(enabled: Boolean) {
        context.rulesDataStore.edit { it[Keys.WHITELIST_MODE] = enabled }
    }

    suspend fun setShowSystem(show: Boolean) {
        context.rulesDataStore.edit { it[Keys.SHOW_SYSTEM] = show }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.rulesDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setVpnState(state: VpnState) {
        context.rulesDataStore.edit { it[Keys.VPN_STATE] = state.name }
    }

    /** Removes rules for packages that are no longer installed. */
    suspend fun prune(installed: Set<String>) {
        context.rulesDataStore.edit { p ->
            p[Keys.WIFI_BLOCKED] = (p[Keys.WIFI_BLOCKED] ?: emptySet()).intersect(installed)
            p[Keys.DATA_BLOCKED] = (p[Keys.DATA_BLOCKED] ?: emptySet()).intersect(installed)
        }
    }
}

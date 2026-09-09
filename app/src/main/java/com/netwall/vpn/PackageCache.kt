package com.netwall.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netwall.data.AppInventory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * In-memory cache of installed package names for the VPN service.
 * Refreshed on install/uninstall events so tunnel rebuilds never need
 * a full PackageManager scan (which used to stall the tunnel for seconds).
 */
object PackageCache {

    @Volatile var packages: Set<String> = emptySet()

    fun refresh(context: Context) {
        try {
            val fresh = AppInventory(context.applicationContext).packageNames()
            if (fresh.isNotEmpty()) {
                packages = fresh
            }
        } catch (_: Exception) {
        }
    }
}

/** Keeps [PackageCache] fresh on app install / uninstall / update. */
class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED,
            -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        PackageCache.refresh(context.applicationContext)
                    } catch (_: Exception) {
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

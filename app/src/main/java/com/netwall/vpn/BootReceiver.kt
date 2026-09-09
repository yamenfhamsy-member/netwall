package com.netwall.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.netwall.data.RulesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restarts protection after reboot when the master switch is on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = RulesStore(context.applicationContext).current().masterEnabled
                if (enabled) {
                    try {
                        FirewallController.start(context.applicationContext)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

package com.netwall.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/** Starts/stops the local firewall service. Never runs anything locally itself. */
object FirewallController {

    fun isPrepared(context: Context): Boolean = VpnService.prepare(context) == null

    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        val intent = Intent(context, FirewallVpnService::class.java)
            .setAction(FirewallVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, FirewallVpnService::class.java)
            .setAction(FirewallVpnService.ACTION_STOP)
        try {
            context.startService(intent)
        } catch (_: Exception) {
        }
    }
}

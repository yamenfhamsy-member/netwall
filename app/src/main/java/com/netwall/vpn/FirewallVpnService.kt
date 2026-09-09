package com.netwall.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.netwall.R
import com.netwall.data.AppInventory
import com.netwall.data.RulesStore
import com.netwall.ui.MainActivity
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Local on-device firewall.
 *
 * How it works (no root, no remote server):
 * - Apps the user allows on the current network are excluded from the VPN
 *   via [Builder.addDisallowedApplication], so their traffic bypasses us untouched.
 * - Everything else is routed into our TUN interface, where packets are read
 *   and discarded (sinkhole). Blocked apps simply time out.
 * - On network change (WiFi <-> mobile) the tunnel is rebuilt so the
 *   WiFi-rules vs data-rules split applies.
 */
class FirewallVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.netwall.action.START"
        const val ACTION_STOP = "com.netwall.action.STOP"
        private const val NOTIF_ID = 11
        private const val CONFLICT_NOTIF_ID = 12
        private const val CHANNEL_ID = "protection"
    }

    private val guard = Any()
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastRebuild = 0L

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            teardown()
            stopSelf()
            return Service.START_NOT_STICKY
        }
        try {
            startForegroundCompat(buildStatusNotification())
        } catch (_: Exception) {
            // Could not start in foreground (e.g. background-start restriction
            // after reboot): ask the user to open the app instead.
            postTapToResume()
            stopSelf()
            return Service.START_NOT_STICKY
        }
        registerNetworkCallbackOnce()
        restartTunnel()
        return Service.START_STICKY
    }

    override fun onRevoke() {
        teardown()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardown()
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            netCallback?.let { cm?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {
        }
        netCallback = null
        super.onDestroy()
    }

    // ---------- foreground ----------

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildStatusNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, FirewallVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(mainPendingIntent())
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun postConflict() {
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_conflict_title))
            .setContentText(getString(R.string.notif_conflict_text))
            .setContentIntent(mainPendingIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(CONFLICT_NOTIF_ID, notif)
    }

    private fun postTapToResume() {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_boot_title))
            .setContentText(getString(R.string.notif_boot_text))
            .setContentIntent(mainPendingIntent())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(CONFLICT_NOTIF_ID, notif)
    }

    // ---------- network tracking ----------

    private fun registerNetworkCallbackOnce() {
        if (netCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = requestRebuild()
            override fun onLost(network: Network) = requestRebuild()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                requestRebuild()
        }
        netCallback = callback
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            netCallback = null
        }
    }

    private fun requestRebuild() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRebuild < 2000) return
        lastRebuild = now
        restartTunnel()
    }

    // ---------- tunnel ----------

    private fun restartTunnel() {
        synchronized(guard) {
            stopWorkerLocked()
            val thread = Thread({ setupAndSink() }, "netwall-tun")
            thread.isDaemon = true
            worker = thread
            thread.start()
        }
    }

    private fun teardown() {
        synchronized(guard) { stopWorkerLocked() }
    }

    private fun stopWorkerLocked() {
        running = false
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
        try {
            worker?.interrupt()
        } catch (_: Exception) {
        }
        worker = null
    }

    private enum class NetType { WIFI, CELLULAR, OFFLINE }

    private fun currentNetType(): NetType {
        return try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return NetType.OFFLINE
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetType.OFFLINE
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.CELLULAR
                else -> NetType.OFFLINE
            }
        } catch (_: Exception) {
            NetType.OFFLINE
        }
    }

    private fun setupAndSink() {
        val store = RulesStore(applicationContext)
        val snapshot = try {
            runBlocking { store.current() }
        } catch (_: Exception) {
            stopSelf()
            return
        }
        if (!snapshot.masterEnabled) {
            stopSelf()
            return
        }

        val installed = try {
            AppInventory(applicationContext).load().map { it.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        val netType = currentNetType()
        val self = packageName
        // Packages allowed to bypass the VPN (their traffic is untouched).
        val bypass = HashSet<String>(installed.size + 1)
        bypass.add(self)
        if (snapshot.whitelistMode) {
            // Only fully-unblocked apps are allowed; everything else is sinkholed.
            val blockedAll = snapshot.wifiBlocked + snapshot.dataBlocked
            for (pkg in installed) {
                if (!blockedAll.contains(pkg)) bypass.add(pkg)
            }
        } else {
            val blockedNow = when (netType) {
                NetType.WIFI -> snapshot.wifiBlocked
                NetType.CELLULAR -> snapshot.dataBlocked
                NetType.OFFLINE -> snapshot.wifiBlocked + snapshot.dataBlocked
            }
            for (pkg in installed) {
                if (!blockedNow.contains(pkg)) bypass.add(pkg)
            }
        }

        val fd = try {
            val builder = Builder()
                .setSession("NetWall")
                .setMtu(1500)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.8.0.1")
                .setBlocking(true)
                .setConfigureIntent(mainPendingIntent())
            try {
                builder.addRoute("::", 0)
            } catch (_: Exception) {
            }
            for (pkg in bypass) {
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                }
            }
            builder.establish()
        } catch (_: Exception) {
            null
        }

        if (fd == null) {
            // Not prepared or another VPN owns the slot.
            postConflict()
            stopSelf()
            return
        }

        synchronized(guard) { tun = fd }
        running = true
        try {
            FileInputStream(fd.fileDescriptor).use { input ->
                val buffer = ByteArray(32767)
                while (running) {
                    val n = try {
                        input.read(buffer)
                    } catch (_: Exception) {
                        break
                    }
                    if (n < 0) break
                    // Discard: sinkhole. Blocked connections time out.
                    @Suppress("UNUSED_EXPRESSION")
                    ByteBuffer.wrap(buffer, 0, n.coerceAtLeast(0))
                }
            }
        } catch (_: Exception) {
        } finally {
            synchronized(guard) {
                try {
                    fd.close()
                } catch (_: Exception) {
                }
                if (tun === fd) tun = null
            }
        }
    }
}

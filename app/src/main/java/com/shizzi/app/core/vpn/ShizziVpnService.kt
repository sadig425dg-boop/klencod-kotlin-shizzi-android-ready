package com.shizzi.app.core.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.shizzi.app.MainActivity
import com.shizzi.app.R
import com.shizzi.app.ShizziApplication
import com.shizzi.app.core.model.ServerConfig
import com.shizzi.app.core.shizuku.HotspotManager
import com.shizzi.app.core.xray.V2RayCoreManager
import com.shizzi.app.core.xray.XrayConfigBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Local Android VPN tunnel: captures all device traffic through a TUN
 * interface, relays it into Xray-core's local SOCKS inbound via
 * [Tun2socksBridge], and (optionally) keeps the mobile hotspot clients on the
 * same route through [HotspotManager] ("Follow VPN" mode, powered by Shizuku).
 */
class ShizziVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val coreManager = V2RayCoreManager(object : V2RayCoreManager.Callback {
        override fun onProtectSocket(fd: Int): Boolean = protect(fd)
        override fun onCoreStateChanged(running: Boolean) {
            isTunnelRunning = running
        }
        override fun onStats(uplink: Long, downlink: Long) {
            // Could be surfaced to UI via a LocalBroadcast / StateFlow in MainActivity.
        }
    })

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            else -> {
                val serverId = intent?.getLongExtra(EXTRA_SERVER_ID, -1L) ?: -1L
                startTunnel(serverId)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(serverId: Long) {
        scope.launch {
            val app = application as ShizziApplication
            val server = if (serverId > 0) {
                app.database.serverDao().getById(serverId)
            } else {
                app.database.serverDao().getSelected()
            } ?: return@launch

            val configJson = XrayConfigBuilder.build(server)
            establishTun()
            coreManager.start(configJson)
            tunInterface?.fd?.let { fd ->
                Tun2socksBridge.start(fd, "127.0.0.1", XrayConfigBuilder.SOCKS_PORT)
            }
            startForeground(NOTIFICATION_ID, buildNotification(server))

            // "Follow VPN": tether clients are routed through the same tunnel once it's up.
            HotspotManager(applicationContext, app.shizukuBridge).onVpnStateChanged(connected = true)
        }
    }

    private fun establishTun() {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .addAddress("10.10.10.1", 32)
            .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setBlocking(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        runCatching { builder.addDisallowedApplication(packageName) }

        tunInterface = builder.establish()
    }

    private fun stopTunnel() {
        Tun2socksBridge.stop()
        coreManager.stop()
        tunInterface?.close()
        tunInterface = null
        val app = application as ShizziApplication
        HotspotManager(applicationContext, app.shizukuBridge).onVpnStateChanged(connected = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(server: ServerConfig): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ShizziVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, ShizziApplication.VPN_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("${server.name} · ${server.address}:${server.port}")
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.vpn_stop), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopTunnel()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    companion object {
        const val ACTION_STOP = "com.shizzi.app.action.STOP_VPN"
        const val EXTRA_SERVER_ID = "extra_server_id"
        private const val NOTIFICATION_ID = 42

        @Volatile
        var isTunnelRunning: Boolean = false
            private set

        fun start(context: android.content.Context, serverId: Long) {
            val intent = Intent(context, ShizziVpnService::class.java).putExtra(EXTRA_SERVER_ID, serverId)
            context.startForegroundService(intent)
        }

        fun stop(context: android.content.Context) {
            context.startService(Intent(context, ShizziVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}

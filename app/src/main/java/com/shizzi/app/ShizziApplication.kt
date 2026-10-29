package com.shizzi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.shizzi.app.core.db.AppDatabase
import com.shizzi.app.core.shizuku.ShizukuBridge
import com.shizzi.app.data.ServerRepository
import com.shizzi.app.data.SettingsRepository

class ShizziApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var serverRepository: ServerRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var shizukuBridge: ShizukuBridge
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        serverRepository = ServerRepository(database.serverDao())
        settingsRepository = SettingsRepository(this)
        shizukuBridge = ShizukuBridge(this)
        shizukuBridge.initialize()

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VPN_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_vpn),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.setShowBadge(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val VPN_NOTIFICATION_CHANNEL_ID = "shizzi_vpn_channel"
        lateinit var instance: ShizziApplication
            private set
    }
}

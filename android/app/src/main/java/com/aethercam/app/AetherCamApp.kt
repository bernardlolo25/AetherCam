package com.aethercam.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AetherCamApp : Application() {

    companion object {
        const val STREAM_NOTIFICATION_CHANNEL_ID = "aethercam_stream_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STREAM_NOTIFICATION_CHANNEL_ID,
                "AetherCam Active Stream",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status during video and audio streaming"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}

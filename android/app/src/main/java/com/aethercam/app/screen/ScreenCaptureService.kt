package com.aethercam.app.screen

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aethercam.app.AetherCamApp
import com.aethercam.app.ui.MainActivity

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.aethercam.app.screen.START"
        const val ACTION_STOP = "com.aethercam.app.screen.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private var mediaProjection: MediaProjection? = null
        private var virtualDisplay: VirtualDisplay? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultData != null) {
                    startForegroundServiceWithNotification()
                    startScreenMirroring(resultCode, resultData)
                }
            }
            ACTION_STOP -> {
                stopScreenMirroring()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, AetherCamApp.STREAM_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AetherCam Screen Stream")
            .setContentText("Screen mirroring is active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1002,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1002, notification)
        }
    }

    private fun startScreenMirroring(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        val metrics = resources.displayMetrics
        val density = metrics.densityDpi
        val width = 1920
        val height = 1080

        // In active screen mode, virtualDisplay feeds the encoder surface
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AetherCamDisplay",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null, // Bound to encoder surface in full pipeline
            null,
            null
        )
    }

    private fun stopScreenMirroring() {
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onDestroy() {
        stopScreenMirroring()
        super.onDestroy()
    }
}

package com.aethercam.app.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aethercam.app.network.NetworkTransport
import com.aethercam.app.network.UdpStreamingClient
import com.aethercam.app.audio.AudioStreamer
import com.aethercam.app.security.SecurityManager
import com.aethercam.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * CameraStreamingService
 *
 * Long-running foreground service that:
 *  - Keeps the camera/mic pipeline alive when the UI goes to background
 *  - Owns the codec loop, UDP streaming client, and audio streamer
 *  - Exposes a Binder so MainActivity can query live stats / control the stream
 *
 * Declared foregroundServiceType: camera | microphone (Android 14+ requirement)
 */
class CameraStreamingService : Service() {

    companion object {
        private const val TAG = "CameraStreamingService"
        private const val CHANNEL_ID = "aethercam_streaming"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.aethercam.app.START_STREAM"
        const val ACTION_STOP  = "com.aethercam.app.STOP_STREAM"

        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
    }

    // ── Binder ───────────────────────────────────────────────────────────────
    inner class StreamBinder : Binder() {
        fun getService(): CameraStreamingService = this@CameraStreamingService
    }

    private val binder = StreamBinder()

    // ── Coroutine scope (survives config changes) ─────────────────────────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Live objects (created on demand) ─────────────────────────────────────
    private var videoEncoder: VideoEncoder? = null
    private var audioStreamer: AudioStreamer? = null
    private var udpClient: UdpStreamingClient? = null
    private var streamJob: Job? = null

    // ── Public stats (polled by MainActivity via binder) ─────────────────────
    var currentBitrateKbps: Int = 0
        private set
    var currentFps: Float = 0f
        private set
    var totalFramesSent: Long = 0L
        private set

    // ─────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: "192.168.1.100"
                val port = intent.getIntExtra(EXTRA_PORT, 48200)
                startForeground(NOTIFICATION_ID, buildNotification("Streaming to $host:$port"))
                beginStream(host, port)
            }
            ACTION_STOP -> {
                stopStream()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        stopStream()
        serviceScope.cancel()
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stream control
    // ─────────────────────────────────────────────────────────────────────────

    private fun beginStream(host: String, port: Int) {
        if (streamJob?.isActive == true) return

        streamJob = serviceScope.launch {
            try {
                Log.i(TAG, "Starting AetherCam stream → $host:$port")

                udpClient = UdpStreamingClient(host, port).also { it.connect() }

                audioStreamer = AudioStreamer(
                    sampleRate    = 48_000,
                    channelConfig = android.media.AudioFormat.CHANNEL_IN_STEREO,
                    onPacket      = { packet -> udpClient?.sendAudio(packet) }
                ).also { it.start() }

                // Note: VideoEncoder is started from MainActivity via CameraManager
                // (needs a camera Surface). We just track stats here.
                statsLoop()

            } catch (e: Exception) {
                Log.e(TAG, "Stream error: ${e.message}", e)
            }
        }
    }

    private fun stopStream() {
        streamJob?.cancel()
        audioStreamer?.stop()
        udpClient?.disconnect()
        audioStreamer = null
        udpClient = null
        Log.i(TAG, "Stream stopped")
    }

    private suspend fun statsLoop() {
        while (streamJob?.isActive == true) {
            val snapshot = udpClient?.getStats()
            currentBitrateKbps = snapshot?.bitrateKbps ?: 0
            currentFps         = snapshot?.fps ?: 0f
            totalFramesSent    = snapshot?.framesSent ?: 0L

            // Update the persistent notification with live stats
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(
                "${currentFps.toInt()} fps · ${currentBitrateKbps} kbps"
            ))

            kotlinx.coroutines.delay(2_000)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AetherCam Live Stream",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while AetherCam is actively streaming"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AetherCam • Streaming")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}

package com.aethercam.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*

/**
 * AudioStreamer
 *
 * Records PCM audio from the microphone and delivers 20 ms frames to the
 * supplied callback.  Supports three usage patterns:
 *
 *  1. ViewModel usage (lambda-only constructor):
 *       AudioStreamer { pcmData, timestampUs -> ... }
 *
 *  2. Full config usage:
 *       AudioStreamer(sampleRate = 48_000, isStereo = true) { pcmData, timestampUs -> ... }
 *
 *  3. CameraStreamingService usage (channel config + single-arg callback):
 *       AudioStreamer(sampleRate, channelConfig, onPacket = { bytes -> ... })
 */
class AudioStreamer(
    private val sampleRate: Int    = 48_000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_STEREO,
    private val onChunk: (pcm: ByteArray, timestampUs: Long) -> Unit
) {
    companion object {
        private const val TAG = "AudioStreamer"

        /** Convenience: isStereo flag instead of raw channelConfig constant. */
        operator fun invoke(
            sampleRate: Int = 48_000,
            isStereo: Boolean = true,
            onAudioChunkReady: (pcmData: ByteArray, timestampUs: Long) -> Unit
        ): AudioStreamer = AudioStreamer(
            sampleRate    = sampleRate,
            channelConfig = if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
            onChunk       = onAudioChunkReady
        )

        /** CameraStreamingService convenience – single-arg packet callback. */
        operator fun invoke(
            sampleRate: Int,
            channelConfig: Int,
            onPacket: (ByteArray) -> Unit
        ): AudioStreamer = AudioStreamer(
            sampleRate    = sampleRate,
            channelConfig = channelConfig,
            onChunk       = { pcm, _ -> onPacket(pcm) }
        )
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private var audioRecord: AudioRecord? = null
    private var effectsProcessor: AudioEffectsProcessor? = null
    private var recordingJob: Job? = null
    @Volatile private var isRecording = false

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** ViewModel usage: start(viewModelScope). */
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) = beginRecording(scope)

    /** Service usage: start() — creates its own supervised IO scope. */
    @SuppressLint("MissingPermission")
    fun start() = beginRecording(CoroutineScope(SupervisorJob() + Dispatchers.IO))

    fun stop() {
        if (!isRecording) return
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            effectsProcessor?.release()
            effectsProcessor = null
        }
        Log.i(TAG, "AudioStreamer stopped")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun beginRecording(scope: CoroutineScope) {
        if (isRecording) return

        val audioFmt   = AudioFormat.ENCODING_PCM_16BIT
        val minBuf     = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFmt)
        val bufferSize = (minBuf * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFmt,
                bufferSize
            )

            val sessionId = audioRecord?.audioSessionId ?: 0
            effectsProcessor = AudioEffectsProcessor(sessionId).apply {
                enableHardwareEffects()
            }

            audioRecord?.startRecording()
            isRecording = true

            val isStereo   = (channelConfig == AudioFormat.CHANNEL_IN_STEREO)
            val chunkBytes = 960 * if (isStereo) 4 else 2 // 20 ms @ 48 kHz

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(chunkBytes)
                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, chunkBytes) ?: -1
                    if (read > 0) {
                        val ts   = System.nanoTime() / 1000L
                        onChunk(buffer.copyOf(read), ts)
                    }
                }
            }

            Log.i(TAG, "AudioStreamer started ($sampleRate Hz, stereo=$isStereo)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
            stop()
        }
    }
}

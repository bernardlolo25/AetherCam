package com.aethercam.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*

class AudioStreamer(
    private val sampleRate: Int = 48000,
    private val isStereo: Boolean = true,
    private val onAudioChunkReady: (pcmData: ByteArray, timestampUs: Long) -> Unit
) {
    companion object {
        private const val TAG = "AudioStreamer"
    }

    private var audioRecord: AudioRecord? = null
    private var effectsProcessor: AudioEffectsProcessor? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isRecording) return

        val channelConfig = if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            val sessionId = audioRecord?.audioSessionId ?: 0
            effectsProcessor = AudioEffectsProcessor(sessionId).apply {
                enableHardwareEffects()
            }

            audioRecord?.startRecording()
            isRecording = true

            // Read loop in dedicated IO thread
            recordingJob = scope.launch(Dispatchers.IO) {
                val chunkSize = 960 * (if (isStereo) 4 else 2) // 20ms audio frame
                val buffer = ByteArray(chunkSize)

                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, chunkSize) ?: -1
                    if (read > 0) {
                        val timestampUs = System.nanoTime() / 1000
                        val copy = ByteArray(read)
                        System.arraycopy(buffer, 0, copy, 0, read)
                        onAudioChunkReady(copy, timestampUs)
                    }
                }
            }

            Log.i(TAG, "AudioStreamer recording started ($sampleRate Hz, stereo=$isStereo)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
            stop()
        }
    }

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
    }
}

package com.aethercam.app.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

class AudioEffectsProcessor(private val audioSessionId: Int) {

    companion object {
        private const val TAG = "AudioEffectsProcessor"
    }

    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    fun enableHardwareEffects() {
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                enabled = true
                Log.i(TAG, "Hardware AcousticEchoCanceler activated")
            }
        }

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                enabled = true
                Log.i(TAG, "Hardware NoiseSuppressor activated")
            }
        }

        if (AutomaticGainControl.isAvailable()) {
            gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                enabled = true
                Log.i(TAG, "Hardware AutomaticGainControl activated")
            }
        }
    }

    fun release() {
        echoCanceler?.release()
        noiseSuppressor?.release()
        gainControl?.release()
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
    }
}

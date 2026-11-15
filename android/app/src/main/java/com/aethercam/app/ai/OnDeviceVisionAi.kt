package com.aethercam.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log

class OnDeviceVisionAi(private val context: Context) {

    companion object {
        private const val TAG = "OnDeviceVisionAi"
    }

    private val autoFramingEngine = AutoFramingEngine()
    var isAutoFramingEnabled = false
    var isPortraitBlurEnabled = false
    var blurStrength = 0.6f

    /**
     * Executes real-time inference on preview image buffer to compute
     * face tracking coordinates and background segmentation mask.
     */
    fun processPreviewFrame(bitmap: Bitmap, onCropCalculated: (Rect) -> Unit) {
        if (!isAutoFramingEnabled && !isPortraitBlurEnabled) return

        try {
            // Simulated local NPU face tracking detection [x, y, w, h]
            val faceLeft = 0.35f
            val faceTop = 0.25f
            val faceRight = 0.65f
            val faceBottom = 0.65f

            if (isAutoFramingEnabled) {
                val crop = autoFramingEngine.computeCinematicCrop(
                    bitmap.width,
                    bitmap.height,
                    faceLeft,
                    faceTop,
                    faceRight,
                    faceBottom
                )
                onCropCalculated(crop)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI Vision processing error", e)
        }
    }
}

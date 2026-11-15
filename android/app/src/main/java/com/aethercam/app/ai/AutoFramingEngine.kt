package com.aethercam.app.ai

import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

class AutoFramingEngine {

    private var currentCropRect = Rect(0, 0, 1920, 1080)
    private val smoothingFactor = 0.12f

    /**
     * Given detected face bounding box normalized [0.0..1.0], calculates
     * the optimal cinematic crop region to center the user with head room.
     */
    fun computeCinematicCrop(
        sensorWidth: Int,
        sensorHeight: Int,
        faceLeft: Float,
        faceTop: Float,
        faceRight: Float,
        faceBottom: Float
    ): Rect {
        val faceCenterX = (faceLeft + faceRight) / 2f * sensorWidth
        val faceCenterY = (faceTop + faceBottom) / 2f * sensorHeight
        val faceHeightPx = (faceBottom - faceTop) * sensorHeight

        // Rule of thirds framing: head positioned in upper third of image
        val targetFrameHeight = (faceHeightPx * 2.8f).coerceIn(sensorHeight * 0.45f, sensorHeight.toFloat())
        val targetFrameWidth = targetFrameHeight * (sensorWidth.toFloat() / sensorHeight)

        val targetLeft = (faceCenterX - targetFrameWidth / 2f).toInt().coerceIn(0, (sensorWidth - targetFrameWidth).toInt())
        val targetTop = (faceCenterY - targetFrameHeight * 0.38f).toInt().coerceIn(0, (sensorHeight - targetFrameHeight).toInt())
        val targetRight = (targetLeft + targetFrameWidth).toInt().coerceAtMost(sensorWidth)
        val targetBottom = (targetTop + targetFrameHeight).toInt().coerceAtMost(sensorHeight)

        // Exponential smoothing for fluid pan & zoom
        val smoothedLeft = (currentCropRect.left + (targetLeft - currentCropRect.left) * smoothingFactor).toInt()
        val smoothedTop = (currentCropRect.top + (targetTop - currentCropRect.top) * smoothingFactor).toInt()
        val smoothedRight = (currentCropRect.right + (targetRight - currentCropRect.right) * smoothingFactor).toInt()
        val smoothedBottom = (currentCropRect.bottom + (targetBottom - currentCropRect.bottom) * smoothingFactor).toInt()

        currentCropRect = Rect(smoothedLeft, smoothedTop, smoothedRight, smoothedBottom)
        return currentCropRect
    }
}

package com.aethercam.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class AetherCameraManager(
    private val context: Context,
    private val onFrameEncoded: (data: ByteArray, isKeyframe: Boolean, timestampUs: Long) -> Unit
) {
    companion object {
        private const val TAG = "AetherCameraManager"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var videoEncoder: VideoEncoder? = null

    private var currentConfig = CameraCaptureConfig()
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraOpenCloseLock = Semaphore(1)
    private var previewSurface: Surface? = null

    private var activeCameraId: String = "0"
    private val availableCameraLenses = mutableMapOf<LensType, String>()

    init {
        detectCameraSensors()
    }

    private fun detectCameraSensors() {
        try {
            var firstBackId: String? = null
            var firstFrontId: String? = null

            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (firstFrontId == null) firstFrontId = id
                    availableCameraLenses[LensType.FRONT] = id
                } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (firstBackId == null) firstBackId = id
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val focalLength = focalLengths?.firstOrNull() ?: 4.0f

                    if (focalLength < 2.5f) {
                        availableCameraLenses[LensType.ULTRA_WIDE] = id
                    } else if (focalLength > 6.0f) {
                        availableCameraLenses[LensType.TELEPHOTO] = id
                    } else {
                        availableCameraLenses[LensType.WIDE] = id
                    }
                }
            }

            // Fallback safety: ensure WIDE is mapped to primary back camera
            if (availableCameraLenses[LensType.WIDE] == null && firstBackId != null) {
                availableCameraLenses[LensType.WIDE] = firstBackId
            }
            if (availableCameraLenses[LensType.FRONT] == null && firstFrontId != null) {
                availableCameraLenses[LensType.FRONT] = firstFrontId
            }
            Log.i(TAG, "Detected Physical Camera Lenses: $availableCameraLenses")
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering camera hardware capabilities", e)
        }
    }

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("AetherCamBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    fun stopBackgroundThread() {
        try {
            backgroundThread?.quitSafely()
            backgroundThread?.join(500)
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startCapture(config: CameraCaptureConfig, surface: Surface?) {
        if (cameraDevice != null && captureSession != null) {
            Log.i(TAG, "Camera is already actively capturing; applying settings directly")
            currentConfig = config
            if (surface != null && surface.isValid) {
                previewSurface = surface
            }
            applyCaptureRequestSettings()
            return
        }

        currentConfig = config
        previewSurface = surface
        startBackgroundThread()

        val targetId = availableCameraLenses[config.lens]
            ?: availableCameraLenses[LensType.WIDE]
            ?: cameraManager.cameraIdList.firstOrNull()
            ?: "0"
        activeCameraId = targetId

        // Initialize low-latency encoder for network streaming (non-blocking if encoder fails)
        try {
            videoEncoder?.stop()
            videoEncoder = VideoEncoder(
                width = config.resolution.width,
                height = config.resolution.height,
                fps = config.targetFps,
                bitrateBps = config.bitrateBps,
                codecType = config.codec,
                onEncodedFrame = onFrameEncoded
            )
            videoEncoder?.start()
        } catch (e: Exception) {
            Log.e(TAG, "VideoEncoder initialization warning, proceeding with UI preview: ${e.message}")
        }

        val encoderSurface = videoEncoder?.inputSurface

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Camera open lock timeout, resetting lock")
                try { cameraOpenCloseLock.release() } catch (e: Exception) {}
            }

            cameraManager.openCamera(targetId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try { cameraOpenCloseLock.release() } catch (e: Exception) {}
                    cameraDevice = camera
                    createCaptureSession(camera, encoderSurface, previewSurface)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    try { cameraOpenCloseLock.release() } catch (e: Exception) {}
                    try { camera.close() } catch (e: Exception) {}
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    try { cameraOpenCloseLock.release() } catch (e: Exception) {}
                    try { camera.close() } catch (e: Exception) {}
                    cameraDevice = null
                    Log.e(TAG, "Camera open error code: $error")
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Exception opening Camera2", e)
        }
    }

    private fun createCaptureSession(
        camera: CameraDevice,
        encoderSurface: Surface?,
        uiPreviewSurface: Surface?
    ) {
        try {
            val targets = mutableListOf<Surface>()
            if (encoderSurface != null && encoderSurface.isValid) {
                targets.add(encoderSurface)
            }
            if (uiPreviewSurface != null && uiPreviewSurface.isValid) {
                targets.add(uiPreviewSurface)
            }

            if (targets.isEmpty()) {
                Log.w(TAG, "No valid surfaces to configure camera capture session")
                return
            }

            camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    applyCaptureRequestSettings()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Failed to configure camera capture session")
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Exception creating capture session", e)
        }
    }

    fun updateSettings(updates: (CameraCaptureConfig) -> CameraCaptureConfig) {
        val oldLens = currentConfig.lens
        currentConfig = updates(currentConfig)

        if (currentConfig.lens != oldLens) {
            // Lens switch requires reopening the specific physical sensor
            stopCapture()
            startCapture(currentConfig, previewSurface)
        } else {
            applyCaptureRequestSettings()
        }
    }

    private fun applyCaptureRequestSettings() {
        val session = captureSession ?: return
        val camera = cameraDevice ?: return
        val encoderSurface = videoEncoder?.inputSurface

        try {
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                if (encoderSurface != null && encoderSurface.isValid) {
                    addTarget(encoderSurface)
                }
                if (previewSurface != null && previewSurface!!.isValid) {
                    addTarget(previewSurface!!)
                }

                // Optical Zoom or Sensor Crop
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        set(CaptureRequest.CONTROL_ZOOM_RATIO, currentConfig.zoomRatio)
                    } catch (e: Exception) {
                        Log.w(TAG, "Zoom ratio not supported: ${e.message}")
                    }
                }

                // Exposure & Auto modes
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)

                if (currentConfig.iso > 0 && currentConfig.exposureTimeNs > 0) {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    set(CaptureRequest.SENSOR_SENSITIVITY, currentConfig.iso)
                    set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentConfig.exposureTimeNs)
                } else {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }

                // White Balance
                if (currentConfig.whiteBalanceKelvin > 0) {
                    set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                } else {
                    set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                }

                // Flashlight / Torch
                set(
                    CaptureRequest.FLASH_MODE,
                    if (currentConfig.torchEnabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
                )

                // Stabilization
                if (currentConfig.stabilization) {
                    try {
                        set(
                            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                        )
                    } catch (e: Exception) {}
                }
            }

            session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
            Log.i(TAG, "Camera repeating capture request started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply capture request parameters", e)
        }
    }

    fun requestKeyframe() {
        videoEncoder?.requestKeyframe()
    }

    fun setDynamicBitrate(bitrateBps: Int) {
        videoEncoder?.setDynamicBitrate(bitrateBps)
    }

    fun stopCapture() {
        try {
            cameraOpenCloseLock.tryAcquire(1000, TimeUnit.MILLISECONDS)
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            videoEncoder?.stop()
            videoEncoder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera capture", e)
        } finally {
            try { cameraOpenCloseLock.release() } catch (e: Exception) {}
            stopBackgroundThread()
        }
    }
}

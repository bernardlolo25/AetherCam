package com.aethercam.app.camera

enum class LensType(val displayName: String) {
    FRONT("Front TrueDepth / Wide"),
    WIDE("Main Wide (1.0x)"),
    ULTRA_WIDE("Ultra-Wide (0.5x)"),
    TELEPHOTO("Telephoto (3.0x - 10.0x)")
}

enum class VideoCodec(val mimeType: String) {
    H264("video/avc"),
    H265_HEVC("video/hevc")
}

data class StreamResolution(
    val width: Int,
    val height: Int,
    val label: String
) {
    companion object {
        val RES_4K = StreamResolution(3840, 2160, "4K UHD")
        val RES_1080P = StreamResolution(1920, 1080, "1080p FHD")
        val RES_720P = StreamResolution(1280, 720, "720p HD")
        val RES_480P = StreamResolution(640, 480, "480p SD")
    }
}

data class CameraCaptureConfig(
    val lens: LensType = LensType.WIDE,
    val resolution: StreamResolution = StreamResolution.RES_1080P,
    val targetFps: Int = 60,
    val bitrateBps: Int = 12_000_000, // 12 Mbps default
    val codec: VideoCodec = VideoCodec.H264,
    val zoomRatio: Float = 1.0f,
    val torchEnabled: Boolean = false,
    val iso: Int = 0, // 0 = Auto
    val exposureTimeNs: Long = 0L, // 0 = Auto
    val whiteBalanceKelvin: Int = 0, // 0 = Auto
    val manualFocusDistance: Float = -1.0f, // -1.0 = Continuous AF
    val stabilization: Boolean = true,
    val hdr: Boolean = true
)

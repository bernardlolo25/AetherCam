package com.aethercam.app.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrateBps: Int,
    private val codecType: VideoCodec,
    private val onEncodedFrame: (data: ByteArray, isKeyframe: Boolean, presentationTimestampUs: Long) -> Unit
) {
    companion object {
        private const val TAG = "AetherVideoEncoder"
    }

    private var mediaCodec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    private var spsPpsBuffer: ByteArray? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        try {
            val format = MediaFormat.createVideoFormat(codecType.mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                
                // Low-latency configuration
                setInteger(MediaFormat.KEY_PRIORITY, 0) // Real-time priority
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_LATENCY, 0) // Low latency
                }

                if (codecType == VideoCodec.H264) {
                    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51)
                }
            }

            mediaCodec = MediaCodec.createEncoderByType(codecType.mimeType).apply {
                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        // Input is fed automatically via inputSurface
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {
                        val outputBuffer = codec.getOutputBuffer(index) ?: return

                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            // Cache SPS/PPS parameter sets
                            val configBytes = ByteArray(info.size)
                            outputBuffer.position(info.offset)
                            outputBuffer.get(configBytes, 0, info.size)
                            spsPpsBuffer = configBytes
                            codec.releaseOutputBuffer(index, false)
                            return
                        }

                        val isKeyframe = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                        outputBuffer.position(info.offset)
                        val frameBytes = ByteArray(info.size)
                        outputBuffer.get(frameBytes, 0, info.size)

                        if (isKeyframe && spsPpsBuffer != null) {
                            // Prepend SPS/PPS to IDR frame for decoder validation
                            val combined = ByteArray(spsPpsBuffer!!.size + frameBytes.size)
                            System.arraycopy(spsPpsBuffer!!, 0, combined, 0, spsPpsBuffer!!.size)
                            System.arraycopy(frameBytes, 0, combined, spsPpsBuffer!!.size, frameBytes.size)
                            onEncodedFrame(combined, true, info.presentationTimeUs)
                        } else {
                            onEncodedFrame(frameBytes, isKeyframe, info.presentationTimeUs)
                        }

                        codec.releaseOutputBuffer(index, false)
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Log.e(TAG, "MediaCodec Exception: ${e.message}", e)
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        Log.i(TAG, "MediaCodec Format Changed: $format")
                    }
                })

                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            isRunning = true
            Log.i(TAG, "MediaCodec low-latency encoder started ($width x $height @ $fps fps, $bitrateBps bps)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VideoEncoder", e)
            stop()
        }
    }

    fun requestKeyframe() {
        val bundle = Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        }
        mediaCodec?.setParameters(bundle)
    }

    fun setDynamicBitrate(targetBitrateBps: Int) {
        val bundle = Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, targetBitrateBps)
        }
        mediaCodec?.setParameters(bundle)
        Log.i(TAG, "Dynamic Bitrate adapted to: $targetBitrateBps bps")
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaCodec", e)
        } finally {
            mediaCodec = null
            inputSurface?.release()
            inputSurface = null
        }
    }
}

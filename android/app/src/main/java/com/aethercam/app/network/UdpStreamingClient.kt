package com.aethercam.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * UdpStreamingClient
 *
 * Sends ABSP-framed video/audio packets over UDP with inline XOR FEC.
 * Construction accepts host+port so CameraStreamingService can instantiate
 * it without calling a separate connect() with parameters.
 */
class UdpStreamingClient(
    private val host: String = "192.168.1.100",
    private val port: Int    = 48200
) {
    companion object {
        private const val TAG           = "UdpStreamingClient"
        private const val FEC_GROUP_SIZE = 8
    }

    // ── Socket ───────────────────────────────────────────────────────────────
    private var socket: DatagramSocket? = null
    private var destinationAddress: InetAddress? = null

    // ── Stats counters ───────────────────────────────────────────────────────
    private val sequenceCounter  = AtomicInteger(0)
    private val bytesSentCounter = AtomicLong(0L)
    private val framesSentCounter = AtomicLong(0L)
    private var statsWindowStart = System.currentTimeMillis()
    private var bytesInWindow    = AtomicLong(0L)

    private val fecPacketBuffer  = mutableListOf<ByteArray>()

    // ─────────────────────────────────────────────────────────────────────────
    // Connection
    // ─────────────────────────────────────────────────────────────────────────

    /** Open the UDP socket toward host:port supplied at construction time. */
    fun connect() {
        disconnect()
        try {
            socket = DatagramSocket()
            destinationAddress = InetAddress.getByName(host)
            Log.i(TAG, "UdpStreamingClient connected → $host:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open DatagramSocket", e)
        }
    }

    /** Legacy overload kept for callers that supply host+port explicitly. */
    fun connect(h: String, p: Int) {
        disconnect()
        try {
            socket = DatagramSocket()
            destinationAddress = InetAddress.getByName(h)
            Log.i(TAG, "UdpStreamingClient connected → $h:$p (explicit)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open DatagramSocket", e)
        }
    }

    fun disconnect() = close()

    // ─────────────────────────────────────────────────────────────────────────
    // Sending
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun sendMediaFrame(
        payload: ByteArray,
        packetType: Byte,
        isKeyframe: Boolean,
        timestampUs: Long
    ) = withContext(Dispatchers.IO) {
        val sock = socket ?: return@withContext
        val addr = destinationAddress ?: return@withContext

        val chunkSize      = AbspConstants.MAX_PAYLOAD_CHUNK
        val totalFragments = ((payload.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)

        for (i in 0 until totalFragments) {
            val offset = i * chunkSize
            val length = (payload.size - offset).coerceAtMost(chunkSize)
            val chunk  = payload.copyOfRange(offset, offset + length)

            var flags = 0.toByte()
            if (isKeyframe)         flags = (flags.toInt() or AbspConstants.FLAG_KEYFRAME.toInt()).toByte()
            if (totalFragments > 1) flags = (flags.toInt() or AbspConstants.FLAG_FRAGMENTED.toInt()).toByte()
            if (totalFragments > 1 && i == totalFragments - 1)
                                    flags = (flags.toInt() or AbspConstants.FLAG_LAST_FRAGMENT.toInt()).toByte()

            val seq = (sequenceCounter.getAndIncrement() and 0xFFFF).toShort()
            val pkt = AbspPacket(
                packetType     = packetType,
                flags          = flags,
                channel        = if (packetType == AbspConstants.PKT_TYPE_AUDIO) 1 else 0,
                sequenceNumber = seq,
                timestampUs    = (timestampUs and 0xFFFFFFFFL).toInt(),
                fragmentIndex  = if (totalFragments > 1) i.toShort() else null,
                totalFragments = if (totalFragments > 1) totalFragments.toShort() else null,
                payload        = chunk
            )

            val serialized = pkt.serialize()
            sock.send(DatagramPacket(serialized, serialized.size, addr, port))

            bytesSentCounter.addAndGet(serialized.size.toLong())
            bytesInWindow.addAndGet(serialized.size.toLong())

            fecPacketBuffer.add(serialized)
            if (fecPacketBuffer.size >= FEC_GROUP_SIZE) {
                dispatchFecParity(sock, addr, timestampUs)
            }
        }
        framesSentCounter.incrementAndGet()
    }

    /** Send a raw pre-encoded audio PCM/Opus packet. */
    suspend fun sendAudio(packet: ByteArray) = sendMediaFrame(
        payload    = packet,
        packetType = AbspConstants.PKT_TYPE_AUDIO,
        isKeyframe = false,
        timestampUs = System.nanoTime() / 1000L
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Stats
    // ─────────────────────────────────────────────────────────────────────────

    data class Stats(val bitrateKbps: Int, val fps: Float, val framesSent: Long)

    /** Returns a snapshot of current streaming statistics. */
    fun getStats(): Stats {
        val now     = System.currentTimeMillis()
        val elapsed = (now - statsWindowStart).coerceAtLeast(1L)
        val bytes   = bytesInWindow.getAndSet(0L)
        statsWindowStart = now

        val bitrateKbps = ((bytes * 8L * 1000L) / elapsed / 1024L).toInt()
        // FPS approximated from frames sent in window
        val fps = (framesSentCounter.get() * 1000f / elapsed).coerceAtMost(120f)
        return Stats(bitrateKbps, fps, framesSentCounter.get())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FEC
    // ─────────────────────────────────────────────────────────────────────────

    private fun dispatchFecParity(sock: DatagramSocket, addr: InetAddress, timestampUs: Long) {
        val parityBytes = FecHelper.createParity(fecPacketBuffer)
        fecPacketBuffer.clear()

        val seq = (sequenceCounter.getAndIncrement() and 0xFFFF).toShort()
        val fecPkt = AbspPacket(
            packetType     = AbspConstants.PKT_TYPE_FEC_PARITY,
            flags          = AbspConstants.FLAG_FEC_PARITY,
            channel        = 0,
            sequenceNumber = seq,
            timestampUs    = (timestampUs and 0xFFFFFFFFL).toInt(),
            payload        = parityBytes
        )
        val serialized = fecPkt.serialize()
        try {
            sock.send(DatagramPacket(serialized, serialized.size, addr, port))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send FEC parity", e)
        }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        destinationAddress = null
        fecPacketBuffer.clear()
    }
}

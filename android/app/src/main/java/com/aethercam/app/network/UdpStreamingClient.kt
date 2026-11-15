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
 * Completely hardened with mutex-protected FEC buffer and resilient socket exception handling.
 */
class UdpStreamingClient(
    private val host: String = "192.168.1.100",
    private val port: Int    = 48200
) {
    companion object {
        private const val TAG           = "UdpStreamingClient"
        private const val FEC_GROUP_SIZE = 8
    }

    // ── Socket & Address ──────────────────────────────────────────────────────
    private var socket: DatagramSocket? = null
    private var destinationAddress: InetAddress? = null
    private var targetHost: String = host
    private var targetPort: Int = port

    // ── Stats counters ───────────────────────────────────────────────────────
    private val sequenceCounter  = AtomicInteger(0)
    private val bytesSentCounter = AtomicLong(0L)
    private val framesSentCounter = AtomicLong(0L)
    private var statsWindowStart = System.currentTimeMillis()
    private var bytesInWindow    = AtomicLong(0L)

    private val fecPacketBuffer  = mutableListOf<ByteArray>()
    private val fecLock = Any()

    // ─────────────────────────────────────────────────────────────────────────
    // Connection
    // ─────────────────────────────────────────────────────────────────────────

    /** Open the UDP socket toward host:port supplied at construction time. */
    fun connect() {
        connect(targetHost, targetPort)
    }

    /** Connect toward explicitly specified host and port. */
    fun connect(h: String, p: Int) {
        disconnect()
        try {
            targetHost = h.trim()
            targetPort = p
            socket = DatagramSocket()
            destinationAddress = InetAddress.getByName(targetHost)
            Log.i(TAG, "UdpStreamingClient connected → $targetHost:$targetPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open DatagramSocket to $h:$p", e)
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

        try {
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

                // Safe socket send: never throw or crash on transient network/firewall drops
                try {
                    sock.send(DatagramPacket(serialized, serialized.size, addr, targetPort))
                } catch (e: Exception) {
                    Log.w(TAG, "Socket send dropped frame fragment: ${e.message}")
                    return@withContext
                }

                bytesSentCounter.addAndGet(serialized.size.toLong())
                bytesInWindow.addAndGet(serialized.size.toLong())

                synchronized(fecLock) {
                    fecPacketBuffer.add(serialized)
                    if (fecPacketBuffer.size >= FEC_GROUP_SIZE) {
                        dispatchFecParity(sock, addr, timestampUs)
                    }
                }
            }
            framesSentCounter.incrementAndGet()
        } catch (e: Exception) {
            Log.w(TAG, "sendMediaFrame suppressed error: ${e.message}")
        }
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
            sock.send(DatagramPacket(serialized, serialized.size, addr, targetPort))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send FEC parity: ${e.message}")
        }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        destinationAddress = null
        synchronized(fecLock) {
            fecPacketBuffer.clear()
        }
    }
}

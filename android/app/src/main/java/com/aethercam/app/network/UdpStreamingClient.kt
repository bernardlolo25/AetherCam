package com.aethercam.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

class UdpStreamingClient {

    companion object {
        private const val TAG = "UdpStreamingClient"
        private const val FEC_GROUP_SIZE = 8
    }

    private var socket: DatagramSocket? = null
    private var destinationAddress: InetAddress? = null
    private var destinationPort: Int = 48200

    private val sequenceCounter = AtomicInteger(0)
    private val fecPacketBuffer = mutableListOf<ByteArray>()

    fun connect(host: String, port: Int) {
        close()
        try {
            socket = DatagramSocket()
            destinationAddress = InetAddress.getByName(host)
            destinationPort = port
            Log.i(TAG, "UdpStreamingClient initialized targeting $host:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create DatagramSocket", e)
        }
    }

    suspend fun sendMediaFrame(
        payload: ByteArray,
        packetType: Byte,
        isKeyframe: Boolean,
        timestampUs: Long
    ) = withContext(Dispatchers.IO) {
        val sock = socket ?: return@withContext
        val addr = destinationAddress ?: return@withContext

        val chunkSize = AbspConstants.MAX_PAYLOAD_CHUNK
        val totalFragments = ((payload.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)

        for (i in 0 until totalFragments) {
            val offset = i * chunkSize
            val length = (payload.size - offset).coerceAtMost(chunkSize)
            val chunk = ByteArray(length)
            System.arraycopy(payload, offset, chunk, 0, length)

            var flags = 0.toByte()
            if (isKeyframe) flags = (flags.toInt() or AbspConstants.FLAG_KEYFRAME.toInt()).toByte()
            if (totalFragments > 1) {
                flags = (flags.toInt() or AbspConstants.FLAG_FRAGMENTED.toInt()).toByte()
                if (i == totalFragments - 1) {
                    flags = (flags.toInt() or AbspConstants.FLAG_LAST_FRAGMENT.toInt()).toByte()
                }
            }

            val seq = (sequenceCounter.getAndIncrement() and 0xFFFF).toShort()
            val pkt = AbspPacket(
                packetType = packetType,
                flags = flags,
                channel = if (packetType == AbspConstants.PKT_TYPE_AUDIO) 1 else 0,
                sequenceNumber = seq,
                timestampUs = (timestampUs and 0xFFFFFFFFL).toInt(),
                fragmentIndex = if (totalFragments > 1) i.toShort() else null,
                totalFragments = if (totalFragments > 1) totalFragments.toShort() else null,
                payload = chunk
            )

            val serialized = pkt.serialize()
            val datagram = DatagramPacket(serialized, serialized.size, addr, destinationPort)
            sock.send(datagram)

            // Accumulate for inline XOR FEC
            fecPacketBuffer.add(serialized)
            if (fecPacketBuffer.size >= FEC_GROUP_SIZE) {
                dispatchFecParity(sock, addr, timestampUs)
            }
        }
    }

    private fun dispatchFecParity(sock: DatagramSocket, addr: InetAddress, timestampUs: Long) {
        val parityBytes = FecHelper.createParity(fecPacketBuffer)
        fecPacketBuffer.clear()

        val seq = (sequenceCounter.getAndIncrement() and 0xFFFF).toShort()
        val fecPkt = AbspPacket(
            packetType = AbspConstants.PKT_TYPE_FEC_PARITY,
            flags = AbspConstants.FLAG_FEC_PARITY,
            channel = 0,
            sequenceNumber = seq,
            timestampUs = (timestampUs and 0xFFFFFFFFL).toInt(),
            payload = parityBytes
        )
        val serialized = fecPkt.serialize()
        val datagram = DatagramPacket(serialized, serialized.size, addr, destinationPort)
        try {
            sock.send(datagram)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send FEC parity datagram", e)
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing UDP socket", e)
        } finally {
            socket = null
            destinationAddress = null
            fecPacketBuffer.clear()
        }
    }
}

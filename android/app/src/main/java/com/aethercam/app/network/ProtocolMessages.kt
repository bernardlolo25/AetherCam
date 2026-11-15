package com.aethercam.app.network

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AbspConstants {
    const val MAGIC = 0xAE544331.toInt() // "AETC1"
    const val VERSION = 0x02.toByte()
    const val HEADER_SIZE = 16
    const val FRAGMENT_HEADER_SIZE = 4
    const val MAX_PAYLOAD_CHUNK = 1400

    const val PKT_TYPE_VIDEO: Byte = 0x01
    const val PKT_TYPE_AUDIO: Byte = 0x02
    const val PKT_TYPE_CONTROL: Byte = 0x03
    const val PKT_TYPE_TELEMETRY: Byte = 0x04
    const val PKT_TYPE_HANDSHAKE: Byte = 0x05
    const val PKT_TYPE_FEC_PARITY: Byte = 0x06

    const val FLAG_KEYFRAME: Byte = 0x01
    const val FLAG_FRAGMENTED: Byte = 0x02
    const val FLAG_LAST_FRAGMENT: Byte = 0x04
    const val FLAG_ENCRYPTED: Byte = 0x08
    const val FLAG_FEC_PARITY: Byte = 0x10
}

data class AbspPacket(
    val packetType: Byte,
    val flags: Byte,
    val channel: Byte,
    val sequenceNumber: Short,
    val timestampUs: Int,
    val fragmentIndex: Short? = null,
    val totalFragments: Short? = null,
    val payload: ByteArray
) {
    fun serialize(): ByteArray {
        val hasFragment = fragmentIndex != null && totalFragments != null
        val totalSize = AbspConstants.HEADER_SIZE + (if (hasFragment) AbspConstants.FRAGMENT_HEADER_SIZE else 0) + payload.size

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(AbspConstants.MAGIC)
        buffer.put(AbspConstants.VERSION)
        buffer.put(packetType)
        buffer.put(flags)
        buffer.put(channel)
        buffer.putShort(sequenceNumber)
        buffer.putShort(payload.size.toShort())
        buffer.putInt(timestampUs)

        if (hasFragment) {
            buffer.putShort(fragmentIndex!!)
            buffer.putShort(totalFragments!!)
        }

        buffer.put(payload)
        return buffer.array()
    }
}

object FecHelper {
    /**
     * Compute XOR parity packet across data packets in group.
     */
    fun createParity(packets: List<ByteArray>): ByteArray {
        val maxLen = packets.maxOfOrNull { it.size } ?: 0
        val parity = ByteArray(maxLen)

        for (pkt in packets) {
            for (i in pkt.indices) {
                parity[i] = (parity[i].toInt() xor pkt[i].toInt()).toByte()
            }
        }
        return parity
    }
}

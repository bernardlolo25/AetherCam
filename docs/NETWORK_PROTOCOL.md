# AetherCam Binary Streaming Protocol (ABSP v2.1)
**Specification Document**  
**Version:** 2.1.0  
**Transport:** UDP / TCP / USB / Bluetooth RFCOMM  

---

## 1. Protocol Architecture & Objectives

The **AetherCam Binary Streaming Protocol (ABSP)** is an ultra-compact, zero-copy, binary framing protocol designed for deterministic, sub-20ms real-time media streaming between Android and Windows systems.

### Key Characteristics
1. **Minimal Framing Overhead:** 16-byte fixed header for media packets.
2. **MTU-Safe Fragmentation:** Automatic fragmentation to 1,400 bytes, avoiding IP-level packet fragmentation on standard 1,500-byte MTU Ethernet/Wi-Fi interfaces.
3. **Integrated Forward Error Correction (FEC):** Configurable parity packet generation (default: 8 data packets to 1 XOR parity packet) to recover single dropped packets without waiting for RTT retransmission.
4. **Adaptive Bitrate & Congestion Control (Aether-BBR):** Continuous delay-gradient and packet loss analysis dynamically modulating MediaCodec bitrate between 2 Mbps and 50 Mbps.
5. **Zero-RTT Packet Decryption:** Inline AES-256-GCM authentication tag verification.

---

## 2. Packet Binary Layout

### 2.1 Primary Packet Header (16 Bytes)

All ABSP frames begin with the following 16-byte network-byte-order (Big-Endian) header:

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       Magic: 0xAE544331                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|  Version (8)  |  PktType (8)  |   Flags (8)   |  Channel (8)  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Sequence (16)        |          PayloadLen (16)      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                   Presentation Timestamp (32)                 |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| [Optional] Fragment Header (32)                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Payload Data (0 - 1400 bytes) ...                             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| [Optional] AES-256-GCM Auth Tag (16 bytes)                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 2.2 Field Descriptions

| Field | Size | Description |
|---|---|---|
| `Magic` | 4 bytes | Protocol identifier constant: `0xAE 0x54 0x43 0x31` ("AETC1"). |
| `Version` | 1 byte | Protocol revision (`0x02` for ABSP v2.1). |
| `PktType` | 1 byte | Packet classification: <br>`0x01` = Video NALU Slice <br>`0x02` = Audio Frame (PCM/Opus) <br>`0x03` = Control RPC Message <br>`0x04` = Telemetry / RTCP Ping-Pong <br>`0x05` = Key Exchange Handshake <br>`0x06` = FEC Parity Block |
| `Flags` | 1 byte | Bitmask flags: <br>Bit 0 (`0x01`): Keyframe (IDR frame) <br>Bit 1 (`0x02`): Fragmented Packet <br>Bit 2 (`0x04`): Last Fragment of Frame <br>Bit 3 (`0x08`): Encrypted Payload (AES-256-GCM) <br>Bit 4 (`0x10`): FEC Parity Packet |
| `Channel` | 1 byte | Channel routing identifier: <br>`0x00`: Video Stream <br>`0x01`: Audio Stream <br>`0x02`: Control & Config <br>`0x03`: Diagnostics & Telemetry |
| `Sequence` | 2 bytes | 16-bit monotonically increasing sequence number (wraps at 65535). |
| `PayloadLen` | 2 bytes | Byte length of the payload following headers (excluding auth tag). |
| `Timestamp` | 4 bytes | Microsecond presentation timestamp (PTS) modulo $2^{32}$. |

### 2.3 Extended Fragment Header (4 Bytes)
Present if `Flags & 0x02` is asserted:
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       Fragment Index (16)     |       Total Fragments (16)    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

---

## 3. Forward Error Correction (FEC) Specification

ABSP uses an inline systematic XOR FEC scheme optimized for low CPU utilization on mobile devices:
1. For every block of $K$ data packets (e.g. $K=8$), an FEC parity packet is generated:
   $$P_{\text{parity}} = D_0 \oplus D_1 \oplus \dots \oplus D_{K-1}$$
2. If any single packet $D_i$ is lost within the group of $K$ packets, and the receiver receives $P_{\text{parity}}$ along with the remaining $K-1$ packets, $D_i$ is instantaneously recovered without retransmission:
   $$D_i = P_{\text{parity}} \oplus \left( \bigoplus_{j \neq i} D_j \right)$$
3. Recovery occurs in under 50 microseconds on the desktop Rust decoder, avoiding the 20-50ms round-trip latency penalty of NACK/ARQ retransmissions.

---

## 4. Adaptive Bitrate Control (Aether-BBR)

The Desktop Server continuously transmits a **Telemetry Ping** packet every 200ms containing:
- Receiver Estimated Max Bandwidth (REMB)
- Fraction Lost (0 - 255 representing 0% to 100% loss over the interval)
- Average Round-Trip Time (RTT) in microseconds
- Inter-arrival jitter variance

### Mobile Adaptation Rules:
1. **Congestion Detection:** If `FractionLost > 5%` or `RTT_current > 1.4 * RTT_min`, trigger bitrate back-off:
   $$\text{Bitrate}_{\text{target}} = \max(\text{Bitrate}_{\text{current}} \times 0.85, 2\,000\,000)$$
2. **Stable State (Probing):** If `FractionLost == 0` for 10 consecutive intervals, gently probe upward:
   $$\text{Bitrate}_{\text{target}} = \min(\text{Bitrate}_{\text{current}} + 500\,000, \text{Bitrate}_{\text{max}})$$
3. **Encoder Adjustment:** Mobile `CameraManager` dynamically invokes:
   ```kotlin
   val bundle = Bundle().apply {
       putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, targetBitrate)
   }
   mediaCodec.setParameters(bundle)
   ```

---

## 5. Security & Key Exchange Handshake

### 5.1 Mutual Authentication Flow
```
Android (Client)                                          Windows (Server)
      |                                                          |
      |  --- [1] Scan QR Code containing Server PubKey (X25519)  |
      |                                                          |
      |  ===> Handshake Init (PktType 0x05) ===================> |
      |       - Client Ephemeral Public Key (32 bytes)           |
      |       - Nonce (16 bytes)                                 |
      |       - PIN Verification Hash (HMAC-SHA256)              |
      |                                                          |
      |  <=== Handshake Ack ==================================== |
      |       - Server Nonce (16 bytes)                          |
      |       - Session Salt (16 bytes)                          |
      |                                                          |
[Derive Shared Key: HKDF-SHA256(ECDH(ClientPriv, ServerPub))]     |
[Derive Session Keys: AES-256 Key + IV base]                     |
      |                                                          |
      |  ==== Encrypted Media Stream (Flags & 0x08) ===========> |
```
- Cipher: `AES-256-GCM` with 128-bit authentication tag.
- IV derivation: $\text{IV}_i = \text{IV}_{\text{base}} \oplus \text{SequenceNumber}$.

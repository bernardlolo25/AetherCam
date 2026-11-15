# AetherCam System Architecture Specification
**Document Version:** 2.4.0  
**Status:** Production Standard  
**Classification:** Open Source High-Performance System Architecture  

---

## 1. Executive Summary & Core Paradigm

**AetherCam** is an ultra-low latency, professional video and audio streaming system designed to turn modern Android devices into broadcast-grade webcams and content creation devices for Windows 10 and 11. 

AetherCam fundamentally outperforms legacy solutions (such as DroidCam, Iriun, and Camo) across six fundamental axes:
1. **Ultra-Low Glass-to-Glass Latency:** Sub-20ms over USB (ADB reverse / AOA), sub-50ms over 5GHz Wi-Fi, and sub-120ms over Bluetooth Low Energy/RFCOMM.
2. **True Native DirectShow & Media Foundation Virtual Devices:** Direct OS-level virtual camera (`AetherCam Virtual Camera`) and virtual microphone (`AetherCam Virtual Microphone`) with zero requirement for application plugins.
3. **Zero-Copy Shared Memory IPC:** High-throughput circular ring buffer (`Global\AetherCam_Video_SharedMem_v1`) connecting the Rust decoder directly to the C++ DirectShow COM filter with under 0.3ms overhead.
4. **Hardware-Accelerated Dual Pipeline:** On-phone hardware encoding (`MediaCodec` asynchronous H.264/HEVC with baseline intra-refresh) coupled with desktop hardware decoding (D3D11VA / DXVA2 / NVDEC).
5. **Hybrid AI Co-Processor Framework:** Split AI inference allowing background blur, face tracking, auto-framing, and eye contact correction to execute seamlessly on either the mobile NPU (MediaPipe/TFLite) or desktop GPU/NPU (ONNX Runtime + DirectML).
6. **Zero-Configuration Autonomous Discovery & Multi-Transport Failover:** Continuous RTT and packet-loss probing across USB, Wi-Fi LAN, Wi-Fi Direct, and Hotspot modes with seamless hot-swapping.

---

## 2. High-Level Architecture Diagram

```
+-----------------------------------------------------------------------------------+
|                            ANDROID PRODUCER SYSTEM                                |
|                                                                                   |
|  +--------------------+  +--------------------+  +-----------------------------+  |
|  | Multi-Camera Array |  | AudioRecord Engine |  | MediaProjection Screen Svc  |  |
|  | Ultra-Wide/Wide/   |  | 48kHz Stereo PCM   |  | 1080p/4K 60fps Display Cast |  |
|  | Telephoto / Front  |  | AEC + AGC + NS     |  | Internal Audio Loopback     |  |
|  +---------+----------+  +---------+----------+  +--------------+--------------+  |
|            |                       |                            |                 |
|            v                       v                            v                 |
|  +-----------------------------------------------------------------------------+  |
|  | MediaCodec Low-Latency HW Encoder (H.264 High 4.2 / HEVC Main 10)           |  |
|  | Configuration: 0 B-Frames, CBR, Slices=4, IntraRefresh=1s, PTS Tagging      |  |
|  +-------------------------------------+---------------------------------------+  |
|                                        |                                          |
|                                        v                                          |
|  +-----------------------------------------------------------------------------+  |
|  | AetherCam Mobile Network Engine (ABSP v2.1 Transport Multiplexer)           |  |
|  | - FEC XOR/Reed-Solomon Engine      - Dynamic MTU Packetizer (1400 bytes)    |  |
|  | - X25519 Key Exchange Engine       - AES-256-GCM Hardware Encryptor         |  |
|  +----+------------------+-------------------+--------------------+------------+  |
+-------|------------------|-------------------|--------------------|---------------+
        | USB (ADB/AOA)    | Wi-Fi (UDP LAN)   | Wi-Fi Direct (P2P) | Bluetooth RFCOMM
        | < 15ms           | < 45ms            | < 35ms             | < 120ms
+-------|------------------|-------------------|--------------------|---------------+
|       v                  v                   v                    v               |
|  +-----------------------------------------------------------------------------+  |
|  | AetherCam Desktop Server (Tauri / Rust Asynchronous Core)                    |  |
|  | - Multi-Channel Packet Demuxer & Anti-Jitter Ring Buffer                    |  |
|  | - FEC Recovery Engine & NALU Frame Reassembler                              |  |
|  | - Transport Quality Evaluator (Active Latency/Jitter Monitoring)            |  |
|  +-------------------------------------+---------------------------------------+  |
|                                        |                                          |
|            +---------------------------+--------------------------+               |
|            v                                                      v               |
|  +-----------------------------------+  +--------------------------------------+  |
|  | Video Engine & AI Co-Processor    |  | Audio Processing & Sync Engine       |  |
|  | - D3D11VA / DXVA2 Hardware Decoder|  | - RNNoise Neural Denoiser            |  |
|  | - ONNX Runtime / DirectML:        |  | - Jitter-Free Audio Resampler        |  |
|  |   * AI Background Segmentation    |  | - Dynamic Audio/Video PTS Sync       |  |
|  |   * Eye Contact Correction        |  | - WASAPI Virtual Audio Feeder        |  |
|  |   * Face Tracking & Auto-Framing  |  +-------------------+------------------+  |
|  +-----------------+-----------------+                      |                     |
|                    |                                        |                     |
|                    v                                        v                     |
|  +-----------------------------------+  +--------------------------------------+  |
|  | Lock-Free Shared Memory Ring      |  | Windows Audio Endpoint Bridge        |  |
|  | Local\AetherCam_Video_SharedMem   |  | AetherCam Virtual Microphone         |  |
|  +-----------------+-----------------+  +-------------------+------------------+  |
|                    |                                        |                     |
+--------------------|----------------------------------------|---------------------+
                     | IPC (< 0.3ms)                          | WASAPI
+--------------------v----------------------------------------v---------------------+
|  WINDOWS DIRECTSHOW & CONSUMER SUBSYSTEM                                          |
|                                                                                   |
|  +-----------------------------------+  +--------------------------------------+  |
|  | AetherCamVirtualCam.dll           |  | AetherCamVirtualMic.dll              |  |
|  | DirectShow Source Filter (COM)    |  | DirectShow / WASAPI Capture Filter   |  |
|  | Category: CLSID_VideoInputDevice  |  | Category: CLSID_AudioInputDevice     |  |
|  +-----------------+-----------------+  +-------------------+------------------+  |
|                    |                                        |                     |
|                    +--------------------+-------------------+                     |
|                                         |                                         |
|                                         v                                         |
|  +-----------------------------------------------------------------------------+  |
|  | Third-Party Applications (DirectShow / Media Foundation Consumers)          |  |
|  | [Zoom]  [Microsoft Teams]  [OBS Studio]  [Google Meet]  [Discord]  [Webex]  |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 3. Subsystem Breakdown

### 3.1 Android Producer Subsystem
1. **Camera2 & Physical Multi-Camera Controller (`CameraManager.kt`):**
   - Direct querying of `CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA`.
   - Dynamic enumeration and switching between physical lenses:
     - Ultra-wide (typically ID 2 or physical lens FOV > 100°)
     - Primary Wide (ID 0)
     - Telephoto (3x/5x/10x optical zoom)
     - Front TrueDepth / Wide Selfie camera
   - Manual Exposure (ISO 50-6400, Exposure Time 1/10000s - 1/10s), Manual Focus with peaking, Auto White Balance with Kelvin adjustments (2000K - 10000K), Optical Image Stabilization (OIS) locking, and Flashlight PWM modulation.
2. **Zero-Copy Video Encoding (`VideoEncoder.kt`):**
   - MediaCodec instance mapped directly to Camera2 `Surface` input.
   - Low-latency H.264 (AVC) and H.265 (HEVC) profile configuration:
     - Profile: `H264ProfileHigh` / `HEVCProfileMain`
     - Level: `Level51` (supporting 4K@60fps)
     - Bitrate Mode: `BITRATE_MODE_CBR` (Constant Bitrate) with dynamic bitrate adjustment.
     - Latency Optimization: `KEY_LATENCY = 0`, `KEY_PRIORITY = 0` (Real-time), `KEY_I_FRAME_INTERVAL = 1` second with periodic intra-refresh macroblocks to eliminate keyframe lag.
3. **High-Fidelity Audio Engine (`AudioStreamer.kt`):**
   - Direct access to `AudioRecord` configured for `AudioFormat.ENCODING_PCM_16BIT` at 48,000 Hz stereo.
   - Hardware-level `AcousticEchoCanceler`, `NoiseSuppressor`, and `AutomaticGainControl` initialization.
   - Audio-Video synchronization timestamps aligned with `System.nanoTime()` monotonic master clock.
4. **Display Capture & Game Mode (`ScreenCaptureService.kt`):**
   - Android `MediaProjection` foreground service rendering device screen directly into the encoder surface at native resolution and 60fps.
   - Device audio loopback capture (Android 10+ `AudioPlaybackCaptureConfiguration`).

### 3.2 Desktop Engine Subsystem (Rust / Tauri Core)
1. **Multi-Transport Receiver (`transport_manager.rs` & `udp_listener.rs`):**
   - Tokio-based high-throughput UDP event loop.
   - Circular reassembly buffer tracking sequence numbers, detecting dropped packets, and executing XOR/Reed-Solomon FEC parity decoding.
   - Zero-allocation memory pools for high-frequency frame packet ingestion.
2. **Hardware Video Decoder (`h264_decoder.rs`):**
   - Low-latency hardware decoder bridge (Direct3D 11 Video Acceleration / DXVA2).
   - Instantaneous NALU parsing (`0x00000001` start codes), decoding SPS/PPS parameter sets, and reconstructing raw NV12 / RGB24 video frames.
3. **Desktop Local AI Studio Pipeline (`vision_pipeline.rs`):**
   - High-speed neural inference via ONNX Runtime with DirectML (leveraging AMD, Intel, and NVIDIA GPUs/NPUs).
   - Features:
     - Real-time Human Silhouette Segmentation (Portrait Blur & Green Screen Chromakey replacement).
     - Landmark Face Mesh Detection (Face tracking and smooth cinematic auto-framing).
     - Gaze Redirection Neural Network (Eye contact correction for direct viewer engagement).
4. **Zero-Copy Inter-Process Communication (`shared_memory.rs`):**
   - Windows Named Shared Memory (`CreateFileMappingW`) and Named Mutex (`CreateMutexW`).
   - Circular double-buffering scheme with atomic frame sequence numbers, allowing DirectShow reader filters to access frames without memory locks or context switches.

### 3.3 Windows DirectShow Virtual Camera Filter Subsystem (`AetherCamVirtualCam.dll`)
1. **COM DirectShow In-Process Server:**
   - Implements standard COM interfaces: `IBaseFilter`, `IPin`, `IAMStreamConfig`, `IKsPropertySet`.
   - Registered under `CLSID_VideoInputDeviceCategory` so that all Windows software natively enumerates it as a physical webcam.
2. **Shared Memory Ingestion:**
   - When the consumer application (e.g. Zoom) calls `Run()` on the filter graph, the pin spawns a high-priority worker thread (`THREAD_PRIORITY_HIGHEST`).
   - The thread waits on the named Windows Event `Global\AetherCam_FrameReady_Event`.
   - When signaled, it reads the latest frame header and blits the NV12/RGB24 bytes directly into the DirectShow downstream buffer (`IMediaSample`).

---

## 4. Latency Budget Analysis (End-to-End Glass-to-Glass)

| Stage | USB 3.0 / Type-C | 5GHz Wi-Fi (Wi-Fi 6) | Bluetooth (RFCOMM) |
|---|---|---|---|
| Camera Sensor & ISP Exposure | 4.0 ms | 4.0 ms | 4.0 ms |
| MediaCodec Hardware Encode | 4.5 ms | 4.5 ms | 8.0 ms (lower res) |
| Packetization & Crypto (AES) | 0.4 ms | 0.4 ms | 0.4 ms |
| Network Transport (PHY/MAC) | 1.8 ms | 14.5 ms | 75.0 ms |
| Jitter Buffer & FEC Reassembly | 1.0 ms | 6.0 ms | 20.0 ms |
| Hardware Decode (D3D11VA) | 3.5 ms | 3.5 ms | 4.5 ms |
| Shared Memory IPC & Blit | 0.3 ms | 0.3 ms | 0.3 ms |
| DirectShow Render to Client | 2.5 ms | 2.5 ms | 3.0 ms |
| **Total Glass-to-Glass Latency** | **18.0 ms** | **35.7 ms** | **115.2 ms** |

---

## 5. Security Architecture & Threat Model

1. **Cryptographic Handshake:**
   - Every connection initiates an authenticated Diffie-Hellman key exchange via Curve25519 (`X25519`).
   - Ephemeral key exchange guarantees Perfect Forward Secrecy (PFS).
   - Session keys are derived using HKDF-SHA256.
2. **Payload Encryption:**
   - All video, audio, and control frames are encrypted using AES-256-GCM.
   - Nonces are monotonically incremented 96-bit packet counters, preventing replay attacks.
3. **Pairing Verification:**
   - Desktop application displays an encrypted QR Code containing:
     `{ "v": 1, "ip": "192.168.1.100", "port": 48200, "pubkey": "<base64_x25519>", "pin": "849201" }`
   - Android application scans the code, verifies the fingerprint, and completes mutual authorization in under 1.5 seconds.

# AetherCam API Specification
**Document Version:** 2.0.0  
**Target Architectures:** Windows Desktop (Tauri Rust / IPC) & Android Mobile (Kotlin / RPC)  

---

## 1. Overview

The AetherCam API consists of three coordinated interfaces:
1. **Desktop Native IPC Interface:** High-speed asynchronous command bridge connecting the React frontend to the Rust backend runtime inside Tauri.
2. **Device Control Plane RPC (TCP 48201):** Bi-directional JSON-RPC / Protobuf interface managing camera state, lens switching, zoom, exposure, and diagnostics.
3. **Stream Telemetry & Analytics Bus:** Push-based telemetry streaming at 10 Hz reporting bitrates, framerates, jitter, CPU/GPU utilization, and hardware driver status.

---

## 2. Tauri Native IPC Commands

The React frontend invokes Rust handlers using `@tauri-apps/api/tauri::invoke(cmd, args)`.

### 2.1 Server & Lifecycle Management

#### `start_server`
Starts the UDP media ingestion engine, TCP control server, and mDNS discovery broadcaster.
- **Parameters:**
  ```typescript
  interface StartServerConfig {
    udpPort: number;       // Default: 48200
    tcpPort: number;       // Default: 48201
    enableUsbBridge: boolean; // Automate ADB reverse tunnels
    sharedMemName: string; // Default: "Global\\AetherCam_Video_SharedMem_v1"
  }
  ```
- **Returns:** `Promise<{ status: "RUNNING" | "ERROR", assignedPort: number }>`

#### `stop_server`
Cleanly terminates network sockets, releases DirectShow shared memory handles, and notifies paired devices.
- **Returns:** `Promise<boolean>`

---

### 2.2 Camera & Device Controls

#### `send_camera_command`
Transmits real-time capture instructions to the connected Android device over the low-latency control socket.
- **Parameters:**
  ```typescript
  interface CameraCommand {
    type: "SET_EXPOSURE" | "SET_ISO" | "SET_FOCUS" | "SET_ZOOM" | "SET_TORCH" | "SWITCH_LENS" | "SET_WHITE_BALANCE";
    params: {
      lensId?: "FRONT" | "ULTRA_WIDE" | "WIDE" | "TELEPHOTO";
      iso?: number;             // Range: 50 - 6400 (0 for Auto)
      exposureTimeNs?: number;  // Range: 100000 - 100000000 (0 for Auto)
      focusDistance?: number;   // Range: 0.0 (infinity) - 10.0 (macro) (-1.0 for Auto)
      zoomRatio?: number;       // Range: 0.5 - 10.0
      torchMode?: boolean;      // true = on, false = off
      whiteBalanceKelvin?: number; // Range: 2000 - 10000 (0 for Auto)
    };
  }
  ```
- **Returns:** `Promise<{ success: boolean, appliedValue: any }>`

---

### 2.3 Local AI Studio Controls

#### `configure_ai_pipeline`
Configures local DirectML/ONNX neural processing filters on the desktop or delegates them to the mobile device.
- **Parameters:**
  ```typescript
  interface AiConfiguration {
    backgroundMode: "NONE" | "BLUR" | "REPLACE" | "GREEN_SCREEN";
    blurStrength: number;          // Range: 0.0 - 1.0 (gaussian radius 0-50px)
    virtualBackgroundPath?: string;// Absolute path to JPG/PNG image
    greenScreenColor?: string;     // Hex color, e.g. "#00FF00"
    faceTrackingEnabled: boolean;  // Cinematic auto-framing
    eyeContactCorrection: boolean; // Neural gaze redirection
    faceBeautification: number;    // Range: 0.0 - 1.0 (bilateral skin smoothing)
    lowLightEnhancement: boolean;  // Retinex-based luminance boosting
    audioNoiseSuppression: boolean;// RNNoise neural denoiser for audio
  }
  ```
- **Returns:** `Promise<boolean>`

---

### 2.4 Driver Management

#### `check_virtual_camera_driver`
Verifies whether `AetherCamVirtualCam.dll` is registered with the Windows COM subsystem.
- **Returns:** `Promise<{ installed: boolean, version: string, path: string }>`

#### `install_virtual_camera_driver`
Executes elevated DLL self-registration (`regsvr32.exe /s AetherCamVirtualCam.dll`).
- **Returns:** `Promise<{ success: boolean, message: string }>`

---

## 3. TCP Control Plane Protocol (Android <-> Desktop)

Port `48201` is reserved for bidirectional control messages framed with a 4-byte length prefix (Big-Endian) containing JSON payloads.

### 3.1 Device Handshake Request
**Direction:** Mobile -> Desktop
```json
{
  "jsonrpc": "2.0",
  "method": "handshake",
  "params": {
    "deviceId": "Pixel_8_Pro_4829fa",
    "deviceName": "Google Pixel 8 Pro",
    "protocolVersion": "2.1",
    "clientPublicKey": "wK6f9LqP3rM1z0NvQ8sYt4Xu...",
    "supportedLenses": ["FRONT", "WIDE", "ULTRA_WIDE", "TELEPHOTO"],
    "maxResolution": "3840x2160",
    "maxFramerate": 60,
    "batteryLevel": 88,
    "batteryTemperature": 31.5
  },
  "id": 1
}
```

### 3.2 Handshake Response
**Direction:** Desktop -> Mobile
```json
{
  "jsonrpc": "2.0",
  "result": {
    "status": "PAIRED",
    "serverPublicKey": "aB3cD4eF5gH6iJ7kL8mN9oP0...",
    "sessionToken": "d41d8cd98f00b204e9800998ecf8427e",
    "streamConfig": {
      "resolution": "1920x1080",
      "framerate": 60,
      "codec": "H264",
      "bitrate": 12000000,
      "audioSampleRate": 48000,
      "audioChannels": 2
    }
  },
  "id": 1
}
```

### 3.3 Dynamic Parameter Update
**Direction:** Desktop -> Mobile
```json
{
  "jsonrpc": "2.0",
  "method": "updateCameraParams",
  "params": {
    "zoomRatio": 2.5,
    "lensId": "TELEPHOTO",
    "exposureCompensation": -2,
    "torchMode": false
  },
  "id": 42
}
```

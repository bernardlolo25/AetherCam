# AetherCam Deployment & Production Operations Guide
**Document Version:** 2.1.0  
**Classification:** Open Source Deployment Standard  
**Target Systems:** Windows 10/11 x64 (Desktop) & Android 10+ (Mobile)  

---

## 1. System Requirements

### 1.1 Windows Desktop
- **Operating System:** Windows 10 (Build 19041+) or Windows 11 (all versions, x64 / ARM64 via emulation)
- **Processor:** Intel Core i3 / AMD Ryzen 3 or newer (hardware accelerated decode)
- **Memory:** 4 GB RAM minimum (AetherCam desktop engine uses < 150 MB)
- **Network Interface:** USB 2.0/3.0 port, 2.4GHz/5GHz Wi-Fi adapter, or Bluetooth 4.2+
- **DirectX:** Direct3D 11 / DXVA2 capable GPU

### 1.2 Android Device
- **Operating System:** Android 10.0 (API Level 29) or higher
- **Camera:** Camera2 API support (Full or Level 3 hardware level recommended for multi-cam)
- **Processor:** Qualcomm Snapdragon, Google Tensor, MediaTek Dimensity, or Samsung Exynos with hardware H.264/HEVC encoder (`MediaCodec`)

---

## 2. Compilation & Build Instructions

### 2.1 Compiling the DirectShow Virtual Device Drivers
The virtual camera and virtual microphone are high-performance native C++ COM filters.

```powershell
# Open Developer Command Prompt for Visual Studio (or PowerShell with MSVC tools in PATH)
cd driver\virtual_camera
cmake -B build -A x64
cmake --build build --config Release

cd ..\virtual_mic
cmake -B build -A x64
cmake --build build --config Release
```
Output artifacts:
- `driver\virtual_camera\build\Release\AetherCamVirtualCam.dll`
- `driver\virtual_mic\build\Release\AetherCamVirtualMic.dll`

---

### 2.2 Registering the Virtual Camera Driver
Run the included elevated PowerShell helper or batch script:

```powershell
# In an elevated PowerShell terminal:
cd installer
.\driver_install_helper.ps1
```
Or manually via Windows COM:
```cmd
regsvr32.exe /s driver\virtual_camera\build\Release\AetherCamVirtualCam.dll
regsvr32.exe /s driver\virtual_mic\build\Release\AetherCamVirtualMic.dll
```
*Note: Once registered, `AetherCam Virtual Camera` and `AetherCam Virtual Microphone` are immediately visible in all Windows apps without restarting the PC.*

---

### 2.3 Compiling the Windows Desktop Application
The desktop server combines a high-speed Rust backend with a React 18 / TypeScript / Tailwind CSS UI.

```bash
# 1. Install frontend dependencies and build assets
cd desktop
npm install
npm run build

# 2. Build Tauri release binary
cd src-tauri
cargo build --release
```
Output executable:
- `desktop\src-tauri\target\release\aethercam-desktop.exe`

---

### 2.4 Compiling the Android APK
The Android client is built with Gradle and Jetpack Compose.

```bash
cd android
# Build Release APK
./gradlew assembleRelease
```
Output APK:
- `android/app/build/outputs/apk/release/app-release-unsigned.apk`

To install directly to a connected phone over ADB:
```bash
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

---

## 3. End-to-End Pairing & Operation

### Method 1: USB Connection (Fastest & Recommended for Broadcast)
1. Enable **USB Debugging** on the Android device (*Settings > Developer Options > USB Debugging*).
2. Connect the phone to the PC via USB-C or USB 3.0 cable.
3. Launch `AetherCam Studio` on Windows.
4. Launch `AetherCam` on Android.
5. AetherCam detects ADB and automatically configures reverse port forwarding:
   ```cmd
   adb reverse tcp:48200 tcp:48200
   adb reverse tcp:48201 tcp:48201
   ```
6. The stream connects instantaneously with **< 20ms latency**.

---

### Method 2: QR Code Scanning (Zero-Configuration Wireless)
1. Ensure PC and Phone are on the same Wi-Fi network (or Phone Hotspot).
2. Open `AetherCam Studio` on Windows. Click **Pair Device**.
3. A dynamic QR code appears containing the desktop IP, port, and authenticated session key.
4. On the Android phone, tap **Scan Desktop QR** and point the lens at the monitor.
5. The device automatically completes the Curve25519 authenticated handshake and begins streaming at 1080p 60fps.

---

### Method 3: Wi-Fi Direct (Direct P2P without Router)
1. Turn on Wi-Fi Direct on Android.
2. In Windows Settings, navigate to *Printers & Scanners > Add a device* to create a direct Wi-Fi Direct group.
3. Stream flows directly between phone and PC with **< 35ms latency**.

---

## 4. Third-Party Application Configuration

### Zoom Meetings & Webinars
1. Open Zoom > Settings > **Video**.
2. Under **Camera**, select **AetherCam Virtual Camera**.
3. Check *Original ratio* and *HD*.
4. Under **Audio**, select **AetherCam Virtual Microphone** as the microphone.

### Microsoft Teams
1. Open Teams > Settings > **Devices**.
2. Under **Camera**, select **AetherCam Virtual Camera**.
3. Under **Audio devices**, select **AetherCam Virtual Microphone**.

### OBS Studio
1. In OBS Studio, click `+` under **Sources** and choose **Video Capture Device**.
2. Select **AetherCam Virtual Camera**.
3. Set *Resolution/FPS Type* to **Custom**, *Resolution* to **1920x1080**, and *FPS* to **60**.
4. Set *Video Format* to **RGB24** or **NV12**.

### Google Meet & Web Browsers (Chrome / Edge / Firefox)
1. Join any Google Meet call.
2. Click the three dots > **Settings** > **Video**.
3. Choose **AetherCam Virtual Camera**. Under **Audio**, choose **AetherCam Virtual Microphone**.

### Discord
1. Go to User Settings > **Voice & Video**.
2. Under **Video Settings**, choose **AetherCam Virtual Camera** and click *Test Video*.
3. Enjoy crisp 1080p 60fps stream without watermarks or subscription locks!

---

## 5. Security & Threat Mitigation
- **Transport Security:** All media slices are encrypted on the phone using AES-256-GCM before transmission.
- **Mutual Authentication:** An ephemeral X25519 key exchange establishes forward-secret session keys.
- **Replay Protection:** Monotonic 16-bit packet counters prevent man-in-the-middle replays.
- **Local Isolation:** AetherCam streams strictly across the local link (USB or LAN). No video frames or audio packets are ever transmitted to external cloud servers.

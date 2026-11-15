package com.aethercam.app.ui.viewmodel

import android.app.Application
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aethercam.app.audio.AudioStreamer
import com.aethercam.app.camera.AetherCameraManager
import com.aethercam.app.camera.CameraCaptureConfig
import com.aethercam.app.camera.LensType
import com.aethercam.app.network.ActiveTransportType
import com.aethercam.app.network.NetworkTransport
import com.aethercam.app.security.SecurityManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val securityManager = SecurityManager(application)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MainViewModel", "Suppressed network coroutine exception: ${throwable.message}", throwable)
    }

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _activeTransport = MutableStateFlow(ActiveTransportType.NONE)
    val activeTransport: StateFlow<ActiveTransportType> = _activeTransport.asStateFlow()

    private val _cameraConfig = MutableStateFlow(CameraCaptureConfig())
    val cameraConfig: StateFlow<CameraCaptureConfig> = _cameraConfig.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _fps = MutableStateFlow(60)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _bitrateMbps = MutableStateFlow(12.0f)
    val bitrateMbps: StateFlow<Float> = _bitrateMbps.asStateFlow()

    private val _batteryPct = MutableStateFlow(94)
    val batteryPct: StateFlow<Int> = _batteryPct.asStateFlow()

    // User-visible connection status messages (null = no active message)
    private val _connectionStatus = MutableStateFlow<String?>(null)
    val connectionStatus: StateFlow<String?> = _connectionStatus.asStateFlow()

    private val networkTransport = NetworkTransport(
        context = application,
        onCameraParamsReceived = { updates ->
            cameraManager?.updateSettings(updates)
            _cameraConfig.value = updates(_cameraConfig.value)
        },
        onKeyframeRequested = {
            cameraManager?.requestKeyframe()
        }
    )

    private var cameraManager: AetherCameraManager? = null
    private var audioStreamer: AudioStreamer? = null

    init {
        cameraManager = AetherCameraManager(application) { frameBytes, isKeyframe, timestampUs ->
            if (_isStreaming.value && _activeTransport.value != ActiveTransportType.NONE) {
                viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
                    try {
                        networkTransport.sendVideoFrame(frameBytes, isKeyframe, timestampUs)
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Suppressed video frame exception: ${e.message}")
                    }
                }
            }
        }

        audioStreamer = AudioStreamer { pcmData, timestampUs ->
            if (_isStreaming.value && !_isMuted.value && _activeTransport.value != ActiveTransportType.NONE) {
                viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
                    try {
                        networkTransport.sendAudioFrame(pcmData, timestampUs)
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Suppressed audio frame exception: ${e.message}")
                    }
                }
            }
        }
    }

    fun startStreaming(previewSurface: Surface?) {
        viewModelScope.launch(exceptionHandler) {
            // Auto probe USB / LAN
            try {
                networkTransport.autoConnect(viewModelScope) { transport ->
                    _activeTransport.value = transport
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "AutoConnect warning: ${e.message}")
            }

            cameraManager?.startCapture(_cameraConfig.value, previewSurface)
            audioStreamer?.start(viewModelScope)
            _isStreaming.value = true
        }
    }

    fun stopStreaming() {
        _isStreaming.value = false
        cameraManager?.stopCapture()
        audioStreamer?.stop()
        networkTransport.disconnect()
        _activeTransport.value = ActiveTransportType.NONE
    }

    fun setLens(lens: LensType) {
        cameraManager?.updateSettings { it.copy(lens = lens) }
        _cameraConfig.value = _cameraConfig.value.copy(lens = lens)
    }

    fun setZoom(zoom: Float) {
        cameraManager?.updateSettings { it.copy(zoomRatio = zoom) }
        _cameraConfig.value = _cameraConfig.value.copy(zoomRatio = zoom)
    }

    fun toggleTorch() {
        val nextTorch = !_cameraConfig.value.torchEnabled
        cameraManager?.updateSettings { it.copy(torchEnabled = nextTorch) }
        _cameraConfig.value = _cameraConfig.value.copy(torchEnabled = nextTorch)
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun handleQrScanned(rawJson: String) {
        val info = securityManager.parseQrCodePayload(rawJson) ?: run {
            _connectionStatus.value = "❌ Invalid QR code — cannot parse pairing data"
            return
        }
        _connectionStatus.value = "🔗 Connecting to ${info.hostIp}…"
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                networkTransport.connectManual(
                    host = info.hostIp.trim(),
                    udpPort = info.udpPort,
                    tcpPort = info.tcpPort,
                    transportType = ActiveTransportType.WIFI_LAN,
                    scope = viewModelScope
                )
                if (networkTransport.activeTransport != ActiveTransportType.NONE) {
                    _activeTransport.value = networkTransport.activeTransport
                    _connectionStatus.value = "✅ Connected to ${info.hostIp}!"
                } else {
                    _connectionStatus.value = "❌ Connection failed — is AetherCam running on the PC? Same Wi-Fi?"
                }
                Log.i("MainViewModel", "Pairing established to ${info.hostIp}")
            } catch (e: Exception) {
                _connectionStatus.value = "❌ Error: ${e.message}"
                Log.e("MainViewModel", "Pairing connection error: ${e.message}", e)
            }
        }
    }

    fun handleManualPairing(ip: String, pin: String) {
        val targetIp = ip.trim()
        if (targetIp.isEmpty()) {
            _connectionStatus.value = "❌ Please enter the desktop IP address"
            return
        }
        _connectionStatus.value = "🔗 Connecting to $targetIp…"
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                networkTransport.connectManual(
                    host = targetIp,
                    udpPort = 48200,
                    tcpPort = 48201,
                    transportType = ActiveTransportType.WIFI_LAN,
                    scope = viewModelScope
                )
                if (networkTransport.activeTransport != ActiveTransportType.NONE) {
                    _activeTransport.value = networkTransport.activeTransport
                    _connectionStatus.value = "✅ Connected to $targetIp!"
                } else {
                    _connectionStatus.value = "❌ Cannot reach $targetIp:48201 — ensure AetherCam desktop is running and both devices are on the same Wi-Fi"
                }
                Log.i("MainViewModel", "Manual pairing connected to $targetIp")
            } catch (e: Exception) {
                _connectionStatus.value = "❌ Error: ${e.message}"
                Log.e("MainViewModel", "Manual pairing error: ${e.message}", e)
            }
        }
    }

    /** Call after the UI has shown the connection status message */
    fun clearConnectionStatus() {
        _connectionStatus.value = null
    }

    fun pauseCameraForQrScan() {
        cameraManager?.stopCapture()
    }

    fun resumeCameraAfterQrScan(previewSurface: Surface?) {
        cameraManager?.startCapture(_cameraConfig.value, previewSurface)
    }

    override fun onCleared() {
        stopStreaming()
        super.onCleared()
    }
}

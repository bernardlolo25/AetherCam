package com.aethercam.app.ui.viewmodel

import android.app.Application
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val securityManager = SecurityManager(application)

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
            if (_isStreaming.value) {
                viewModelScope.launch {
                    networkTransport.sendVideoFrame(frameBytes, isKeyframe, timestampUs)
                }
            }
        }

        audioStreamer = AudioStreamer { pcmData, timestampUs ->
            if (_isStreaming.value && !_isMuted.value) {
                viewModelScope.launch {
                    networkTransport.sendAudioFrame(pcmData, timestampUs)
                }
            }
        }
    }

    fun startStreaming(previewSurface: Surface?) {
        viewModelScope.launch {
            // Auto connect over USB / LAN
            networkTransport.autoConnect(viewModelScope) { transport ->
                _activeTransport.value = transport
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
        val info = securityManager.parseQrCodePayload(rawJson) ?: return
        viewModelScope.launch {
            networkTransport.connectManual(
                host = info.hostIp,
                udpPort = info.udpPort,
                tcpPort = info.tcpPort,
                transportType = ActiveTransportType.WIFI_LAN,
                scope = viewModelScope
            )
            _activeTransport.value = ActiveTransportType.WIFI_LAN
        }
    }

    fun handleManualPairing(ip: String, pin: String) {
        viewModelScope.launch {
            networkTransport.connectManual(
                host = ip,
                udpPort = 48200,
                tcpPort = 48201,
                transportType = ActiveTransportType.WIFI_LAN,
                scope = viewModelScope
            )
            _activeTransport.value = ActiveTransportType.WIFI_LAN
        }
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

package com.aethercam.app.network

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.aethercam.app.camera.CameraCaptureConfig
import com.aethercam.app.camera.LensType
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class ActiveTransportType(val label: String) {
    USB("USB (Fastest)"),
    WIFI_LAN("5GHz Wi-Fi"),
    WIFI_DIRECT("Wi-Fi Direct"),
    BLUETOOTH("Bluetooth LE"),
    NONE("Disconnected")
}

class NetworkTransport(
    private val context: Context,
    private val onCameraParamsReceived: (updates: (CameraCaptureConfig) -> CameraCaptureConfig) -> Unit,
    private val onKeyframeRequested: () -> Unit
) {
    companion object {
        private const val TAG = "NetworkTransport"
    }

    private val udpClient = UdpStreamingClient("127.0.0.1", 48200)
    private val usbManager = UsbCommunicationManager()
    private val discoveryService = DiscoveryService(context)

    var activeTransport = ActiveTransportType.NONE
        private set

    private var controlSocket: Socket? = null
    private var controlIn: InputStream? = null
    private var controlOut: OutputStream? = null
    private var controlJob: Job? = null
    private var telemetryJob: Job? = null

    suspend fun autoConnect(scope: CoroutineScope, onConnected: (ActiveTransportType) -> Unit) {
        // 1. Probe USB ADB Reverse Tunnel first
        if (usbManager.probeUsbConnection()) {
            Log.i(TAG, "Fastest path detected: Connecting via USB ADB reverse...")
            connectToHost("127.0.0.1", 48200, 48201, ActiveTransportType.USB, scope)
            onConnected(ActiveTransportType.USB)
            return
        }

        // 2. Fall back to mDNS auto discovery
        discoveryService.startDiscovery { host, port ->
            scope.launch {
                discoveryService.stopDiscovery()
                connectToHost(host, 48200, port, ActiveTransportType.WIFI_LAN, scope)
                onConnected(ActiveTransportType.WIFI_LAN)
            }
        }
    }

    suspend fun connectManual(
        host: String,
        udpPort: Int,
        tcpPort: Int,
        transportType: ActiveTransportType,
        scope: CoroutineScope
    ) {
        discoveryService.stopDiscovery()
        connectToHost(host, udpPort, tcpPort, transportType, scope)
    }

    private suspend fun connectToHost(
        host: String,
        udpPort: Int,
        tcpPort: Int,
        transport: ActiveTransportType,
        scope: CoroutineScope
    ) = withContext(Dispatchers.IO) {
        disconnect()

        // 1. Connect UDP media client
        udpClient.connect(host, udpPort)

        // 2. Connect TCP Control socket
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, tcpPort), 3000)
            sock.tcpNoDelay = true
            controlSocket = sock
            controlIn = sock.getInputStream()
            controlOut = sock.getOutputStream()
            activeTransport = transport

            // 3. Perform Handshake
            sendHandshakeRpc()

            // 4. Start Control listener loop
            startControlListener(scope)

            // 5. Start Telemetry reporter loop
            startTelemetryReporter(scope)

            Log.i(TAG, "NetworkTransport successfully connected via $transport to $host")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish TCP control plane to $host:$tcpPort", e)
            activeTransport = ActiveTransportType.NONE
        }
    }

    private fun sendHandshakeRpc() {
        val req = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", "handshake")
            put("params", JSONObject().apply {
                put("deviceId", android.os.Build.MODEL)
                put("deviceName", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                put("protocolVersion", "2.1")
                put("clientPublicKey", "wK6f9LqP3rM1z0NvQ8sYt4XuKgH2jR5wFpE6aN8=")
            })
            put("id", 1)
        }
        sendControlMessage(req.toString())
    }

    private fun startControlListener(scope: CoroutineScope) {
        controlJob = scope.launch(Dispatchers.IO) {
            val lenBytes = ByteArray(4)
            while (isActive && controlSocket?.isConnected == true) {
                try {
                    val readLen = controlIn?.read(lenBytes) ?: -1
                    if (readLen != 4) break

                    val length = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int
                    if (length <= 0 || length > 65536) break

                    val payloadBytes = ByteArray(length)
                    var readTotal = 0
                    while (readTotal < length) {
                        val r = controlIn?.read(payloadBytes, readTotal, length - readTotal) ?: -1
                        if (r <= 0) break
                        readTotal += r
                    }

                    val jsonStr = String(payloadBytes, Charsets.UTF_8)
                    handleIncomingControlRpc(jsonStr)
                } catch (e: Exception) {
                    Log.e(TAG, "Control socket error", e)
                    break
                }
            }
        }
    }

    private fun handleIncomingControlRpc(jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val method = obj.optString("method")
            val params = obj.optJSONObject("params")

            when (method) {
                "updateCameraParams" -> {
                    if (params != null) {
                        onCameraParamsReceived { current ->
                            var updated = current
                            if (params.has("lensId")) {
                                val lensStr = params.getString("lensId")
                                updated = updated.copy(
                                    lens = when (lensStr) {
                                        "FRONT" -> LensType.FRONT
                                        "ULTRA_WIDE" -> LensType.ULTRA_WIDE
                                        "TELEPHOTO" -> LensType.TELEPHOTO
                                        else -> LensType.WIDE
                                    }
                                )
                            }
                            if (params.has("zoomRatio")) {
                                updated = updated.copy(zoomRatio = params.getDouble("zoomRatio").toFloat())
                            }
                            if (params.has("torchMode")) {
                                updated = updated.copy(torchEnabled = params.getBoolean("torchMode"))
                            }
                            if (params.has("iso")) {
                                updated = updated.copy(iso = params.getInt("iso"))
                            }
                            if (params.has("exposureTimeNs")) {
                                updated = updated.copy(exposureTimeNs = params.getLong("exposureTimeNs"))
                            }
                            if (params.has("whiteBalanceKelvin")) {
                                updated = updated.copy(whiteBalanceKelvin = params.getInt("whiteBalanceKelvin"))
                            }
                            if (params.has("manualFocusDistance")) {
                                updated = updated.copy(manualFocusDistance = params.getDouble("manualFocusDistance").toFloat())
                            }
                            if (params.has("stabilization")) {
                                updated = updated.copy(stabilization = params.getBoolean("stabilization"))
                            }
                            updated
                        }
                    }
                }
                "requestKeyframe" -> {
                    onKeyframeRequested()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming control RPC", e)
        }
    }

    private fun startTelemetryReporter(scope: CoroutineScope) {
        telemetryJob = scope.launch(Dispatchers.IO) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            while (isActive && controlSocket?.isConnected == true) {
                try {
                    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.toFloat() ?: 100f

                    val telemetryObj = JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("method", "telemetry")
                        put("params", JSONObject().apply {
                            put("batteryLevel", batteryPct)
                            put("batteryTemp", 30.5)
                            put("transport", activeTransport.name)
                        })
                    }
                    sendControlMessage(telemetryObj.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Telemetry send exception suppressed: ${e.message}")
                }
                delay(2000)
            }
        }
    }

    private fun sendControlMessage(msg: String) {
        try {
            val out = controlOut ?: return
            val bytes = msg.toByteArray(Charsets.UTF_8)
            val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(bytes.size).array()
            synchronized(out) {
                out.write(lenBytes)
                out.write(bytes)
                out.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send control message: ${e.message}")
        }
    }

    suspend fun sendVideoFrame(data: ByteArray, isKeyframe: Boolean, timestampUs: Long) {
        udpClient.sendMediaFrame(data, AbspConstants.PKT_TYPE_VIDEO, isKeyframe, timestampUs)
    }

    suspend fun sendAudioFrame(pcmData: ByteArray, timestampUs: Long) {
        udpClient.sendMediaFrame(pcmData, AbspConstants.PKT_TYPE_AUDIO, false, timestampUs)
    }

    fun disconnect() {
        controlJob?.cancel()
        controlJob = null
        telemetryJob?.cancel()
        telemetryJob = null
        try {
            controlSocket?.close()
        } catch (e: Exception) {
            // Ignore
        } finally {
            controlSocket = null
            controlIn = null
            controlOut = null
        }
        udpClient.close()
        activeTransport = ActiveTransportType.NONE
    }
}

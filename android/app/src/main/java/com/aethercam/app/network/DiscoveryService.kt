package com.aethercam.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class DiscoveryService(private val context: Context) {

    companion object {
        private const val TAG = "DiscoveryService"
        const val SERVICE_TYPE = "_aethercam._tcp."
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery(onDeviceFound: (host: String, port: Int) -> Unit) {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "mDNS Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.i(TAG, "Discovered AetherCam service: ${service.serviceName}")
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "mDNS resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host.hostAddress ?: return
                        val port = serviceInfo.port
                        Log.i(TAG, "Resolved desktop server at $host:$port")
                        onDeviceFound(host, port)
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.i(TAG, "Lost AetherCam service: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating discovery", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery", e)
            }
        }
        discoveryListener = null
    }
}

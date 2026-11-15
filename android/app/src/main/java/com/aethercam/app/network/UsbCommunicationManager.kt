package com.aethercam.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class UsbCommunicationManager {

    companion object {
        private const val TAG = "UsbCommunicationMgr"
        const val USB_LOCAL_HOST = "127.0.0.1"
        const val USB_CONTROL_PORT = 48201
    }

    /**
     * Checks if the desktop reverse ADB tunnel is active by testing
     * a fast non-blocking connection to 127.0.0.1:48201.
     */
    suspend fun probeUsbConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(USB_LOCAL_HOST, USB_CONTROL_PORT), 300)
                Log.i(TAG, "USB ADB reverse connection active on 127.0.0.1:48201")
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

package com.aethercam.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class AetherBluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "AetherBluetoothManager"
        // Standard Serial Port Profile (SPP) UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    var inputStream: InputStream? = null
        private set
    var outputStream: OutputStream? = null
        private set

    @SuppressLint("MissingPermission")
    fun getPairedDesktops(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.filter { dev ->
            dev.name?.contains("PC", ignoreCase = true) == true ||
            dev.name?.contains("Laptop", ignoreCase = true) == true ||
            dev.name?.contains("Aether", ignoreCase = true) == true ||
            dev.name?.contains("Desktop", ignoreCase = true) == true
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            close()
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter?.cancelDiscovery()
            socket?.connect()
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            Log.i(TAG, "Bluetooth RFCOMM connected to ${device.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth connection failed to ${device.name}", e)
            close()
            false
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Bluetooth socket", e)
        } finally {
            socket = null
            inputStream = null
            outputStream = null
        }
    }
}

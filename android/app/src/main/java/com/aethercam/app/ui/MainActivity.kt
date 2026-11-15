package com.aethercam.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aethercam.app.ui.components.CameraControlsBar
import com.aethercam.app.ui.components.QrScannerDialog
import com.aethercam.app.ui.components.StatsOverlay
import com.aethercam.app.ui.theme.AetherCamTheme
import com.aethercam.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Permissions granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        checkAndRequestPermissions()

        setContent {
            AetherCamTheme {
                MainScreen(viewModel)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val isStreaming by viewModel.isStreaming.collectAsState()
    val activeTransport by viewModel.activeTransport.collectAsState()
    val cameraConfig by viewModel.cameraConfig.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val bitrateMbps by viewModel.bitrateMbps.collectAsState()
    val batteryPct by viewModel.batteryPct.collectAsState()

    var showPairingDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // Camera Preview SurfaceView
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            viewModel.startStreaming(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            viewModel.stopStreaming()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Status Overlay
        StatsOverlay(
            isStreaming = isStreaming,
            transport = activeTransport,
            resolution = cameraConfig.resolution.label,
            fps = fps,
            bitrateMbps = bitrateMbps,
            batteryPct = batteryPct,
            modifier = Modifier.statusBarsPadding()
        )

        // Bottom Controls Bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = androidx.compose.ui.Alignment.BottomCenter
        ) {
            CameraControlsBar(
                currentLens = cameraConfig.lens,
                zoomRatio = cameraConfig.zoomRatio,
                isTorchOn = cameraConfig.torchEnabled,
                isMuted = isMuted,
                onLensChanged = { viewModel.setLens(it) },
                onZoomChanged = { viewModel.setZoom(it) },
                onTorchToggled = { viewModel.toggleTorch() },
                onMuteToggled = { viewModel.toggleMute() },
                onPairingClicked = { showPairingDialog = true }
            )
        }

        // QR Scanner / Manual Pairing Dialog
        if (showPairingDialog) {
            QrScannerDialog(
                onDismiss = { showPairingDialog = false },
                onQrScanned = { rawJson ->
                    viewModel.handleQrScanned(rawJson)
                    showPairingDialog = false
                },
                onManualPinSubmit = { ip, pin ->
                    viewModel.handleManualPairing(ip, pin)
                    showPairingDialog = false
                }
            )
        }
    }
}

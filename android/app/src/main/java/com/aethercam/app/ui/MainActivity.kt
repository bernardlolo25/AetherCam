package com.aethercam.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aethercam.app.ui.components.CameraControlsBar
import com.aethercam.app.ui.components.QrScannerDialog
import com.aethercam.app.ui.components.StatsOverlay
import com.aethercam.app.ui.theme.AetherCamTheme
import com.aethercam.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var currentSurface: Surface? = null

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // User granted permissions! Start streaming immediately if surface is ready
            currentSurface?.let { surface ->
                if (surface.isValid) {
                    viewModel.startStreaming(surface)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        checkAndRequestPermissions()

        setContent {
            AetherCamTheme {
                MainScreen(
                    viewModel = viewModel,
                    onSurfaceAvailable = { surface ->
                        currentSurface = surface
                        if (hasAllPermissions() && surface.isValid) {
                            viewModel.startStreaming(surface)
                        }
                    },
                    onSurfaceDestroyed = {
                        currentSurface = null
                        viewModel.stopStreaming()
                    },
                    onOpenScanner = {
                        viewModel.pauseCameraForQrScan()
                    },
                    onScannerClosed = {
                        currentSurface?.let { surface ->
                            if (surface.isValid && hasAllPermissions()) {
                                viewModel.resumeCameraAfterQrScan(surface)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            currentSurface?.let { surface ->
                if (surface.isValid && !viewModel.isStreaming.value) {
                    viewModel.startStreaming(surface)
                }
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
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
fun MainScreen(
    viewModel: MainViewModel,
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onOpenScanner: () -> Unit,
    onScannerClosed: () -> Unit
) {
    val isStreaming by viewModel.isStreaming.collectAsState()
    val activeTransport by viewModel.activeTransport.collectAsState()
    val cameraConfig by viewModel.cameraConfig.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val bitrateMbps by viewModel.bitrateMbps.collectAsState()
    val batteryPct by viewModel.batteryPct.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()

    var showPairingDialog by remember { mutableStateOf(false) }

    // Auto-dismiss status message after 4 seconds without touching layout
    LaunchedEffect(connectionStatus) {
        if (connectionStatus != null) {
            delay(4000)
            viewModel.clearConnectionStatus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // Camera Preview SurfaceView (fixed fullscreen, never resized by popups)
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            try {
                                onSurfaceAvailable(holder.surface)
                            } catch (t: Throwable) {
                                android.util.Log.e("MainActivity", "Surface created error: ${t.message}")
                            }
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            try {
                                onSurfaceDestroyed()
                            } catch (t: Throwable) {
                                android.util.Log.e("MainActivity", "Surface destroyed error: ${t.message}")
                            }
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

        // Floating Connection Status Banner (overlay, never resizes SurfaceView)
        if (connectionStatus != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF475569)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = connectionStatus ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Bottom Controls Bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
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
                onDismiss = {
                    showPairingDialog = false
                },
                onQrScanned = { rawJson ->
                    viewModel.handleQrScanned(rawJson)
                    showPairingDialog = false
                },
                onManualPinSubmit = { ip, pin ->
                    viewModel.handleManualPairing(ip, pin)
                    showPairingDialog = false
                },
                onOpenScanner = onOpenScanner,
                onScannerClosed = onScannerClosed
            )
        }
    }
}

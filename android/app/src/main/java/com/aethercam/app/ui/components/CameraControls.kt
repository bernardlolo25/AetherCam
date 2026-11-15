package com.aethercam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aethercam.app.camera.LensType

@Composable
fun CameraControlsBar(
    currentLens: LensType,
    zoomRatio: Float,
    isTorchOn: Boolean,
    isMuted: Boolean,
    onLensChanged: (LensType) -> Unit,
    onZoomChanged: (Float) -> Unit,
    onTorchToggled: () -> Unit,
    onMuteToggled: () -> Unit,
    onPairingClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Zoom Preset Chips (0.5x, 1x, 2x, 5x)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xCC090D16))
                .border(1.dp, Color(0x30374151), RoundedCornerShape(20.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val presets = listOf(0.5f, 1.0f, 2.0f, 5.0f)
            presets.forEach { z ->
                val isSelected = (zoomRatio - z).let { kotlin.math.abs(it) < 0.1f }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF6366F1) else Color.Transparent)
                        .clickable { onZoomChanged(z) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${z}x",
                        color = if (isSelected) Color.White else Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Main Bottom Floating Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xEE111827))
                .border(1.dp, Color(0x40374151), RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lens Switcher Button
            IconButton(
                onClick = {
                    val nextLens = when (currentLens) {
                        LensType.WIDE -> LensType.ULTRA_WIDE
                        LensType.ULTRA_WIDE -> LensType.TELEPHOTO
                        LensType.TELEPHOTO -> LensType.FRONT
                        LensType.FRONT -> LensType.WIDE
                    }
                    onLensChanged(nextLens)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch Camera Lens",
                    tint = Color.White
                )
            }

            // Torch Toggle
            IconButton(
                onClick = onTorchToggled,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isTorchOn) Color(0x30F59E0B) else Color.Transparent)
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    tint = if (isTorchOn) Color(0xFFFBBF24) else Color(0xFF9CA3AF)
                )
            }

            // Audio Mute Toggle
            IconButton(
                onClick = onMuteToggled,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isMuted) Color(0x30EF4444) else Color.Transparent)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Microphone",
                    tint = if (isMuted) Color(0xFFEF4444) else Color.White
                )
            }

            // QR Pairing Scanner Button
            IconButton(
                onClick = onPairingClicked,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1))
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan Pairing Code",
                    tint = Color.White
                )
            }
        }
    }
}

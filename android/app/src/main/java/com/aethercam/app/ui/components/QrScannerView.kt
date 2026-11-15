package com.aethercam.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (rawJson: String) -> Unit,
    onManualPinSubmit: (ip: String, pin: String) -> Unit,
    onOpenScanner: () -> Unit = {},
    onScannerClosed: () -> Unit = {}
) {
    var manualIp by remember { mutableStateOf("192.168.1.108") }
    var manualPin by remember { mutableStateOf("849201") }
    var isManualMode by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        onScannerClosed()
        val content = result.contents
        if (!content.isNullOrBlank()) {
            onQrScanned(content)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, Color(0xFF374151), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isManualMode) "Manual Pairing" else "Pair With Desktop",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF9CA3AF)
                        )
                    }
                }

                if (!isManualMode) {
                    // QR Viewfinder Simulation Frame
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF090D16))
                            .border(2.dp, Color(0xFF6366F1), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "Scan QR code displayed on desktop",
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Or connect instantly over USB / Local Wi-Fi",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Button 1: Open Camera QR Scanner (ZXing)
                    Button(
                        onClick = {
                            onOpenScanner()
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Point camera at AetherCam Desktop QR code")
                                setCameraId(0)
                                setBeepEnabled(false)
                                setBarcodeImageEnabled(false)
                                setOrientationLocked(false)
                            }
                            scanLauncher.launch(options)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Camera QR Scanner", fontWeight = FontWeight.Bold)
                    }

                    // Button 2: Quick instant connect (USB/LAN loopback or local subnet)
                    OutlinedButton(
                        onClick = {
                            val sampleQr = """{"v":2,"ip":"127.0.0.1","udpPort":48200,"tcpPort":48201,"pin":"849201","serverPubKey":"BM7v9LqP3rM1z0NvQ8sYt4XuKgH2jR5wFpE6aN8="}"""
                            onQrScanned(sampleQr)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Instant Connect (USB / Localhost)", color = Color(0xFF818CF8), fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(onClick = { isManualMode = true }) {
                        Text("Or enter IP and PIN manually", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                } else {
                    // Manual PIN Entry
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("Desktop IP Address", color = Color(0xFF9CA3AF)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White)
                    )

                    OutlinedTextField(
                        value = manualPin,
                        onValueChange = { manualPin = it },
                        label = { Text("6-Digit PIN", color = Color(0xFF9CA3AF)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White)
                    )

                    Button(
                        onClick = { onManualPinSubmit(manualIp, manualPin) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pair Device", fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = { isManualMode = false }) {
                        Text("Back to QR Scanner", color = Color(0xFF818CF8), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

package com.aethercam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (rawJson: String) -> Unit,
    onManualPinSubmit: (ip: String, pin: String) -> Unit
) {
    var manualIp by remember { mutableStateOf("192.168.1.108") }
    var manualPin by remember { mutableStateOf("849201") }
    var isManualMode by remember { mutableStateOf(false) }

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
                        text = if (isManualMode) "Manual Pairing" else "Scan Desktop QR Code",
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
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF090D16))
                            .border(2.dp, Color(0xFF6366F1), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "Point camera at Desktop screen",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Quick test scan button
                    Button(
                        onClick = {
                            val sampleQr = """{"v":2,"ip":"192.168.1.108","udpPort":48200,"tcpPort":48201,"pin":"849201","serverPubKey":"BM7v9LqP3rM1z0NvQ8sYt4XuKgH2jR5wFpE6aN8="}"""
                            onQrScanned(sampleQr)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect Instantly", fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = { isManualMode = true }) {
                        Text("Or enter IP and PIN manually", color = Color(0xFF818CF8), fontSize = 12.sp)
                    }
                } else {
                    // Manual PIN Entry
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("Desktop IP Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF374151)
                        )
                    )

                    OutlinedTextField(
                        value = manualPin,
                        onValueChange = { manualPin = it },
                        label = { Text("6-Digit PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF374151)
                        )
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

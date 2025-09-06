package org.kvxd.nanocompanion.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.kvxd.nanocompanion.ble.BLEController

@Composable
fun BleDeviceScannerScreen(bleController: BLEController) {
    val devices = bleController.scannedDevices
    val connectedDeviceAddress by remember { bleController.connectedDeviceAddress }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(16.dp)
    ) {
        Text("Select your device", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(8.dp))

        val sortedDevices = devices.sortedWith(compareBy { it.address != connectedDeviceAddress })

        LazyColumn {
            items(sortedDevices) { device ->
                val isConnected = device.address == connectedDeviceAddress

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            if (isConnected) {
                                bleController.connectToDevice(device.device)
                                Toast.makeText(
                                    context,
                                    "Disconnecting from device...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                bleController.stopScan()
                                bleController.connectToDevice(device.device)
                                Toast.makeText(
                                    context,
                                    "Connecting to device...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConnected) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = device.name ?: "Unnamed device",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        if (isConnected) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }
        }
    }
}
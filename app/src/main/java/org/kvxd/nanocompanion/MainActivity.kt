package org.kvxd.nanocompanion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.compose.material3.*
import androidx.core.app.ActivityCompat


@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var bleController: BLEController
    private val REQUEST_CODE_BLE = 1001

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        if (!MediaControl.hasPermissions(this)) {
            Toast.makeText(
                this,
                "Media access permission required. Please grant access on the next screen.",
                Toast.LENGTH_LONG
            ).show()
            MediaControl.requestPermission(this)
        }

        bleController = BLEController(this)

        setContent {
            BleDeviceScannerScreen(bleController)
        }
    }

    override fun onResume() {
        super.onResume()

        if (MediaControl.hasPermissions(this)) {
            try {
                MediaControl.initialize(this)
            } catch (e: SecurityException) {
                Toast.makeText(this, "Missing permission to control media.", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            ActivityCompat.requestPermissions(this, perms, REQUEST_CODE_BLE)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_BLE
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)

        if (requestCode == REQUEST_CODE_BLE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                bleController.startScan()
            } else {
                Toast.makeText(this, "BLE permissions are required", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
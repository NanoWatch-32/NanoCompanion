package org.kvxd.nanocompanion.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import org.kvxd.nanocompanion.MediaControl
import org.kvxd.nanocompanion.protocol.Packet
import org.kvxd.nanocompanion.protocol.PacketFactory
import org.kvxd.nanocompanion.protocol.PacketType
import org.kvxd.nanocompanion.protocol.ReadBuffer
import org.kvxd.nanocompanion.protocol.WriteBuffer
import org.kvxd.nanocompanion.protocol.packet.MediaCommandPacket
import java.util.UUID

class BLEController(private val context: Context) {

    val scannedDevices = mutableStateListOf<BleDevice>()
    val connectedDeviceAddress = mutableStateOf<String?>(null)
    private var connectedGatt: BluetoothGatt? = null
    private var isScanning = mutableStateOf(false)

    private val serviceUUID = UUID.fromString("0b60ab11-bc40-4d00-9ea4-5f2406872d9f")
    private val characteristicUUID = UUID.fromString("cfa93afb-2c3d-4a76-a182-67e8b6d50b55")
    private val cccdUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val fragmentBuffer = mutableListOf<Byte>()
    private var expectedFragments = 0
    private var receivedFragments = 0
    private var currentPacketType: PacketType? = null

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (scannedDevices.none { it.address == device.address }) {
                scannedDevices.add(
                    BleDevice(
                        name = device.name ?: "Unknown Device",
                        address = device.address,
                        device = device
                    )
                )

                scannedDevices.sortBy { it.name != null }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "Scan failed with error: $errorCode")
            isScanning.value = false

            when (errorCode) {
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                    Log.e("BLE", "App registration failed for scanning")
                SCAN_FAILED_INTERNAL_ERROR ->
                    Log.e("BLE", "Internal error occurred during scanning")
                SCAN_FAILED_FEATURE_UNSUPPORTED ->
                    Log.e("BLE", "BLE scanning not supported on this device")
                SCAN_FAILED_ALREADY_STARTED ->
                    Log.e("BLE", "Scan already started")
                else ->
                    Log.e("BLE", "Unknown scan error: $errorCode")
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Connected to $deviceAddress")
                connectedDeviceAddress.value = deviceAddress

                MediaControl.notifyMediaChanged()

                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from $deviceAddress")
                if (deviceAddress == connectedDeviceAddress.value) {
                    connectedDeviceAddress.value = null
                    connectedGatt = null
                }
                gatt.close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Services discovered for ${gatt.device.address}")

                val service = gatt.getService(serviceUUID)
                if (service == null) {
                    Log.e("BLE", "Service not found: $serviceUUID")
                    return
                }

                val characteristic = service.getCharacteristic(characteristicUUID)
                if (characteristic == null) {
                    Log.e("BLE", "Characteristic not found: $characteristicUUID")
                    return
                }

                gatt.setCharacteristicNotification(characteristic, true)

                val descriptor = characteristic.getDescriptor(cccdUUID)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                    Log.d("BLE", "Enabled notifications for characteristic")
                } else {
                    Log.e("BLE", "CCCD descriptor not found")
                }
            } else {
                Log.w("BLE", "Service discovery failed: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Characteristic write successful: ${characteristic.uuid}")
            } else {
                Log.e("BLE", "Characteristic write failed: $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d("BLE", "Characteristic changed: ${characteristic.uuid}")

            val value = characteristic.value
            if (value.size < 2) {
                Log.e("BLE", "Received data too short: ${value.size} bytes")
                return
            }

            val packetTypeByte = value[0].toUByte()
            val fragHeader = value[1].toUByte()
            val fragmentIndex = (fragHeader.toInt() shr 4) and 0x0F
            val totalFragments = fragHeader.toInt() and 0x0F

            val payload = value.sliceArray(2 until value.size)

            Log.d("BLE", "Received fragment $fragmentIndex/$totalFragments for packet type $packetTypeByte")

            if (fragmentIndex == 0) {
                currentPacketType = PacketType.entries.firstOrNull { it.value == packetTypeByte }

                fragmentBuffer.clear()
                fragmentBuffer.addAll(payload.toList())
                expectedFragments = totalFragments
                receivedFragments = 1
            } else {
                // subsequent fragments
                if (fragmentIndex == receivedFragments) {
                    fragmentBuffer.addAll(payload.toList())
                    receivedFragments++
                } else {
                    // Out of order fragment; reset
                    Log.e("BLE", "Out of order fragment: expected $receivedFragments, got $fragmentIndex")

                    fragmentBuffer.clear()
                    expectedFragments = 0
                    receivedFragments = 0
                    currentPacketType = null
                    return
                }
            }

            // All fragments present?
            if (receivedFragments == expectedFragments) {
                Log.d("BLE", "All fragments received, decoding packet")

                val packet = currentPacketType?.let { PacketFactory.createPacketFromType(it) }

                if (packet == null) {
                    Log.e("BLE", "Unknown packet tpye: $packetTypeByte")
                    return
                }

                val readBuffer = ReadBuffer(fragmentBuffer.toByteArray())
                val decodedPacket = packet.decode(readBuffer)

                Log.d("BLE", "Received packet: ${decodedPacket::class.simpleName}")

                if (decodedPacket is MediaCommandPacket) {
                    Log.d("BLE", "COMMAND: ${decodedPacket.command}")
                    when (decodedPacket.command) {
                        1 -> MediaControl.play()
                        2 -> MediaControl.pause()
                        3 -> MediaControl.next()
                        4 -> MediaControl.previous()
                    }
                }

                // reset fragmentation state
                fragmentBuffer.clear()
                expectedFragments = 0
                receivedFragments = 0
                currentPacketType = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBlePermissions()) {
            Log.e("BLE", "Cannot scan: Missing permissions")
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("BLE", "Cannot scan: Bluetooth not available or disabled")
            return
        }

        if (scanner == null) {
            Log.e("BLE", "Cannot scan: BLE scanner not available")
            return
        }

        if (isScanning.value) {
            stopScan()
        }

        scannedDevices.clear()

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val filters = mutableListOf<ScanFilter>()
            scanner.startScan(filters, settings, scanCallback)
            isScanning.value = true
            Log.d("BLE", "BLE scan started successfully")
        } catch (e: SecurityException) {
            Log.e("BLE", "Security exception when starting scan: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e("BLE", "Illegal state when starting scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (isScanning.value && hasBlePermissions() && scanner != null) {
            try {
                scanner.stopScan(scanCallback)
                isScanning.value = false
                Log.d("BLE", "BLE scan stopped")
            } catch (e: Exception) {
                Log.e("BLE", "Error stopping scan: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBlePermissions()) {
            Log.e("BLE", "Cannot connect: Missing permissions")
            return
        }

        if (device.address == connectedDeviceAddress.value) {
            connectedGatt?.disconnect()
            return
        }

        connectedGatt?.disconnect()

        connectedGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun sendPacket(packet: Packet) {
        if (connectedGatt == null) {
            Log.e("BLE", "Cannot send packet: Not connected")
            return
        }

        val buffer = WriteBuffer()
        packet.encode(buffer)
        val encodedData = buffer.toByteArray()

        val maxChunkSize = 128 - 2
        val totalLength = encodedData.size
        val numFragments = (totalLength + maxChunkSize - 1) / maxChunkSize

        if (numFragments > 15) {
            Log.e("BLE", "Packet of type ${packet.packetType} too large for fragmentation")
            return
        }

        val service = connectedGatt?.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)

        if (characteristic == null) {
            Log.e("BLE", "Characteristic not found")
            return
        }

        Log.d("BLE", "Sending packet ${packet.packetType} in $numFragments fragments, size: $totalLength bytes")

        for (i in 0 until numFragments) {
            val header = byteArrayOf(
                packet.packetType.value.toByte(),
                ((i shl 4) or numFragments).toByte()
            )

            val start = i * maxChunkSize
            val end = minOf(start + maxChunkSize, totalLength)
            val chunk = encodedData.copyOfRange(start, end)

            val fragment = header + chunk
            characteristic.value = fragment

            var success = false
            var attempts = 0
            val maxAttempts = 5

            while (!success && attempts < maxAttempts) {
                success = connectedGatt?.writeCharacteristic(characteristic) == true
                if (!success) {
                    attempts++
                    Log.w("BLE", "Failed to write fragment $i, attempt $attempts")
                    Thread.sleep(30)
                }
            }

            if (!success) {
                Log.e("BLE", "Failed to send fragment $i after $maxAttempts attempts")
                return
            }

            Thread.sleep(200) // avoid overwhelming the esp
        }

        Log.d("BLE", "Successfully sent packet: ${packet.packetType}")
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
}
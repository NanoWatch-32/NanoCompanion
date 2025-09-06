package org.kvxd.nanocompanion.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import io.github.kvxd.ksignal.Signal
import org.kvxd.nanocompanion.protocol.Packet
import org.kvxd.nanocompanion.protocol.PacketFactory
import org.kvxd.nanocompanion.protocol.PacketType
import org.kvxd.nanocompanion.protocol.ReadBuffer
import org.kvxd.nanocompanion.protocol.WriteBuffer
import java.util.UUID

class BLEController(private val context: Context) {

    val scannedDevices = mutableStateListOf<BleDevice>()
    val connectedDeviceAddress = mutableStateOf<String?>(null)
    private var connectedGatt: BluetoothGatt? = null
    private var isScanning = mutableStateOf(false)

    private val packetQueue = mutableListOf<Packet>()
    private var isWriting = false

    private val serviceUUID = UUID.fromString("0b60ab11-bc40-4d00-9ea4-5f2406872d9f")
    private val characteristicUUID = UUID.fromString("cfa93afb-2c3d-4a76-a182-67e8b6d50b55")
    private val cccdUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val connectedSignal = Signal<Unit>()
    val packetReceivedSignal = Signal<Packet>()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (scannedDevices.none { it.address == device.address }) {
                if (device.name != null)
                    scannedDevices.add(
                        BleDevice(
                            name = device.name,
                            address = device.address,
                            device = device
                        )
                    )
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

                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from $deviceAddress")
                if (deviceAddress == connectedDeviceAddress.value) {
                    connectedDeviceAddress.value = null
                    connectedGatt = null
                }
                synchronized(packetQueue) {
                    packetQueue.clear()
                    isWriting = false

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
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
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
            if (characteristic.uuid != characteristicUUID) return

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Characteristic write successful: ${characteristic.uuid}")
            } else {
                Log.e("BLE", "Characteristic write failed: $status")
            }

            processNextPacket()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == cccdUUID && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "CCCD descriptor write successful")

                Handler(Looper.getMainLooper()).postDelayed({
                    connectedSignal.emit(Unit)
                }, 500) // delay
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d("BLE", "Characteristic changed: ${characteristic.uuid}")

            if (value.isEmpty()) {
                Log.e("BLE", "Received data too short: ${value.size} bytes")
                return
            }

            val packetTypeByte = value[0].toUByte()
            val payload = value.sliceArray(1 until value.size)

            val packetType = PacketType.entries.firstOrNull { it.value == packetTypeByte }
            if (packetType == null) {
                Log.e("BLE", "Unknown packet type: $packetTypeByte")
                return
            }

            val packet = PacketFactory.createPacketFromType(packetType)
            val readBuffer = ReadBuffer(payload)
            val decodedPacket = packet.decode(readBuffer)

            Log.d("BLE", "Received packet: ${decodedPacket::class.simpleName}")
            packetReceivedSignal.emit(decodedPacket)
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

        synchronized(packetQueue) {
            packetQueue.add(packet)
            if (!isWriting) {
                isWriting = true
                processNextPacket()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun processNextPacket() {
        val packet: Packet?
        synchronized(packetQueue) {
            if (packetQueue.isEmpty()) {
                isWriting = false
                return
            }
            packet = packetQueue.removeAt(0)
        }

        if (packet == null) return

        val buffer = WriteBuffer()
        packet.encode(buffer)
        val encodedData = buffer.toByteArray()

        val maxSize = 128 - 1
        if (encodedData.size > maxSize) {
            Log.e("BLE", "Packet too large for single transmission")
            processNextPacket()
            return
        }

        val service = connectedGatt?.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)

        if (characteristic == null) {
            Log.e("BLE", "Characteristic not found")
            processNextPacket()
            return
        }

        val data = byteArrayOf(packet.packetType.value.toByte()) + encodedData
        val writeType = characteristic.writeType

        val code = connectedGatt?.writeCharacteristic(characteristic, data, writeType)
        val success = code == BluetoothStatusCodes.SUCCESS

        if (!success) {
            Log.e("BLE", "Failed to initiate write for packet ${packet.packetType}")
            processNextPacket()
        }
    }

    private fun hasBlePermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
    }
}
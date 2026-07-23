package com.towersys.adaptiveremote.device.diagnostics

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class BleDeviceDiagnostic(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun scan(scanDurationMs: Long = DEFAULT_SCAN_DURATION_MS): Flow<List<BleDeviceCandidate>> = callbackFlow {
        val adapter = bluetoothManager?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            close(IllegalStateException("Bluetooth is turned off or unavailable."))
            return@callbackFlow
        }

        val candidates = linkedMapOf<String, BleDeviceCandidate>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName
                    ?: runCatching { result.device.name }.getOrNull()
                    ?: "Unnamed BLE device"
                val candidate = BleDeviceCandidate(name, result.device.address, result.rssi)
                candidates[candidate.address] = candidate
                trySend(
                    candidates.values.sortedByDescending { it.rssi },
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Bluetooth scan failed (code $errorCode)."))
            }
        }

        val stopScan = Runnable {
            runCatching { scanner.stopScan(callback) }
            channel.close()
        }

        try {
            scanner.startScan(
                emptyList(),
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                callback,
            )
            mainHandler.postDelayed(stopScan, scanDurationMs)
        } catch (error: SecurityException) {
            close(IllegalStateException("Nearby-device permission was not granted.", error))
        }

        awaitClose {
            mainHandler.removeCallbacks(stopScan)
            runCatching { scanner.stopScan(callback) }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun inspect(candidate: BleDeviceCandidate): Result<DeviceDiagnosticReport> =
        suspendCancellableCoroutine { continuation ->
            val adapter = bluetoothManager?.adapter
            if (adapter == null || !adapter.isEnabled) {
                continuation.resume(Result.failure(IllegalStateException("Bluetooth is turned off or unavailable.")))
                return@suspendCancellableCoroutine
            }

            val completed = AtomicBoolean(false)
            var gatt: BluetoothGatt? = null

            fun finish(result: Result<DeviceDiagnosticReport>) {
                if (completed.compareAndSet(false, true)) {
                    runCatching { gatt?.disconnect() }
                    runCatching { gatt?.close() }
                    if (continuation.isActive) continuation.resume(result)
                }
            }

            val timeout = Runnable {
                finish(Result.failure(IllegalStateException("Timed out while inspecting the device.")))
            }

            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.failure(IllegalStateException("GATT connection failed (status $status).")))
                        return
                    }
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> if (!connection.discoverServices()) {
                            finish(Result.failure(IllegalStateException("Could not start service discovery.")))
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            finish(Result.failure(IllegalStateException("Device disconnected during inspection.")))
                        }
                    }
                }

                override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(Result.failure(IllegalStateException("Service discovery failed (status $status).")))
                        return
                    }
                    mainHandler.removeCallbacks(timeout)
                    finish(
                        Result.success(
                            DeviceDiagnosticReport(
                                deviceName = candidate.name,
                                deviceAddress = candidate.address,
                                services = connection.services.map { it.toReport() }.sortedBy { it.uuid },
                            ),
                        ),
                    )
                }
            }

            try {
                val device = adapter.getRemoteDevice(candidate.address)
                gatt = device.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
                mainHandler.postDelayed(timeout, INSPECTION_TIMEOUT_MS)
            } catch (error: Exception) {
                finish(Result.failure(error))
            }

            continuation.invokeOnCancellation {
                mainHandler.removeCallbacks(timeout)
                if (completed.compareAndSet(false, true)) {
                    runCatching { gatt?.disconnect() }
                    runCatching { gatt?.close() }
                }
            }
        }

    private fun BluetoothGattService.toReport() = BleServiceReport(
        uuid = uuid.toString(),
        characteristics = characteristics.map { characteristic ->
            BleCharacteristicReport(characteristic.uuid.toString(), characteristic.propertyNames())
        }.sortedBy { it.uuid },
    )

    private fun BluetoothGattCharacteristic.propertyNames(): Set<String> = buildSet {
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("BROADCAST")
        if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("SIGNED_WRITE")
    }

    companion object {
        const val DEFAULT_SCAN_DURATION_MS = 10_000L
        const val INSPECTION_TIMEOUT_MS = 15_000L
        const val PROBE_VALUE = 16
        const val PROBE_DURATION_MS = 650L
    }
}

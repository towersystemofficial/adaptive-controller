package com.towersys.adaptiveremote.device.diagnostics

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.towersys.adaptiveremote.device.control.KnownKnight
import com.towersys.adaptiveremote.device.control.KnownKnightStore
import com.towersys.adaptiveremote.device.control.KnightControlState
import com.towersys.adaptiveremote.device.control.KnightControlService
import com.towersys.adaptiveremote.device.control.KnightConnectionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class BleDiagnosticViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val diagnostic = KnightBleDiagnostic(application.applicationContext)
    private val knownKnightStore = KnownKnightStore(application.applicationContext)
    private val _status = MutableStateFlow<BleDiagnosticStatus>(BleDiagnosticStatus.Idle)
    val status: StateFlow<BleDiagnosticStatus> = _status.asStateFlow()
    val connection = KnightControlState.connection
    private var scanJob: Job? = null

    fun startScan() {
        scanJob?.cancel()
        _status.value = BleDiagnosticStatus.Scanning
        scanJob = viewModelScope.launch {
            disconnectService()
            delay(SCAN_DISCONNECT_SETTLE_MS)
            var lastDevices = emptyList<BleDeviceCandidate>()
            diagnostic.scan()
                .catch { error ->
                    _status.value = BleDiagnosticStatus.Error(error.message ?: "Bluetooth scan failed.")
                }
                .collect { devices ->
                    lastDevices = devices
                    _status.value = BleDiagnosticStatus.DevicesFound(devices)
                }
            if (_status.value !is BleDiagnosticStatus.Error) {
                _status.value = BleDiagnosticStatus.DevicesFound(lastDevices)
            }
        }
    }

    fun inspect(candidate: BleDeviceCandidate) {
        scanJob?.cancel()
        _status.value = BleDiagnosticStatus.Inspecting(candidate)
        viewModelScope.launch {
            diagnostic.inspect(candidate)
                .onSuccess {
                    val report = it
                    if (report.hasExpectedJoyHubTransport) {
                        val device = KnownKnight(report.deviceName, report.deviceAddress)
                        knownKnightStore.save(device)
                        KnightControlState.knownDevice.value = device
                        delay(INSPECTION_DISCONNECT_SETTLE_MS)
                        connectService(device)
                    }
                    _status.value = BleDiagnosticStatus.Complete(report)
                }
                .onFailure { error ->
                    _status.value = BleDiagnosticStatus.Error(error.message ?: "Device inspection failed.")
                }
        }
    }

    fun runLowOutputProbe(report: KnightDiagnosticReport) {
        _status.value = BleDiagnosticStatus.TestingCommand(report)
        viewModelScope.launch {
            if (KnightControlState.connection.value !is KnightConnectionStatus.Ready) {
                _status.value = BleDiagnosticStatus.Error("Wait for the persistent Knight connection, then retry.")
                return@launch
            }
            app.startService(
                Intent(app, KnightControlService::class.java)
                    .setAction(KnightControlService.ACTION_SET_LEVEL)
                    .putExtra(KnightControlService.EXTRA_LEVEL, KnightBleDiagnostic.PROBE_VALUE),
            )
            delay(KnightBleDiagnostic.PROBE_DURATION_MS)
            app.startService(Intent(app, KnightControlService::class.java).setAction(KnightControlService.ACTION_STOP))
            _status.value = BleDiagnosticStatus.CommandAccepted(report)
        }
    }

    fun disconnectAndReset() {
        scanJob?.cancel()
        disconnectService()
        _status.value = BleDiagnosticStatus.Idle
    }

    fun reset() {
        scanJob?.cancel()
        _status.value = BleDiagnosticStatus.Idle
    }

    private fun connectService(device: KnownKnight) {
        ContextCompat.startForegroundService(
            app,
            Intent(app, KnightControlService::class.java)
                .setAction(KnightControlService.ACTION_CONNECT)
                .putExtra(KnightControlService.EXTRA_NAME, device.name)
                .putExtra(KnightControlService.EXTRA_ADDRESS, device.address),
        )
    }

    private fun disconnectService() {
        runCatching {
            app.startService(
                Intent(app, KnightControlService::class.java).setAction(KnightControlService.ACTION_DISCONNECT),
            )
        }
        KnightControlState.connection.value = KnightConnectionStatus.Disconnected
    }

    companion object {
        private const val SCAN_DISCONNECT_SETTLE_MS = 500L
        private const val INSPECTION_DISCONNECT_SETTLE_MS = 350L
    }
}

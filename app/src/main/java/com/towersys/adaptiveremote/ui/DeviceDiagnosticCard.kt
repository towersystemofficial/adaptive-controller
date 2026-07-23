package com.towersys.adaptiveremote.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.towersys.adaptiveremote.device.diagnostics.BleDeviceCandidate
import com.towersys.adaptiveremote.device.diagnostics.BleDiagnosticStatus
import com.towersys.adaptiveremote.device.diagnostics.BleDiagnosticViewModel
import com.towersys.adaptiveremote.device.control.DeviceConnectionStatus
import com.towersys.adaptiveremote.device.protocol.AdapterSupportStatus

@Composable
fun DeviceDiagnosticCard(viewModel: BleDiagnosticViewModel = viewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.BLUETOOTH_SCAN] == true &&
            grants[Manifest.permission.BLUETOOTH_CONNECT] == true
        ) viewModel.startScan()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Bluetooth, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Device setup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        status.summary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (status) {
                    BleDiagnosticStatus.Scanning,
                    is BleDiagnosticStatus.Inspecting,
                    is BleDiagnosticStatus.TestingCommand,
                    -> CircularProgressIndicator()
                    else -> Button(
                        onClick = {
                            permissionLauncher.launch(
                                buildList {
                                    add(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                    )
                                    add(
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray(),
                            )
                        },
                    ) { Text(if (connection is DeviceConnectionStatus.Ready) "Rescan" else "Scan") }
                }
            }

            HorizontalDivider()
            Text(
                connection.setupSummary(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (connection is DeviceConnectionStatus.Error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            Text(
                "The persistent connection starts only after this scan confirms a supported control channel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val current = status) {
                is BleDiagnosticStatus.DevicesFound -> CandidateList(current.devices, viewModel::inspect)
                is BleDiagnosticStatus.Complete -> ReportContent(
                    report = current.report,
                    commandAccepted = false,
                    onRunProbe = if (
                        connection is DeviceConnectionStatus.Ready && current.report.probeCapability != null
                    ) {
                        { viewModel.runLowOutputProbe(current.report) }
                    } else null,
                    onDisconnect = viewModel::disconnectAndReset,
                )
                is BleDiagnosticStatus.CommandAccepted -> ReportContent(
                    report = current.report,
                    commandAccepted = true,
                    onRunProbe = null,
                    onDisconnect = viewModel::disconnectAndReset,
                )
                is BleDiagnosticStatus.Error -> {
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = viewModel::reset) { Text("Clear") }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun CandidateList(
    devices: List<BleDeviceCandidate>,
    onInspect: (BleDeviceCandidate) -> Unit,
) {
    val visible = devices.take(6)
    if (visible.isEmpty()) {
        Text("No BLE devices found. Power on your device and scan again.")
        return
    }
    visible.forEach { device ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Signal ${device.rssi} dBm • ${device.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { onInspect(device) }) { Text("Inspect") }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ReportContent(
    report: com.towersys.adaptiveremote.device.diagnostics.DeviceDiagnosticReport,
    commandAccepted: Boolean,
    onRunProbe: (() -> Unit)?,
    onDisconnect: () -> Unit,
) {
    Text(
        when (report.matchedAdapter?.supportStatus) {
            AdapterSupportStatus.VERIFIED -> "Verified protocol match found and saved."
            AdapterSupportStatus.EXPERIMENTAL -> "Experimental protocol match found and saved."
            null -> "A supported control channel was not found."
        },
        color = if (report.matchedAdapter != null) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        style = MaterialTheme.typography.titleSmall,
    )
    if (report.matchedAdapter?.supportStatus == AdapterSupportStatus.EXPERIMENTAL) {
        Text(
            "This protocol has not completed physical verification. Use the controlled test only with compatible hardware available.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (commandAccepted) {
        Text(
            "Android accepted the brief start and stop commands. Confirm what the hardware physically did.",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else if (onRunProbe != null && report.matchedAdapter != null) {
        Text(
            "Controlled test: one ~6% ${report.probeCapability?.name?.lowercase()} command for 0.65 seconds, followed automatically by stop.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRunProbe) { Text("Run brief low-output test") }
    }
    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
}

private fun DeviceConnectionStatus.setupSummary(): String = when (this) {
    DeviceConnectionStatus.Disconnected -> "Not connected • scan to begin"
    is DeviceConnectionStatus.Connecting -> "Connecting persistently to ${device.name}…"
    is DeviceConnectionStatus.Ready -> "${device.name} persistently connected"
    is DeviceConnectionStatus.Error -> message
}

private fun BleDiagnosticStatus.summary(): String = when (this) {
    BleDiagnosticStatus.Idle -> "Ready to find a device"
    BleDiagnosticStatus.Scanning -> "Scanning for nearby BLE devices…"
    is BleDiagnosticStatus.DevicesFound -> "${devices.size} nearby device(s) found"
    is BleDiagnosticStatus.Inspecting -> "Inspecting ${device.name}…"
    is BleDiagnosticStatus.Complete -> "Device saved"
    is BleDiagnosticStatus.TestingCommand -> "Running brief low-output test…"
    is BleDiagnosticStatus.CommandAccepted -> "Start and stop commands accepted"
    is BleDiagnosticStatus.Error -> "Diagnostic needs attention"
}

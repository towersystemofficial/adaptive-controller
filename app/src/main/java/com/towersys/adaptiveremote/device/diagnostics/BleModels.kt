package com.towersys.adaptiveremote.device.diagnostics

import com.towersys.adaptiveremote.device.protocol.BleProtocolAdapter
import com.towersys.adaptiveremote.device.protocol.DeviceProtocolRegistry
import com.towersys.adaptiveremote.device.protocol.DiscoveredBleService
import java.util.UUID

data class BleDeviceCandidate(val name: String, val address: String, val rssi: Int)

data class BleCharacteristicReport(val uuid: String, val properties: Set<String>)

data class BleServiceReport(
    val uuid: String,
    val characteristics: List<BleCharacteristicReport>,
)

data class DeviceDiagnosticReport(
    val deviceName: String,
    val deviceAddress: String,
    val services: List<BleServiceReport>,
) {
    val matchedAdapter: BleProtocolAdapter?
        get() = DeviceProtocolRegistry.match(discoveredServices(), deviceName)

    val probeCapability
        get() = matchedAdapter?.capabilities?.singleOrNull()

    fun discoveredServices(): List<DiscoveredBleService> = services.mapNotNull { service ->
        runCatching {
            DiscoveredBleService(
                uuid = UUID.fromString(service.uuid),
                writableCharacteristicUuids = service.characteristics
                    .filter { characteristic ->
                        characteristic.properties.any { it == "WRITE" || it == "WRITE_NO_RESPONSE" }
                    }
                    .map { UUID.fromString(it.uuid) }
                    .toSet(),
            )
        }.getOrNull()
    }

    fun asShareableText(): String = buildString {
        val adapter = matchedAdapter
        appendLine("Adaptive Remote — read-only BLE diagnostic")
        appendLine("Device: $deviceName")
        appendLine("Address: $deviceAddress")
        if (adapter == null) {
            appendLine("Supported protocol match: NONE")
        } else {
            appendLine("Supported protocol match: ${adapter.displayName}")
            appendLine("Support status: ${adapter.supportStatus.name}")
            appendLine("Capabilities: ${adapter.capabilities.map { it.name }.sorted().joinToString()}")
        }
        appendLine()
        services.forEach { service ->
            appendLine("Service ${service.uuid}")
            service.characteristics.forEach { characteristic ->
                appendLine("  ${characteristic.uuid} [${characteristic.properties.sorted().joinToString()}]")
            }
        }
        appendLine()
        append("No characteristic values were written during this diagnostic.")
    }

}

sealed interface BleDiagnosticStatus {
    data object Idle : BleDiagnosticStatus
    data object Scanning : BleDiagnosticStatus
    data class DevicesFound(val devices: List<BleDeviceCandidate>) : BleDiagnosticStatus
    data class Inspecting(val device: BleDeviceCandidate) : BleDiagnosticStatus
    data class Complete(val report: DeviceDiagnosticReport) : BleDiagnosticStatus
    data class TestingCommand(val report: DeviceDiagnosticReport) : BleDiagnosticStatus
    data class CommandAccepted(val report: DeviceDiagnosticReport) : BleDiagnosticStatus
    data class Error(val message: String) : BleDiagnosticStatus
}

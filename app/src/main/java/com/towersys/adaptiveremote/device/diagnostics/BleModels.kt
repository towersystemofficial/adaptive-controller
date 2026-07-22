package com.towersys.adaptiveremote.device.diagnostics

import com.towersys.adaptiveremote.device.protocol.JoyHubProtocolAdapter

data class BleDeviceCandidate(val name: String, val address: String, val rssi: Int) {
    val isLikelyKnight: Boolean get() = name.equals("J-Mars", ignoreCase = true)
}

data class BleCharacteristicReport(val uuid: String, val properties: Set<String>)

data class BleServiceReport(
    val uuid: String,
    val characteristics: List<BleCharacteristicReport>,
)

data class KnightDiagnosticReport(
    val deviceName: String,
    val deviceAddress: String,
    val services: List<BleServiceReport>,
) {
    val hasExpectedJoyHubTransport: Boolean
        get() = services.any { service ->
            service.uuid.equals(JOYHUB_SERVICE_UUID, ignoreCase = true) &&
                service.characteristics.any { characteristic ->
                    characteristic.uuid.equals(JOYHUB_WRITE_UUID, ignoreCase = true) &&
                        characteristic.properties.any { it == "WRITE" || it == "WRITE_NO_RESPONSE" }
                }
        }

    fun asShareableText(): String = buildString {
        appendLine("Adaptive Remote — read-only BLE diagnostic")
        appendLine("Device: $deviceName")
        appendLine("Address: $deviceAddress")
        appendLine("Expected JoyHub FFA0/FFA1 transport: ${if (hasExpectedJoyHubTransport) "FOUND" else "NOT FOUND"}")
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

    companion object {
        const val JOYHUB_SERVICE_UUID = JoyHubProtocolAdapter.SERVICE_UUID
        const val JOYHUB_WRITE_UUID = JoyHubProtocolAdapter.WRITE_CHARACTERISTIC_UUID
    }
}

sealed interface BleDiagnosticStatus {
    data object Idle : BleDiagnosticStatus
    data object Scanning : BleDiagnosticStatus
    data class DevicesFound(val devices: List<BleDeviceCandidate>) : BleDiagnosticStatus
    data class Inspecting(val device: BleDeviceCandidate) : BleDiagnosticStatus
    data class Complete(val report: KnightDiagnosticReport) : BleDiagnosticStatus
    data class TestingCommand(val report: KnightDiagnosticReport) : BleDiagnosticStatus
    data class CommandAccepted(val report: KnightDiagnosticReport) : BleDiagnosticStatus
    data class Error(val message: String) : BleDiagnosticStatus
}

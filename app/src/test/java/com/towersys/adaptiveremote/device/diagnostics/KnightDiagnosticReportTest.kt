package com.towersys.adaptiveremote.device.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnightDiagnosticReportTest {
    @Test
    fun recognizesExpectedJoyHubWriteTransport() {
        val report = reportWith(
            KnightDiagnosticReport.JOYHUB_SERVICE_UUID,
            KnightDiagnosticReport.JOYHUB_WRITE_UUID,
            setOf("WRITE_NO_RESPONSE"),
        )
        assertTrue(report.hasExpectedJoyHubTransport)
        assertTrue(report.asShareableText().contains("FOUND"))
        assertTrue(report.asShareableText().contains("No characteristic values were written"))
    }

    @Test
    fun rejectsReadOnlyCharacteristic() {
        val report = reportWith(
            KnightDiagnosticReport.JOYHUB_SERVICE_UUID,
            KnightDiagnosticReport.JOYHUB_WRITE_UUID,
            setOf("READ"),
        )
        assertFalse(report.hasExpectedJoyHubTransport)
    }

    private fun reportWith(
        serviceUuid: String,
        characteristicUuid: String,
        properties: Set<String>,
    ) = KnightDiagnosticReport(
        deviceName = "J-Mars",
        deviceAddress = "00:00:00:00:00:00",
        services = listOf(
            BleServiceReport(
                uuid = serviceUuid,
                characteristics = listOf(BleCharacteristicReport(characteristicUuid, properties)),
            ),
        ),
    )
}

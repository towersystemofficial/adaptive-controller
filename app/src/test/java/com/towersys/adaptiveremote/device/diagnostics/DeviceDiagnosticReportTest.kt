package com.towersys.adaptiveremote.device.diagnostics

import com.towersys.adaptiveremote.device.protocol.AdapterSupportStatus
import com.towersys.adaptiveremote.device.protocol.DeviceCapability
import com.towersys.adaptiveremote.device.protocol.JoyHubProtocolAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDiagnosticReportTest {
    @Test
    fun recognizesExpectedJoyHubWriteTransport() {
        val report = reportWith(
            JoyHubProtocolAdapter.SERVICE_UUID,
            JoyHubProtocolAdapter.WRITE_CHARACTERISTIC_UUID,
            setOf("WRITE_NO_RESPONSE"),
        )
        assertEquals(JoyHubProtocolAdapter, report.matchedAdapter)
        assertEquals(AdapterSupportStatus.VERIFIED, report.matchedAdapter?.supportStatus)
        assertEquals(DeviceCapability.OSCILLATION, report.probeCapability)
        assertTrue(report.asShareableText().contains(JoyHubProtocolAdapter.displayName))
        assertTrue(report.asShareableText().contains("Support status: VERIFIED"))
        assertTrue(report.asShareableText().contains(DeviceCapability.OSCILLATION.name))
        assertTrue(report.asShareableText().contains("No characteristic values were written"))
    }

    @Test
    fun rejectsReadOnlyCharacteristic() {
        val report = reportWith(
            JoyHubProtocolAdapter.SERVICE_UUID,
            JoyHubProtocolAdapter.WRITE_CHARACTERISTIC_UUID,
            setOf("READ"),
        )
        assertNull(report.matchedAdapter)
        assertNull(report.probeCapability)
        assertTrue(report.asShareableText().contains("Supported protocol match: NONE"))
    }

    private fun reportWith(
        serviceUuid: String,
        characteristicUuid: String,
        properties: Set<String>,
    ) = DeviceDiagnosticReport(
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

package com.towersys.adaptiveremote.device.protocol

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Lovense5030ProtocolAdapterTest {
    @Test
    fun declaresExperimentalTransportAndVibrationCapability() {
        assertEquals(AdapterSupportStatus.EXPERIMENTAL, Lovense5030ProtocolAdapter.supportStatus)
        assertEquals(
            "50300001-0024-4bd4-bbd5-a6920e4c5653",
            Lovense5030ProtocolAdapter.serviceUuid.toString(),
        )
        assertEquals(
            "50300002-0024-4bd4-bbd5-a6920e4c5653",
            Lovense5030ProtocolAdapter.writeCharacteristicUuid.toString(),
        )
        assertEquals(9, Lovense5030ProtocolAdapter.transports.size)
        assertEquals(setOf(DeviceCapability.VIBRATION), Lovense5030ProtocolAdapter.capabilities)
    }

    @Test
    fun scalesAdaptiveRemoteLevelsToLovenseRange() {
        assertCommand("Vibrate:0;", Lovense5030ProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 0))
        assertCommand("Vibrate:10;", Lovense5030ProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 128))
        assertCommand("Vibrate:20;", Lovense5030ProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 255))
        assertCommand("Vibrate:0;", Lovense5030ProtocolAdapter.encodeStop())
    }

    @Test
    fun rejectsOutOfRangeValuesAndUnsupportedCapabilities() {
        assertThrows(IllegalArgumentException::class.java) {
            Lovense5030ProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 256)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Lovense5030ProtocolAdapter.encodeScalar(DeviceCapability.OSCILLATION, 128)
        }
    }

    private fun assertCommand(expected: String, actual: ByteArray) {
        assertArrayEquals(expected.toByteArray(StandardCharsets.US_ASCII), actual)
    }
}

package com.towersys.adaptiveremote.device.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JoyHubProtocolAdapterTest {
    @Test
    fun declaresVerifiedTransportAndCapability() {
        assertEquals("0000ffa0-0000-1000-8000-00805f9b34fb", JoyHubProtocolAdapter.serviceUuid.toString())
        assertEquals("0000ffa1-0000-1000-8000-00805f9b34fb", JoyHubProtocolAdapter.writeCharacteristicUuid.toString())
        assertEquals(setOf(DeviceCapability.OSCILLATION), JoyHubProtocolAdapter.capabilities)
    }

    @Test
    fun encodesOscillationWithoutChangingJoyHubBytes() {
        assertArrayEquals(
            JoyHubCommand.oscillate(16),
            JoyHubProtocolAdapter.encodeScalar(DeviceCapability.OSCILLATION, 16),
        )
        assertArrayEquals(JoyHubCommand.stop(), JoyHubProtocolAdapter.encodeStop())
    }

    @Test
    fun rejectsCapabilitiesTheProtocolDoesNotSupport() {
        assertThrows(IllegalArgumentException::class.java) {
            JoyHubProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 16)
        }
    }
}

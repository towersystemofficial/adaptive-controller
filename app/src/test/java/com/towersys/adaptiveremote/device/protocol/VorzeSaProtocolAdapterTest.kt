package com.towersys.adaptiveremote.device.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VorzeSaProtocolAdapterTest {
    @Test
    fun declaresExperimentalNameRestrictedVibrationAdapters() {
        assertEquals(AdapterSupportStatus.EXPERIMENTAL, VorzeBachProtocolAdapter.supportStatus)
        assertEquals(setOf("Bach smart"), VorzeBachProtocolAdapter.advertisedNames)
        assertEquals(setOf(DeviceCapability.VIBRATION), VorzeBachProtocolAdapter.capabilities)

        assertEquals(AdapterSupportStatus.EXPERIMENTAL, AdultFestaRocketProtocolAdapter.supportStatus)
        assertEquals(setOf("ROCKET"), AdultFestaRocketProtocolAdapter.advertisedNames)
        assertEquals(setOf(DeviceCapability.VIBRATION), AdultFestaRocketProtocolAdapter.capabilities)
    }

    @Test
    fun encodesBachVibrationAndStop() {
        assertArrayEquals(byteArrayOf(6, 3, 0), VorzeBachProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 0))
        assertArrayEquals(byteArrayOf(6, 3, 50), VorzeBachProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 128))
        assertArrayEquals(byteArrayOf(6, 3, 100), VorzeBachProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 255))
        assertArrayEquals(byteArrayOf(6, 3, 0), VorzeBachProtocolAdapter.encodeStop())
    }

    @Test
    fun encodesRocketVibrationAndStop() {
        assertArrayEquals(byteArrayOf(7, 3, 100), AdultFestaRocketProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 255))
        assertArrayEquals(byteArrayOf(7, 3, 0), AdultFestaRocketProtocolAdapter.encodeStop())
    }

    @Test
    fun rejectsOutOfRangeValuesAndUnsupportedCapabilities() {
        assertThrows(IllegalArgumentException::class.java) {
            VorzeBachProtocolAdapter.encodeScalar(DeviceCapability.VIBRATION, 256)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VorzeBachProtocolAdapter.encodeScalar(DeviceCapability.ROTATION, 128)
        }
    }
}

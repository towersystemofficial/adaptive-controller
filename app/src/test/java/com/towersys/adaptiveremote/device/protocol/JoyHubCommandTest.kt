package com.towersys.adaptiveremote.device.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JoyHubCommandTest {
    @Test
    fun formsKnownJoyHubOscillationFrame() {
        assertArrayEquals(
            byteArrayOf(0xA0.toByte(), 0x03, 0x10, 0x00, 0x00, 0x00, 0xAA.toByte()),
            JoyHubCommand.oscillate(16),
        )
    }

    @Test
    fun stopClearsAllFourContinuousChannels() {
        assertArrayEquals(
            byteArrayOf(0xA0.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00, 0xAA.toByte()),
            JoyHubCommand.stop(),
        )
    }

    @Test
    fun rejectsOutOfRangeValue() {
        assertThrows(IllegalArgumentException::class.java) { JoyHubCommand.oscillate(256) }
    }
}

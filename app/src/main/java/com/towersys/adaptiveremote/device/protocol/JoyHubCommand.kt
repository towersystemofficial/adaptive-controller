package com.towersys.adaptiveremote.device.protocol

object JoyHubCommand {
    fun oscillate(value: Int): ByteArray {
        require(value in 0..255) { "Oscillation value must be between 0 and 255" }
        return byteArrayOf(
            0xA0.toByte(),
            0x03,
            value.toByte(),
            0x00,
            0x00,
            0x00,
            0xAA.toByte(),
        )
    }

    fun stop(): ByteArray = oscillate(0)
}

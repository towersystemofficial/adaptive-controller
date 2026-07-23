package com.towersys.adaptiveremote.device.protocol

import java.nio.charset.StandardCharsets

object LovenseCommand {
    fun vibrate(value: Int): ByteArray {
        require(value in 0..255) { "Vibration value must be between 0 and 255" }
        val lovenseLevel = (value * MAX_LEVEL + 127) / 255
        return "Vibrate:$lovenseLevel;".toByteArray(StandardCharsets.US_ASCII)
    }

    fun stop(): ByteArray = "Vibrate:0;".toByteArray(StandardCharsets.US_ASCII)

    private const val MAX_LEVEL = 20
}

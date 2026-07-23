package com.towersys.adaptiveremote.device.protocol

object VorzeSaCommand {
    fun vibrate(device: Int, value: Int): ByteArray {
        require(device in 0..255) { "Device byte must be between 0 and 255" }
        require(value in 0..255) { "Vibration value must be between 0 and 255" }
        val vorzeLevel = (value * MAX_LEVEL + 127) / 255
        return byteArrayOf(device.toByte(), VIBRATE_ACTION, vorzeLevel.toByte())
    }

    private const val MAX_LEVEL = 100
    private const val VIBRATE_ACTION: Byte = 3
}

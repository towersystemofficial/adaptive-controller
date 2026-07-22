package com.towersys.adaptiveremote.device.protocol

import java.util.UUID

enum class DeviceCapability {
    VIBRATION,
    ROTATION,
    OSCILLATION,
    CONSTRICTION,
    INFLATION,
    LINEAR,
    POSITION,
}

interface BleProtocolAdapter {
    val id: String
    val displayName: String
    val serviceUuid: UUID
    val writeCharacteristicUuid: UUID
    val capabilities: Set<DeviceCapability>

    fun encodeScalar(capability: DeviceCapability, value: Int): ByteArray
    fun encodeStop(): ByteArray
}

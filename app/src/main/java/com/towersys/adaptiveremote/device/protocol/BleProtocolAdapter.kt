package com.towersys.adaptiveremote.device.protocol

import java.util.UUID

enum class AdapterSupportStatus {
    VERIFIED,
    EXPERIMENTAL,
}

enum class DeviceCapability {
    VIBRATION,
    ROTATION,
    OSCILLATION,
    CONSTRICTION,
    INFLATION,
    LINEAR,
    POSITION,
}

data class BleProtocolTransport(
    val serviceUuid: UUID,
    val writeCharacteristicUuid: UUID,
)

interface BleProtocolAdapter {
    val id: String
    val displayName: String
    val supportStatus: AdapterSupportStatus
    val serviceUuid: UUID
    val writeCharacteristicUuid: UUID
    val additionalTransports: Set<BleProtocolTransport>
        get() = emptySet()
    val transports: Set<BleProtocolTransport>
        get() = additionalTransports + BleProtocolTransport(serviceUuid, writeCharacteristicUuid)
    val capabilities: Set<DeviceCapability>

    fun encodeScalar(capability: DeviceCapability, value: Int): ByteArray
    fun encodeStop(): ByteArray
}

package com.towersys.adaptiveremote.device.protocol

import java.util.UUID

object Lovense5030ProtocolAdapter : BleProtocolAdapter {
    override val id = "lovense-5030-0024"
    override val displayName = "Lovense 5030/0024"
    override val supportStatus = AdapterSupportStatus.EXPERIMENTAL
    override val serviceUuid: UUID = UUID.fromString(SERVICE_UUID)
    override val writeCharacteristicUuid: UUID = UUID.fromString(WRITE_CHARACTERISTIC_UUID)
    override val capabilities = setOf(DeviceCapability.VIBRATION)

    override fun encodeScalar(capability: DeviceCapability, value: Int): ByteArray {
        require(capability in capabilities) { "$displayName does not support $capability" }
        return LovenseCommand.vibrate(value)
    }

    override fun encodeStop(): ByteArray = LovenseCommand.stop()

    const val SERVICE_UUID = "50300001-0024-4bd4-bbd5-a6920e4c5653"
    const val WRITE_CHARACTERISTIC_UUID = "50300002-0024-4bd4-bbd5-a6920e4c5653"
}

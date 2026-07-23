package com.towersys.adaptiveremote.device.protocol

import java.util.UUID

object Lovense5030ProtocolAdapter : BleProtocolAdapter {
    override val id = "lovense-5030-0024"
    override val displayName = "Lovense 5030/0024"
    override val supportStatus = AdapterSupportStatus.EXPERIMENTAL
    override val serviceUuid: UUID = UUID.fromString(SERVICE_UUID)
    override val writeCharacteristicUuid: UUID = UUID.fromString(WRITE_CHARACTERISTIC_UUID)
    override val additionalTransports = setOf(
        transport("57300001-0023-4bd4-bbd5-a6920e4c5653", "57300002-0023-4bd4-bbd5-a6920e4c5653"),
        transport("5a300001-0024-4bd4-bbd5-a6920e4c5653", "5a300002-0024-4bd4-bbd5-a6920e4c5653"),
        transport("50300001-0023-4bd4-bbd5-a6920e4c5653", "50300002-0023-4bd4-bbd5-a6920e4c5653"),
        transport("53300001-0023-4bd4-bbd5-a6920e4c5653", "53300002-0023-4bd4-bbd5-a6920e4c5653"),
        transport("5a300001-0023-4bd4-bbd5-a6920e4c5653", "5a300002-0023-4bd4-bbd5-a6920e4c5653"),
        transport("4f300001-0023-4bd4-bbd5-a6920e4c5653", "4f300002-0023-4bd4-bbd5-a6920e4c5653"),
        transport("42300001-0023-4bd4-bbd5-a6920e4c5653", "42300002-0023-4bd4-bbd5-a6920e4c5653"),
        transport("43300001-0023-4bd4-bbd5-a6920e4c5653", "43300002-0023-4bd4-bbd5-a6920e4c5653"),
    )
    override val capabilities = setOf(DeviceCapability.VIBRATION)

    override fun encodeScalar(capability: DeviceCapability, value: Int): ByteArray {
        require(capability in capabilities) { "$displayName does not support $capability" }
        return LovenseCommand.vibrate(value)
    }

    override fun encodeStop(): ByteArray = LovenseCommand.stop()

    private fun transport(serviceUuid: String, writeCharacteristicUuid: String) =
        BleProtocolTransport(UUID.fromString(serviceUuid), UUID.fromString(writeCharacteristicUuid))

    const val SERVICE_UUID = "50300001-0024-4bd4-bbd5-a6920e4c5653"
    const val WRITE_CHARACTERISTIC_UUID = "50300002-0024-4bd4-bbd5-a6920e4c5653"
}

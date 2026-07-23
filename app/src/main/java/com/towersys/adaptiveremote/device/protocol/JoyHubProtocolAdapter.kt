package com.towersys.adaptiveremote.device.protocol

import java.util.UUID

object JoyHubProtocolAdapter : BleProtocolAdapter {
    override val id = "joyhub-ffa0"
    override val displayName = "JoyHub FFA0/FFA1"
    override val supportStatus = AdapterSupportStatus.VERIFIED
    override val serviceUuid: UUID = UUID.fromString(SERVICE_UUID)
    override val writeCharacteristicUuid: UUID = UUID.fromString(WRITE_CHARACTERISTIC_UUID)
    override val capabilities = setOf(DeviceCapability.OSCILLATION)

    override fun encodeScalar(capability: DeviceCapability, value: Int): ByteArray {
        require(capability in capabilities) { "$displayName does not support $capability" }
        return JoyHubCommand.oscillate(value)
    }

    override fun encodeStop(): ByteArray = JoyHubCommand.stop()

    const val SERVICE_UUID = "0000ffa0-0000-1000-8000-00805f9b34fb"
    const val WRITE_CHARACTERISTIC_UUID = "0000ffa1-0000-1000-8000-00805f9b34fb"
}

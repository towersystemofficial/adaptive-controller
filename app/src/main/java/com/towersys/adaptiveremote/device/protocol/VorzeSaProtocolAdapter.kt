package com.towersys.adaptiveremote.device.protocol

import java.util.UUID

private const val VORZE_SA_SERVICE_UUID = "40ee1111-63ec-4b7f-8ce7-712efd55b90e"
private const val VORZE_SA_WRITE_UUID = "40ee2222-63ec-4b7f-8ce7-712efd55b90e"

object VorzeBachProtocolAdapter : BleProtocolAdapter {
    override val id = "vorze-sa-bach"
    override val displayName = "Vorze Bach"
    override val supportStatus = AdapterSupportStatus.EXPERIMENTAL
    override val advertisedNames = setOf("Bach smart")
    override val serviceUuid: UUID = UUID.fromString(VORZE_SA_SERVICE_UUID)
    override val writeCharacteristicUuid: UUID = UUID.fromString(VORZE_SA_WRITE_UUID)
    override val capabilities = setOf(DeviceCapability.VIBRATION)

    override fun encodeScalar(capability: DeviceCapability, value: Int): ByteArray {
        require(capability in capabilities) { "$displayName does not support $capability" }
        return VorzeSaCommand.vibrate(BACH_DEVICE, value)
    }

    override fun encodeStop(): ByteArray = VorzeSaCommand.vibrate(BACH_DEVICE, 0)

    private const val BACH_DEVICE = 6
}

object AdultFestaRocketProtocolAdapter : BleProtocolAdapter {
    override val id = "vorze-sa-rocket"
    override val displayName = "Adult Festa Rocket"
    override val supportStatus = AdapterSupportStatus.EXPERIMENTAL
    override val advertisedNames = setOf("ROCKET")
    override val serviceUuid: UUID = UUID.fromString(VORZE_SA_SERVICE_UUID)
    override val writeCharacteristicUuid: UUID = UUID.fromString(VORZE_SA_WRITE_UUID)
    override val capabilities = setOf(DeviceCapability.VIBRATION)

    override fun encodeScalar(capability: DeviceCapability, value: Int): ByteArray {
        require(capability in capabilities) { "$displayName does not support $capability" }
        return VorzeSaCommand.vibrate(ROCKET_DEVICE, value)
    }

    override fun encodeStop(): ByteArray = VorzeSaCommand.vibrate(ROCKET_DEVICE, 0)

    private const val ROCKET_DEVICE = 7
}

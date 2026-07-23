package com.towersys.adaptiveremote.device.protocol

import java.util.UUID

data class DiscoveredBleService(
    val uuid: UUID,
    val writableCharacteristicUuids: Set<UUID>,
)

object DeviceProtocolRegistry {
    private val adapters: List<BleProtocolAdapter> = listOf(
        JoyHubProtocolAdapter,
        Lovense5030ProtocolAdapter,
    )

    fun findById(id: String): BleProtocolAdapter? = adapters.firstOrNull { it.id == id }

    fun match(services: Collection<DiscoveredBleService>): BleProtocolAdapter? =
        adapters.firstOrNull { adapter ->
            adapter.transports.any { transport ->
                services.any { service ->
                    service.uuid == transport.serviceUuid &&
                        transport.writeCharacteristicUuid in service.writableCharacteristicUuids
                }
            }
        }
}

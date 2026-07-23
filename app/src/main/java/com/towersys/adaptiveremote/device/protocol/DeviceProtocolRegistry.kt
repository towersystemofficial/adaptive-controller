package com.towersys.adaptiveremote.device.protocol

import java.util.UUID

data class DiscoveredBleService(
    val uuid: UUID,
    val writableCharacteristicUuids: Set<UUID>,
)

object DeviceProtocolRegistry {
    private val adapters: List<BleProtocolAdapter> = listOf(JoyHubProtocolAdapter)

    fun findById(id: String): BleProtocolAdapter? = adapters.firstOrNull { it.id == id }

    fun match(services: Collection<DiscoveredBleService>): BleProtocolAdapter? =
        adapters.firstOrNull { adapter ->
            services.any { service ->
                service.uuid == adapter.serviceUuid &&
                    adapter.writeCharacteristicUuid in service.writableCharacteristicUuids
            }
        }
}

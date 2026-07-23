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
        VorzeBachProtocolAdapter,
        AdultFestaRocketProtocolAdapter,
    )

    fun findById(id: String): BleProtocolAdapter? = adapters.firstOrNull { it.id == id }

    fun match(
        services: Collection<DiscoveredBleService>,
        advertisedName: String? = null,
    ): BleProtocolAdapter? =
        adapters.firstOrNull { adapter ->
            adapter.matches(advertisedName, services)
        }
}

internal fun BleProtocolAdapter.matches(
    advertisedName: String?,
    services: Collection<DiscoveredBleService>,
): Boolean {
    val requiresName = advertisedNames.isNotEmpty() || advertisedNamePrefixes.isNotEmpty()
    val nameMatches = !requiresName || (
        advertisedName != null && (
            advertisedName in advertisedNames ||
                advertisedNamePrefixes.any(advertisedName::startsWith)
            )
        )

    return nameMatches && transports.any { transport ->
        services.any { service ->
            service.uuid == transport.serviceUuid &&
                transport.writeCharacteristicUuid in service.writableCharacteristicUuids
        }
    }
}

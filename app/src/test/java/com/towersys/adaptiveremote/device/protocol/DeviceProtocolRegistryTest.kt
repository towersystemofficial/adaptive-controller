package com.towersys.adaptiveremote.device.protocol

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceProtocolRegistryTest {
    @Test
    fun matchesJoyHubFromDocumentedWritableTransport() {
        val match = DeviceProtocolRegistry.match(
            listOf(
                DiscoveredBleService(
                    uuid = JoyHubProtocolAdapter.serviceUuid,
                    writableCharacteristicUuids = setOf(JoyHubProtocolAdapter.writeCharacteristicUuid),
                ),
            ),
        )

        assertEquals(JoyHubProtocolAdapter, match)
    }

    @Test
    fun matchesLovense5030FromDocumentedWritableTransport() {
        val match = DeviceProtocolRegistry.match(
            listOf(
                DiscoveredBleService(
                    uuid = Lovense5030ProtocolAdapter.serviceUuid,
                    writableCharacteristicUuids = setOf(
                        Lovense5030ProtocolAdapter.writeCharacteristicUuid,
                    ),
                ),
            ),
        )

        assertEquals(Lovense5030ProtocolAdapter, match)
    }

    @Test
    fun matchesAlternateLovenseTransport() {
        val alternate = Lovense5030ProtocolAdapter.additionalTransports.first()
        val match = DeviceProtocolRegistry.match(
            listOf(
                DiscoveredBleService(
                    uuid = alternate.serviceUuid,
                    writableCharacteristicUuids = setOf(alternate.writeCharacteristicUuid),
                ),
            ),
        )

        assertEquals(Lovense5030ProtocolAdapter, match)
    }

    @Test
    fun doesNotMatchAlternateLovenseTransportWhenWriteCharacteristicIsMissing() {
        val alternate = Lovense5030ProtocolAdapter.additionalTransports.first()
        val match = DeviceProtocolRegistry.match(
            listOf(
                DiscoveredBleService(
                    uuid = alternate.serviceUuid,
                    writableCharacteristicUuids = emptySet(),
                ),
            ),
        )

        assertNull(match)
    }

    @Test
    fun doesNotMatchLovense5030WhenWriteCharacteristicIsMissing() {
        val match = DeviceProtocolRegistry.match(
            listOf(
                DiscoveredBleService(
                    uuid = Lovense5030ProtocolAdapter.serviceUuid,
                    writableCharacteristicUuids = emptySet(),
                ),
            ),
        )

        assertNull(match)
    }

    @Test
    fun doesNotMatchWhenWriteCharacteristicIsMissing() {
        val match = DeviceProtocolRegistry.match(
            listOf(
                DiscoveredBleService(
                    uuid = JoyHubProtocolAdapter.serviceUuid,
                    writableCharacteristicUuids = emptySet(),
                ),
            ),
        )

        assertNull(match)
    }

    @Test
    fun doesNotMatchUnknownTransport() {
        val match = DeviceProtocolRegistry.match(
            listOf(
                DiscoveredBleService(
                    uuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"),
                    writableCharacteristicUuids = setOf(
                        UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"),
                    ),
                ),
            ),
        )

        assertNull(match)
    }

    @Test
    fun resolvesPersistedAdapterId() {
        assertEquals(
            JoyHubProtocolAdapter,
            DeviceProtocolRegistry.findById(JoyHubProtocolAdapter.id),
        )
        assertEquals(
            Lovense5030ProtocolAdapter,
            DeviceProtocolRegistry.findById(Lovense5030ProtocolAdapter.id),
        )
        assertNull(DeviceProtocolRegistry.findById("unknown"))
    }
}

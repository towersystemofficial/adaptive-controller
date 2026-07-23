package com.towersys.adaptiveremote.device.protocol

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceProtocolRegistryTest {
    private val documentedTransport = BleProtocolTransport(
        serviceUuid = UUID.fromString("12345678-0000-1000-8000-00805f9b34fb"),
        writeCharacteristicUuid = UUID.fromString("12345679-0000-1000-8000-00805f9b34fb"),
    )

    private fun nameRestrictedAdapter(
        exactNames: Set<String> = emptySet(),
        prefixes: Set<String> = emptySet(),
    ) = object : BleProtocolAdapter {
        override val id = "test"
        override val displayName = "Test"
        override val supportStatus = AdapterSupportStatus.EXPERIMENTAL
        override val advertisedNames = exactNames
        override val advertisedNamePrefixes = prefixes
        override val serviceUuid = documentedTransport.serviceUuid
        override val writeCharacteristicUuid = documentedTransport.writeCharacteristicUuid
        override val capabilities = setOf(DeviceCapability.VIBRATION)

        override fun encodeScalar(capability: DeviceCapability, value: Int) = byteArrayOf()
        override fun encodeStop() = byteArrayOf()
    }

    private val documentedServices = listOf(
        DiscoveredBleService(
            uuid = documentedTransport.serviceUuid,
            writableCharacteristicUuids = setOf(documentedTransport.writeCharacteristicUuid),
        ),
    )

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
        assertEquals(
            VorzeBachProtocolAdapter,
            DeviceProtocolRegistry.findById(VorzeBachProtocolAdapter.id),
        )
        assertEquals(
            AdultFestaRocketProtocolAdapter,
            DeviceProtocolRegistry.findById(AdultFestaRocketProtocolAdapter.id),
        )
        assertNull(DeviceProtocolRegistry.findById("unknown"))
    }

    @Test
    fun matchesVorzeVariantsOnlyWithExactNameAndTransport() {
        val services = listOf(
            DiscoveredBleService(
                uuid = VorzeBachProtocolAdapter.serviceUuid,
                writableCharacteristicUuids = setOf(VorzeBachProtocolAdapter.writeCharacteristicUuid),
            ),
        )

        assertEquals(
            VorzeBachProtocolAdapter,
            DeviceProtocolRegistry.match(services, "Bach smart"),
        )
        assertEquals(
            AdultFestaRocketProtocolAdapter,
            DeviceProtocolRegistry.match(services, "ROCKET"),
        )
        assertNull(DeviceProtocolRegistry.match(services))
        assertNull(DeviceProtocolRegistry.match(services, "CycSA"))
    }

    @Test
    fun rejectsVorzeNameWhenWriteCharacteristicIsMissing() {
        val services = listOf(
            DiscoveredBleService(
                uuid = VorzeBachProtocolAdapter.serviceUuid,
                writableCharacteristicUuids = emptySet(),
            ),
        )

        assertNull(DeviceProtocolRegistry.match(services, "Bach smart"))
    }

    @Test
    fun matchesDocumentedExactAdvertisedName() {
        val adapter = nameRestrictedAdapter(exactNames = setOf("Bach smart"))

        assertEquals(true, adapter.matches("Bach smart", documentedServices))
    }

    @Test
    fun matchesDocumentedAdvertisedNamePrefix() {
        val adapter = nameRestrictedAdapter(prefixes = setOf("UFO-"))

        assertEquals(true, adapter.matches("UFO-TW", documentedServices))
    }

    @Test
    fun rejectsMissingAdvertisedNameWhenNameEvidenceIsRequired() {
        val adapter = nameRestrictedAdapter(exactNames = setOf("Bach smart"))

        assertEquals(false, adapter.matches(null, documentedServices))
    }

    @Test
    fun rejectsIncorrectAdvertisedNameWhenNameEvidenceIsRequired() {
        val adapter = nameRestrictedAdapter(exactNames = setOf("Bach smart"))

        assertEquals(false, adapter.matches("CycSA", documentedServices))
    }

    @Test
    fun preservesTransportOnlyMatchingWhenNoNameEvidenceIsRequired() {
        val adapter = nameRestrictedAdapter()

        assertEquals(true, adapter.matches(null, documentedServices))
    }
}

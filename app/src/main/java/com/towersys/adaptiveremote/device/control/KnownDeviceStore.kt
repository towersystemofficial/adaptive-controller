package com.towersys.adaptiveremote.device.control

import android.content.Context
import com.towersys.adaptiveremote.device.protocol.JoyHubProtocolAdapter

class KnownDeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences("known_knight", Context.MODE_PRIVATE)

    fun load(): KnownDevice? {
        val name = preferences.getString(KEY_NAME, null) ?: return null
        val address = preferences.getString(KEY_ADDRESS, null) ?: return null
        val protocolId = preferences.getString(KEY_PROTOCOL_ID, null) ?: JoyHubProtocolAdapter.id
        return KnownDevice(name, address, protocolId)
    }

    fun save(device: KnownDevice) {
        preferences.edit()
            .putString(KEY_NAME, device.name)
            .putString(KEY_ADDRESS, device.address)
            .putString(KEY_PROTOCOL_ID, device.protocolId)
            .apply()
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_ADDRESS = "address"
        private const val KEY_PROTOCOL_ID = "protocol_id"
    }
}

package com.towersys.adaptiveremote.device.control

import android.content.Context

class KnownDeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences("known_knight", Context.MODE_PRIVATE)

    fun load(): KnownDevice? {
        val name = preferences.getString(KEY_NAME, null) ?: return null
        val address = preferences.getString(KEY_ADDRESS, null) ?: return null
        return KnownDevice(name, address)
    }

    fun save(device: KnownDevice) {
        preferences.edit()
            .putString(KEY_NAME, device.name)
            .putString(KEY_ADDRESS, device.address)
            .apply()
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_ADDRESS = "address"
    }
}

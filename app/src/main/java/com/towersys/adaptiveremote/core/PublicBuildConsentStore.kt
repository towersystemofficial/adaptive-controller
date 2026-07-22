package com.towersys.adaptiveremote.core

import android.content.Context

class PublicBuildConsentStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun hasAcceptedCurrentNotice(): Boolean =
        preferences.getInt(KEY_NOTICE_VERSION, 0) >= CURRENT_NOTICE_VERSION

    fun acceptCurrentNotice() {
        preferences.edit().putInt(KEY_NOTICE_VERSION, CURRENT_NOTICE_VERSION).apply()
    }

    companion object {
        private const val PREFERENCES = "public_build_consent"
        private const val KEY_NOTICE_VERSION = "accepted_notice_version"
        private const val CURRENT_NOTICE_VERSION = 1
    }
}

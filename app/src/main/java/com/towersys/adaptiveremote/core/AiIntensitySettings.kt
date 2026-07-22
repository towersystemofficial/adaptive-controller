package com.towersys.adaptiveremote.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AiIntensitySettings {
    private const val PREFS = "adaptive_remote_settings"
    private const val KEY_MULTIPLIER = "ai_intensity_multiplier"
    private val _multiplier = MutableStateFlow(AdaptiveRemoteState.DEFAULT_ADAPTIVE_MULTIPLIER)
    val multiplier: StateFlow<Float> = _multiplier.asStateFlow()
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        _multiplier.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_MULTIPLIER, AdaptiveRemoteState.DEFAULT_ADAPTIVE_MULTIPLIER)
            .coerceIn(0f, 1.5f)
        initialized = true
    }

    fun set(context: Context, value: Float) {
        initialize(context)
        val normalized = value.coerceIn(0f, 1.5f)
        _multiplier.value = normalized
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_MULTIPLIER, normalized)
            .apply()
    }
}

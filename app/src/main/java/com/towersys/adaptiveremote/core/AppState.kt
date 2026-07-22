package com.towersys.adaptiveremote.core

enum class DeviceConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    DISCOVERING,
    READY,
    RECONNECTING,
    ERROR,
}

enum class ControlMode(val displayName: String) {
    MANUAL("Manual"),
    PATTERNS("Patterns"),
    TEXT("Text"),
    VIDEO("Video"),
    AUTONOMOUS("Autonomous"),
}

data class AdaptiveRemoteState(
    val connectionState: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val controlMode: ControlMode = ControlMode.MANUAL,
    val adaptiveMultiplier: Float = DEFAULT_ADAPTIVE_MULTIPLIER,
    val isSessionActive: Boolean = false,
    val isPaused: Boolean = false,
) {
    companion object {
        const val DEFAULT_ADAPTIVE_MULTIPLIER = 0.5f
    }
}


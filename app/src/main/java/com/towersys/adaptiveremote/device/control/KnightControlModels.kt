package com.towersys.adaptiveremote.device.control

data class KnownKnight(val name: String, val address: String)

sealed interface KnightConnectionStatus {
    data object Disconnected : KnightConnectionStatus
    data class Connecting(val device: KnownKnight) : KnightConnectionStatus
    data class Ready(val device: KnownKnight) : KnightConnectionStatus
    data class Error(val message: String) : KnightConnectionStatus
}

object KnightControlState {
    val knownDevice = kotlinx.coroutines.flow.MutableStateFlow<KnownKnight?>(null)
    val connection = kotlinx.coroutines.flow.MutableStateFlow<KnightConnectionStatus>(
        KnightConnectionStatus.Disconnected,
    )
    val outputLevel = kotlinx.coroutines.flow.MutableStateFlow(0)
    val patternPlayback = kotlinx.coroutines.flow.MutableStateFlow<PatternPlaybackState>(
        PatternPlaybackState.Idle,
    )
}

sealed interface TextMonitorStatus {
    data object Idle : TextMonitorStatus
    data object WaitingForText : TextMonitorStatus
    data class Analyzing(val sourceApp: String) : TextMonitorStatus
    data class Playing(val summary: String) : TextMonitorStatus
    data class Error(val message: String) : TextMonitorStatus
}

object TextMonitorState {
    val status = kotlinx.coroutines.flow.MutableStateFlow<TextMonitorStatus>(TextMonitorStatus.Idle)
}

sealed interface VideoMonitorStatus {
    data object Idle : VideoMonitorStatus
    data object Capturing : VideoMonitorStatus
    data object Analyzing : VideoMonitorStatus
    data class Playing(val summary: String) : VideoMonitorStatus
    data class Error(val message: String) : VideoMonitorStatus
}

object VideoMonitorState {
    val status = kotlinx.coroutines.flow.MutableStateFlow<VideoMonitorStatus>(VideoMonitorStatus.Idle)
}

sealed interface ProceduralMonitorStatus {
    data object Idle : ProceduralMonitorStatus
    data object Starting : ProceduralMonitorStatus
    data class Running(val style: String, val batch: Int, val detail: String) : ProceduralMonitorStatus
    data class Special(val detail: String) : ProceduralMonitorStatus
    data class Error(val message: String) : ProceduralMonitorStatus
}

object ProceduralMonitorState {
    val status = kotlinx.coroutines.flow.MutableStateFlow<ProceduralMonitorStatus>(ProceduralMonitorStatus.Idle)
}

sealed interface PatternPlaybackState {
    data object Idle : PatternPlaybackState
    data class Playing(val name: String, val step: Int, val totalSteps: Int) : PatternPlaybackState
}

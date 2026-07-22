package com.towersys.adaptiveremote.text

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.towersys.adaptiveremote.device.control.KnightControlService
import com.towersys.adaptiveremote.device.control.KnightControlState
import com.towersys.adaptiveremote.device.control.TextMonitorState
import com.towersys.adaptiveremote.core.AiIntensitySettings
import com.towersys.adaptiveremote.patterns.KnightPattern
import com.towersys.adaptiveremote.video.VideoAnalysisService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

sealed interface TextAnalysisState {
    data object Idle : TextAnalysisState
    data object Analyzing : TextAnalysisState
    data class Ready(val interpretation: TextInterpretation) : TextAnalysisState
    data class Error(val message: String) : TextAnalysisState
}

class TextModeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val secrets = SecretStore(app)
    private val client = GrokTextClient()
    val capturedText = VisibleTextState.latestText
    val sourceApp = VisibleTextState.sourceApp
    val accessibilityConnected = VisibleTextState.isServiceConnected
    val connection = KnightControlState.connection
    val playback = KnightControlState.patternPlayback
    val monitorStatus = TextMonitorState.status
    private val _analysis = MutableStateFlow<TextAnalysisState>(TextAnalysisState.Idle)
    val analysis = _analysis.asStateFlow()
    private val _hasApiKey = MutableStateFlow(secrets.loadApiKey().isNotBlank())
    val hasApiKey = _hasApiKey.asStateFlow()

    fun saveApiKey(key: String) {
        secrets.saveApiKey(key.trim())
        _hasApiKey.value = key.isNotBlank()
    }

    fun analyze(passage: String) {
        val key = secrets.loadApiKey()
        if (key.isBlank()) {
            _analysis.value = TextAnalysisState.Error("Save an xAI API key first.")
            return
        }
        if (passage.isBlank()) return
        viewModelScope.launch {
            _analysis.value = TextAnalysisState.Analyzing
            _analysis.value = runCatching {
                withContext(Dispatchers.IO) { client.interpret(key, passage) }
            }.fold(
                onSuccess = { TextAnalysisState.Ready(it) },
                onFailure = { TextAnalysisState.Error(it.message ?: "Text analysis failed.") },
            )
        }
    }

    fun play(pattern: KnightPattern) {
        AiIntensitySettings.initialize(app)
        val multiplier = AiIntensitySettings.multiplier.value
        val levels = pattern.steps.map {
            (it.intensity * multiplier.coerceIn(0f, 1.5f) * 255f / 100f)
                .roundToInt().coerceIn(0, 255)
        }.toIntArray()
        app.startService(
            Intent(app, KnightControlService::class.java)
                .setAction(KnightControlService.ACTION_PLAY_PATTERN)
                .putExtra(KnightControlService.EXTRA_PATTERN_NAME, pattern.name)
                .putExtra(KnightControlService.EXTRA_PATTERN_LEVELS, levels)
                .putExtra(KnightControlService.EXTRA_PATTERN_DURATIONS, pattern.steps.map { it.durationMs }.toLongArray())
                .putExtra(KnightControlService.EXTRA_PATTERN_REPEATS, 1),
        )
    }

    fun stop() {
        app.startService(Intent(app, KnightControlService::class.java).setAction(KnightControlService.ACTION_STOP))
    }

    fun startContinuous() {
        viewModelScope.launch {
            app.startService(Intent(app, VideoAnalysisService::class.java).setAction(VideoAnalysisService.ACTION_STOP))
            delay(300L)
            app.startService(
                Intent(app, KnightControlService::class.java)
                    .setAction(KnightControlService.ACTION_START_TEXT_MONITOR),
            )
        }
    }

    fun stopContinuous() {
        app.startService(
            Intent(app, KnightControlService::class.java).setAction(KnightControlService.ACTION_STOP_TEXT_MONITOR),
        )
    }
}

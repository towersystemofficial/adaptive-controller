package com.towersys.adaptiveremote.patterns

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.towersys.adaptiveremote.device.control.KnightControlService
import com.towersys.adaptiveremote.device.control.KnightControlState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PatternViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val store = PatternStore(app)
    private val _saved = MutableStateFlow(store.load())
    val saved = _saved.asStateFlow()
    private val _history = MutableStateFlow(store.loadHistory())
    val history = _history.asStateFlow()
    val connection = KnightControlState.connection
    val playback = KnightControlState.patternPlayback

    fun save(pattern: KnightPattern) {
        val normalized = pattern.copy(
            id = if (pattern.id.startsWith("generated")) {
                "custom-${System.currentTimeMillis()}"
            } else pattern.id,
            isBuiltIn = pattern.isBuiltIn,
        )
        _saved.value = _saved.value.filterNot { it.id == normalized.id } + normalized
        store.save(_saved.value)
    }

    fun delete(id: String) {
        _saved.value = _saved.value.filterNot { it.id == id }
        store.save(_saved.value)
    }

    fun toggleFavorite(pattern: KnightPattern) {
        val existing = _saved.value.firstOrNull { it.id == pattern.id }
        if (existing == null) {
            save(pattern.copy(isFavorite = true))
        } else {
            _saved.value = _saved.value.map {
                if (it.id == pattern.id) it.copy(isFavorite = !existing.isFavorite) else it
            }
            store.save(_saved.value)
        }
    }

    fun play(pattern: KnightPattern, repeats: Int) {
        val levels = pattern.steps.map { (it.intensity.coerceIn(0, 100) * 255f / 100f).toInt() }.toIntArray()
        val durations = pattern.steps.map { it.durationMs }.toLongArray()
        app.startService(
            Intent(app, KnightControlService::class.java)
                .setAction(KnightControlService.ACTION_PLAY_PATTERN)
                .putExtra(KnightControlService.EXTRA_PATTERN_NAME, pattern.name)
                .putExtra(KnightControlService.EXTRA_PATTERN_LEVELS, levels)
                .putExtra(KnightControlService.EXTRA_PATTERN_DURATIONS, durations)
                .putExtra(KnightControlService.EXTRA_PATTERN_REPEATS, repeats.coerceIn(0, 100)),
        )
        store.addHistory(pattern.id)
        _history.value = store.loadHistory()
    }

    fun stop() {
        app.startService(Intent(app, KnightControlService::class.java).setAction(KnightControlService.ACTION_STOP))
    }
}

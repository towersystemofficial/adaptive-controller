package com.towersys.adaptiveremote.patterns

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PatternStore(context: Context) {
    private val preferences = context.getSharedPreferences("knight_patterns", Context.MODE_PRIVATE)

    fun load(): List<KnightPattern> = runCatching {
        val array = JSONArray(preferences.getString(KEY_PATTERNS, "[]"))
        List(array.length()) { index -> array.getJSONObject(index).toPattern() }
    }.getOrDefault(emptyList())

    fun save(patterns: List<KnightPattern>) {
        val array = JSONArray()
        patterns.forEach { pattern -> array.put(pattern.toJson()) }
        preferences.edit().putString(KEY_PATTERNS, array.toString()).apply()
    }

    fun addHistory(patternId: String) {
        val history = loadHistory().toMutableList().apply {
            remove(patternId)
            add(0, patternId)
        }.take(20)
        preferences.edit().putString(KEY_HISTORY, JSONArray(history).toString()).apply()
    }

    fun loadHistory(): List<String> = runCatching {
        val array = JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        List(array.length()) { index -> array.getString(index) }
    }.getOrDefault(emptyList())

    private fun KnightPattern.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("favorite", isFavorite)
        .put("builtIn", isBuiltIn)
        .put("steps", JSONArray().also { array ->
            steps.forEach { step ->
                array.put(JSONObject().put("intensity", step.intensity).put("duration", step.durationMs))
            }
        })

    private fun JSONObject.toPattern(): KnightPattern {
        val stepArray = getJSONArray("steps")
        return KnightPattern(
            id = getString("id"),
            name = getString("name"),
            isFavorite = optBoolean("favorite"),
            isBuiltIn = optBoolean("builtIn"),
            steps = List(stepArray.length()) { index ->
                stepArray.getJSONObject(index).let {
                    PatternStep(it.getInt("intensity"), it.getLong("duration"))
                }
            },
        )
    }

    companion object {
        private const val KEY_PATTERNS = "patterns"
        private const val KEY_HISTORY = "history"
    }
}

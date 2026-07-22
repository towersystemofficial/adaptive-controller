package com.towersys.adaptiveremote.text

import com.towersys.adaptiveremote.patterns.KnightPattern
import com.towersys.adaptiveremote.patterns.PatternStep
import com.towersys.adaptiveremote.core.MotionGatePolicy
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TextInterpretation(
    val summary: String,
    val hasActiveSteps: Boolean,
    val pattern: KnightPattern,
)

class GrokTextClient {
    fun interpret(
        apiKey: String,
        passage: String,
        targetDurationMs: Long = TextPatternPolicy.estimateDurationMs(passage),
        recentHistory: List<String> = emptyList(),
        boundedBatch: Boolean = false,
    ): TextInterpretation {
        val body = JSONObject()
            .put("model", MODEL)
            .put("store", false)
            .put("temperature", 0.4)
            .put("max_tokens", 1_400)
            .put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(
                    JSONObject().put("role", "user").put(
                        "content",
                        "Target batch duration: ${maxOf(20_000L, targetDurationMs)}ms. " +
                            "Recent executed batches: ${recentHistory.takeLast(3).joinToString(" | ").ifBlank { "none" }}. " +
                            "Analyze both the whole passage and its individual chronological parts. " +
                            "Visible passage:\n${passage.take(MAX_TEXT_CHARS)}",
                    ),
                )
            })

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val responseText = (if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }).bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText).getJSONObject("error").getString("message")
                }.getOrDefault("Grok request failed (${connection.responseCode}).")
                error(message)
            }
            parseResponse(responseText, targetDurationMs, boundedBatch)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(
        responseText: String,
        targetDurationMs: Long,
        boundedBatch: Boolean,
    ): TextInterpretation {
        val rawContent = JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        val content = rawContent
            .substringAfter("```json", missingDelimiterValue = rawContent)
            .substringBefore("```")
            .trim()
        val json = JSONObject(content.substring(content.indexOf('{'), content.lastIndexOf('}') + 1))
        val qualifyingActionPresent = json.optBoolean("qualifying_action_present", false)
        val stepArray = json.getJSONArray("steps")
        val passageHasExplicitAction = qualifyingActionPresent ||
            (0 until stepArray.length()).any {
                stepArray.getJSONObject(it).optBoolean("explicit_physical_action", false)
            }
        val parsedSteps = List(stepArray.length().coerceAtMost(TextPatternPolicy.MAX_STEPS)) { index ->
            stepArray.getJSONObject(index).let { step ->
                PatternStep(
                    intensity = MotionGatePolicy.gatedIntensity(
                        requestedIntensity = step.getInt("intensity"),
                        explicitPhysicalAction = passageHasExplicitAction &&
                            step.optBoolean("explicit_physical_action", false),
                    ),
                    durationMs = step.getLong("duration_ms").coerceIn(250, 10_000),
                )
            }
        }
        require(parsedSteps.isNotEmpty()) { "Grok returned an empty timeline." }
        val requestedDuration = if (boundedBatch) 20_000L else maxOf(
                targetDurationMs,
                json.optLong("suggested_duration_ms", targetDurationMs)
                    .coerceIn(TextPatternPolicy.MIN_DURATION_MS, TextPatternPolicy.MAX_DURATION_MS),
            )
        val steps = TextPatternPolicy.fitDuration(parsedSteps, requestedDuration)
        return TextInterpretation(
            summary = json.optString("summary", "Whole-passage interpretation"),
            hasActiveSteps = steps.any { it.intensity > 0 },
            pattern = KnightPattern(
                id = "text-${System.currentTimeMillis()}",
                name = json.optString("name", "Text response"),
                steps = steps,
            ),
        )
    }

    companion object {
        private const val ENDPOINT = "https://api.x.ai/v1/chat/completions"
        private const val MODEL = "grok-4.5"
        private const val MAX_TEXT_CHARS = 12_000
        private val SYSTEM_PROMPT = """
            First analyze the whole passage for its continuing scene state and overall pacing, then analyze
            its individual sentences/paragraphs chronologically to produce the steps. Dialogue interspersed
            inside ongoing physical action does NOT end or pause that action. Preserve the active state across
            dialogue unless the passage explicitly says the action stopped, paused, transitioned, entered
            denial, or moved to aftermath/aftercare.
            Return JSON only, with this exact shape:
            {"name":"short title","summary":"one sentence","qualifying_action_present":false,
            "suggested_duration_ms":20000,
            "steps":[{"explicit_physical_action":false,"intensity":0,"duration_ms":1000}]}
            Intensity is an abstract 0-100 response level. Create 4-20 chronological steps whose durations
            total approximately suggested_duration_ms. Treat the supplied target as a minimum based on text
            length; increase suggested_duration_ms when the passage describes substantial elapsed narrative
            time or slower pacing, up to 120000. Follow changes in the passage. Begin gently; end in a state
            that can blend into the next passage. Use 250-10000 ms per step. Do not include Markdown.

            CRITICAL MOTION RULE: set explicit_physical_action=true only while directly described physical
            sexual contact or motion is actually occurring in that exact step. Set it false and intensity=0
            for ordinary narrative, thoughts, attraction, anticipation, setup, undressing without
            contact, teasing, denial, pauses, scene transitions, climax aftermath, and all aftercare. Never
            infer action from tone or context. If the passage does not explicitly describe qualifying action,
            every step must be false with intensity 0.
        """.trimIndent()
    }
}

package com.towersys.adaptiveremote.procedural

import com.towersys.adaptiveremote.patterns.KnightPattern
import com.towersys.adaptiveremote.patterns.PatternStep
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

enum class ProceduralStyle { TEASE, STANDARD, DENIAL, EDGE }

data class ProceduralBatch(
    val style: ProceduralStyle,
    val summary: String,
    val pattern: KnightPattern,
)

class GrokProceduralClient {
    fun generate(
        apiKey: String,
        allowedStyles: Set<ProceduralStyle>,
        recentHistory: List<String>,
        batchNumber: Int,
        openingTeasesRemaining: Int,
        forcedStyle: ProceduralStyle? = null,
        specialInstruction: String? = null,
    ): ProceduralBatch {
        val styleInstruction = forcedStyle?.let { "You MUST select ${it.name}." }
            ?: "Select randomly from ${allowedStyles.joinToString { it.name }}."
        val userPrompt = """
            Batch number: $batchNumber. Opening Teases remaining: $openingTeasesRemaining.
            $styleInstruction
            Recent executed batches: ${recentHistory.takeLast(3).joinToString(" | ").ifBlank { "none" }}.
            ${specialInstruction.orEmpty()}
            Generate the next batch now.
        """.trimIndent()
        val body = JSONObject()
            .put("model", MODEL)
            .put("store", false)
            .put("temperature", 0.9)
            .put("max_tokens", 1_300)
            .put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(JSONObject().put("role", "user").put("content", userPrompt))
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
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                val message = runCatching { JSONObject(response).getJSONObject("error").getString("message") }
                    .getOrDefault("Grok procedural request failed (${connection.responseCode}).")
                error(message)
            }
            parse(response, allowedStyles, forcedStyle)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(
        response: String,
        allowedStyles: Set<ProceduralStyle>,
        forcedStyle: ProceduralStyle?,
    ): ProceduralBatch {
        val raw = JSONObject(response).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        val content = raw.substringAfter("```json", raw).substringBefore("```").trim()
        val json = JSONObject(content.substring(content.indexOf('{'), content.lastIndexOf('}') + 1))
        val requested = runCatching { ProceduralStyle.valueOf(json.getString("style").uppercase()) }
            .getOrDefault(ProceduralStyle.STANDARD)
        val style = forcedStyle ?: requested.takeIf { it in allowedStyles } ?: ProceduralStyle.STANDARD
        val sourceSteps = json.getJSONArray("steps")
        require(sourceSteps.length() > 0) { "Grok returned an empty procedural batch." }
        val parsed = List(sourceSteps.length().coerceAtMost(30)) { index ->
            sourceSteps.getJSONObject(index).let {
                PatternStep(
                    intensity = it.optInt("intensity", 0).coerceIn(0, 100),
                    durationMs = it.optLong("duration_ms", 1_000).coerceIn(100, 25_000),
                )
            }
        }
        val steps = enforceStyle(style, parsed)
        return ProceduralBatch(
            style = style,
            summary = json.optString("summary", style.name.lowercase()),
            pattern = KnightPattern(
                id = "procedural-${System.currentTimeMillis()}",
                name = "Procedural • ${style.name.lowercase().replaceFirstChar { it.uppercase() }}",
                steps = steps,
            ),
        )
    }

    private fun enforceStyle(style: ProceduralStyle, source: List<PatternStep>): List<PatternStep> = when (style) {
        ProceduralStyle.TEASE -> {
            var bursts = 0
            fitDuration(source, TARGET_BATCH_MS).map { step ->
                if (step.intensity > TEASE_LOW_MAX && bursts < TEASE_MAX_BURSTS) {
                    bursts++
                    step.copy(durationMs = step.durationMs.coerceAtMost(1_000))
                } else step.copy(intensity = step.intensity.coerceIn(1, TEASE_LOW_MAX))
            }
        }
        ProceduralStyle.STANDARD -> fitDuration(source, TARGET_BATCH_MS)
        ProceduralStyle.EDGE -> fitDuration(source, TARGET_BATCH_MS)
        ProceduralStyle.DENIAL -> enforceDenial(source)
    }

    private fun fitDuration(source: List<PatternStep>, targetMs: Long): List<PatternStep> {
        val total = source.sumOf { it.durationMs }.coerceAtLeast(1L)
        return source.map { step ->
            step.copy(durationMs = (step.durationMs * targetMs / total).coerceAtLeast(100L))
        }
    }

    private fun enforceDenial(source: List<PatternStep>): List<PatternStep> {
        val existingPause = source.indexOfFirst { it.intensity == 0 && it.durationMs in 15_000L..25_000L }
        if (existingPause >= 0) {
            val pause = source[existingPause].durationMs
            if (pause <= 20_000L) return source
            val splitPause = listOf(
                PatternStep(0, (pause - 3_000L) / 2),
                PatternStep(100, 3_000L),
                PatternStep(0, (pause - 3_000L) - (pause - 3_000L) / 2),
            )
            return source.take(existingPause) + splitPause + source.drop(existingPause + 1)
        }
        val pause = kotlin.random.Random.nextLong(15_000L, 25_001L)
        val insertion = (source.size / 2).coerceAtLeast(1)
        val denial = if (pause > 20_000L) {
            listOf(
                PatternStep(0, (pause - 3_000L) / 2),
                PatternStep(100, 3_000L),
                PatternStep(0, (pause - 3_000L) - (pause - 3_000L) / 2),
            )
        } else listOf(PatternStep(0, pause))
        return source.take(insertion) + denial + source.drop(insertion)
    }

    companion object {
        private const val ENDPOINT = "https://api.x.ai/v1/chat/completions"
        private const val MODEL = "grok-4.5"
        private const val TEASE_LOW_MAX = 22
        private const val TEASE_MAX_BURSTS = 2
        private const val TARGET_BATCH_MS = 20_000L
        private val SYSTEM_PROMPT = """
            Compose a creative device-control batch. Return JSON only:
            {"style":"STANDARD","summary":"brief description","steps":[
            {"intensity":40,"duration_ms":1000}]}

            Normal batches should last about 20 seconds. Intensity is 0-100.
            TEASE: heavily favor varied 1-22 output for long stretches, with at most two rare higher bursts,
            each no longer than one second. STANDARD: freely improvise waves, pulses, ramps, plateaus,
            stutters, and irregular patterns. DENIAL: create Standard-like activity and mix in one randomly
            placed 15-25 second zero-output pause; if the pause exceeds 20 seconds, put one three-second
            100-intensity burst in its middle. EDGE: start slowly and ramp upward with randomness, reversals,
            variation, and stutters. Never output a FINISH style. Use recent history to continue coherently
            while avoiding obvious repetition. End in a state that blends naturally into another batch.
        """.trimIndent()
    }
}

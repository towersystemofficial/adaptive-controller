package com.towersys.adaptiveremote.video

import com.towersys.adaptiveremote.core.MotionGatePolicy
import com.towersys.adaptiveremote.patterns.KnightPattern
import com.towersys.adaptiveremote.patterns.PatternStep
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class VideoInterpretation(
    val summary: String,
    val explicitContentPresent: Boolean,
    val pattern: KnightPattern,
)

class GrokVideoClient {
    fun interpret(
        apiKey: String,
        jpegFramesBase64: List<String>,
        recentHistory: List<String> = emptyList(),
    ): VideoInterpretation {
        require(jpegFramesBase64.size == VideoSamplingPolicy.FRAME_COUNT)
        val userContent = JSONArray().put(
            JSONObject().put("type", "text").put(
                "text",
                "These are exactly five frames sampled evenly at 0.00s, 0.25s, 0.50s, 0.75s, and 1.00s " +
                    "from one continuous screen-video interval. Analyze their ordered visual motion. " +
                    "Recent executed batches: ${recentHistory.takeLast(3).joinToString(" | ").ifBlank { "none" }}.",
            ),
        )
        jpegFramesBase64.forEach { frame ->
            userContent.put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$frame").put("detail", "low"),
                ),
            )
        }
        val body = JSONObject()
            .put("model", MODEL)
            .put("store", false)
            .put("temperature", 0.2)
            .put("max_tokens", 700)
            .put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(JSONObject().put("role", "user").put("content", userContent))
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
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText).getJSONObject("error").getString("message")
                }.getOrDefault("Grok video request failed (${connection.responseCode}).")
                error(message)
            }
            parse(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(responseText: String): VideoInterpretation {
        val raw = JSONObject(responseText).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        val content = raw.substringAfter("```json", raw).substringBefore("```").trim()
        val json = JSONObject(content.substring(content.indexOf('{'), content.lastIndexOf('}') + 1))
        val globalExplicitContent = json.optBoolean(
            "explicit_content_present",
            json.optBoolean("qualifying_action_present", false),
        )
        val array = json.getJSONArray("steps")
        require(array.length() > 0) { "Grok returned an empty video timeline." }
        val clusterHasExplicitContent = globalExplicitContent ||
            (0 until array.length()).any { array.getJSONObject(it).optBoolean("explicit_physical_action", false) }
        val parsedSteps = List(array.length().coerceAtMost(16)) { index ->
            array.getJSONObject(index).let { step ->
                val explicitAction = clusterHasExplicitContent &&
                    (globalExplicitContent || step.optBoolean("explicit_physical_action", false))
                PatternStep(
                    intensity = MotionGatePolicy.gatedIntensity(
                        requestedIntensity = if (explicitAction) {
                            step.getInt("intensity").coerceIn(MIN_EXPLICIT_INTENSITY, 100)
                        } else 0,
                        explicitPhysicalAction = explicitAction,
                    ),
                    durationMs = step.optLong("duration_ms", 750).coerceIn(250, 4_000),
                )
            }
        }
        val parsedDuration = parsedSteps.sumOf { it.durationMs }.coerceAtLeast(1L)
        val steps = parsedSteps.map { step ->
            step.copy(durationMs = (step.durationMs * TARGET_BATCH_MS / parsedDuration).coerceAtLeast(250L))
        }
        return VideoInterpretation(
            summary = json.optString("summary", "Video response"),
            explicitContentPresent = clusterHasExplicitContent,
            pattern = KnightPattern(
                id = "video-${System.currentTimeMillis()}",
                name = "Live video",
                steps = steps,
            ),
        )
    }

    companion object {
        private const val ENDPOINT = "https://api.x.ai/v1/chat/completions"
        private const val MODEL = "grok-4.5"
        private val SYSTEM_PROMPT = """
            Convert the ordered five-frame, one-second video cluster into a short device-motion timeline.
            Return JSON only:
            {"summary":"brief visible event","explicit_content_present":false,"steps":[
            {"explicit_physical_action":false,"intensity":0,"duration_ms":750}]}

            VISUAL GATE RULE: explicit_content_present must be true whenever clearly explicit sexual content
            is visibly present in the frames, including exposed genitals, masturbation, oral activity,
            penetration, direct sexual touching, or another unmistakably explicit act. This is a visual
            classification, not a narrative classification: do not turn the gate off merely because the
            scene could be described as setup, teasing, denial, a pause, aftermath, or aftercare while
            explicit content remains visible. Slow or nearly static explicit content still counts.

            Set explicit_content_present=false only when no clearly explicit sexual content is visible.
            Never infer explicit content solely from clothing, expressions, dialogue, or prior clusters.
            The phone measures motion separately, so intensity should describe the visible explicit content,
            not raw pixel movement. When explicit_content_present=true, mark the timeline steps active and
            create 8-16 steps covering roughly twenty seconds. End in a state suitable for blending.
        """.trimIndent()
        private const val MIN_EXPLICIT_INTENSITY = 5
        private const val TARGET_BATCH_MS = 20_000L
    }
}

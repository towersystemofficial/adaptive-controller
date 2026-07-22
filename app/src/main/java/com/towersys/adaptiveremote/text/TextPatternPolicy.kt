package com.towersys.adaptiveremote.text

import com.towersys.adaptiveremote.patterns.PatternStep

object TextPatternPolicy {
    const val MAX_STEPS = 20
    const val MIN_DURATION_MS = 5_000L
    const val MAX_DURATION_MS = 120_000L

    fun estimateDurationMs(text: String): Long {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return (words * MS_PER_WORD).coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
    }

    fun fitDuration(input: List<PatternStep>, targetDurationMs: Long): List<PatternStep> {
        require(input.isNotEmpty())
        val target = targetDurationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
        val scale = target.toDouble() / input.sumOf(PatternStep::durationMs).coerceAtLeast(1)
        val result = input.map { step ->
            step.copy(durationMs = (step.durationMs * scale).toLong().coerceIn(250, 10_000))
        }.toMutableList()
        while (result.sumOf(PatternStep::durationMs) < target && result.size < MAX_STEPS) {
            val remaining = target - result.sumOf(PatternStep::durationMs)
            result += result.last().copy(durationMs = remaining.coerceIn(250, 10_000))
        }
        return result
    }

    private const val MS_PER_WORD = 300L
}

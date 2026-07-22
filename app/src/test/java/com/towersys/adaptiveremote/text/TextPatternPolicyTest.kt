package com.towersys.adaptiveremote.text

import com.towersys.adaptiveremote.patterns.PatternStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPatternPolicyTest {
    @Test
    fun response_isScaledToEstimatedReadingTime() {
        val result = TextPatternPolicy.fitDuration(
            listOf(PatternStep(20, 1_000), PatternStep(70, 1_000), PatternStep(10, 1_000)),
            targetDurationMs = 20_000,
        )

        assertTrue(result.sumOf(PatternStep::durationMs) >= 20_000)
        assertTrue(result.all { it.durationMs <= 10_000 })
    }

    @Test
    fun matchingResponse_isUnchanged() {
        val input = listOf(PatternStep(50, 10_000), PatternStep(25, 10_000))
        assertEquals(input, TextPatternPolicy.fitDuration(input, 20_000))
    }

    @Test
    fun moreText_producesLongerTargetDuration() {
        val short = TextPatternPolicy.estimateDurationMs("a short passage")
        val long = TextPatternPolicy.estimateDurationMs(List(100) { "word" }.joinToString(" "))
        assertTrue(long > short)
    }
}

package com.towersys.adaptiveremote.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternModelsTest {
    @Test
    fun duration_isSumOfStepDurations() {
        val pattern = KnightPattern(
            id = "test",
            name = "Test",
            steps = listOf(PatternStep(10, 500), PatternStep(80, 1_250)),
        )

        assertEquals(1_750, pattern.durationMs)
    }

    @Test
    fun proceduralPattern_staysWithinEditorAndPlaybackBounds() {
        val pattern = generateProceduralPattern(seed = 42)

        assertTrue(pattern.steps.size in 6..12)
        assertTrue(pattern.steps.all { it.intensity in 10..90 })
        assertTrue(pattern.steps.all { it.durationMs in 350..1_500 })
    }

    @Test
    fun zeroRepeats_continuesUntilExternallyStopped() {
        assertTrue(PatternRepeatPolicy.shouldStartCycle(PatternRepeatPolicy.UNTIL_STOP, 10_000))
    }

    @Test
    fun finiteRepeats_stopsAtRequestedCycleCount() {
        assertTrue(PatternRepeatPolicy.shouldStartCycle(3, 2))
        assertEquals(false, PatternRepeatPolicy.shouldStartCycle(3, 3))
    }
}

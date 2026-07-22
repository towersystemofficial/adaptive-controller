package com.towersys.adaptiveremote.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionGatePolicyTest {
    @Test
    fun inactiveNarrative_isForcedOff() {
        assertEquals(0, MotionGatePolicy.gatedIntensity(85, false))
    }

    @Test
    fun explicitAction_preservesRequestedIntensity() {
        assertEquals(85, MotionGatePolicy.gatedIntensity(85, true))
    }
}

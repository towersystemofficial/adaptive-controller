package com.towersys.adaptiveremote.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IntensityScalerTest {
    @Test
    fun defaultMultiplierScalesOutputProportionally() {
        assertEquals(0.4f, IntensityScaler.scale(0.8f, 0.5f), 0.0001f)
    }

    @Test
    fun multiplierIsNotAHardCap() {
        assertEquals(0.9f, IntensityScaler.scale(0.6f, 1.5f), 0.0001f)
    }

    @Test
    fun resultIsClampedToDeviceRange() {
        assertEquals(1f, IntensityScaler.scale(1f, 1.5f), 0.0001f)
        assertEquals(0f, IntensityScaler.scale(-0.5f, 1f), 0.0001f)
    }

    @Test
    fun invalidMultiplierIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            IntensityScaler.scale(0.5f, -0.1f)
        }
    }
}


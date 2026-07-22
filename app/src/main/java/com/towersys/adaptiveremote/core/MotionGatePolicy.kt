package com.towersys.adaptiveremote.core

object MotionGatePolicy {
    fun gatedIntensity(requestedIntensity: Int, explicitPhysicalAction: Boolean): Int =
        if (explicitPhysicalAction) requestedIntensity.coerceIn(0, 100) else 0
}

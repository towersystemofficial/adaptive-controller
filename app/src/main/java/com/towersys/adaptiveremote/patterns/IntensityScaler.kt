package com.towersys.adaptiveremote.patterns

/**
 * Converts normalized generated output into a valid normalized device value.
 * The multiplier is intentionally scaling rather than a hard ceiling.
 */
object IntensityScaler {
    fun scale(normalizedIntensity: Float, multiplier: Float): Float {
        require(normalizedIntensity.isFinite()) { "Intensity must be finite" }
        require(multiplier.isFinite() && multiplier >= 0f) {
            "Multiplier must be finite and non-negative"
        }

        return (normalizedIntensity.coerceIn(0f, 1f) * multiplier).coerceIn(0f, 1f)
    }
}


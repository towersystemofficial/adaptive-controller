package com.towersys.adaptiveremote.patterns

data class PatternStep(
    val intensity: Int,
    val durationMs: Long,
)

data class KnightPattern(
    val id: String,
    val name: String,
    val steps: List<PatternStep>,
    val isFavorite: Boolean = false,
    val isBuiltIn: Boolean = false,
) {
    val durationMs: Long get() = steps.sumOf(PatternStep::durationMs)
}

object BuiltInPatterns {
    val all = listOf(
        KnightPattern(
            id = "builtin-wave",
            name = "Wave",
            steps = listOf(20, 40, 65, 90, 65, 40).map { PatternStep(it, 700) },
            isBuiltIn = true,
        ),
        KnightPattern(
            id = "builtin-pulse",
            name = "Pulse",
            steps = listOf(75, 10, 75, 10).map { PatternStep(it, 450) },
            isBuiltIn = true,
        ),
        KnightPattern(
            id = "builtin-staircase",
            name = "Staircase",
            steps = listOf(15, 30, 45, 60, 75, 90).map { PatternStep(it, 800) },
            isBuiltIn = true,
        ),
    )
}

fun generateProceduralPattern(seed: Long = System.currentTimeMillis()): KnightPattern {
    val random = kotlin.random.Random(seed)
    val steps = List(random.nextInt(6, 13)) {
        PatternStep(
            intensity = random.nextInt(10, 91),
            durationMs = random.nextLong(350, 1_501),
        )
    }
    return KnightPattern(
        id = "generated-$seed",
        name = "Generated pattern",
        steps = steps,
    )
}

object PatternRepeatPolicy {
    const val UNTIL_STOP = 0

    fun shouldStartCycle(requestedRepeats: Int, completedCycles: Int): Boolean =
        requestedRepeats == UNTIL_STOP || completedCycles < requestedRepeats.coerceIn(1, 100)
}

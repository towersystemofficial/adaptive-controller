package com.towersys.adaptiveremote.video

object VideoSamplingPolicy {
    const val FRAME_COUNT = 5
    const val FRAME_INTERVAL_MS = 250L
    const val CLUSTER_SPAN_MS = 1_000L

    fun frameOffsetsMs(): List<Long> = List(FRAME_COUNT) { index -> index * FRAME_INTERVAL_MS }
}

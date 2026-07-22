package com.towersys.adaptiveremote.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSamplingPolicyTest {
    @Test
    fun clusterHasFiveEvenlySpacedFramesAcrossOneSecond() {
        assertEquals(listOf(0L, 250L, 500L, 750L, 1_000L), VideoSamplingPolicy.frameOffsetsMs())
        assertEquals(VideoSamplingPolicy.CLUSTER_SPAN_MS, VideoSamplingPolicy.frameOffsetsMs().last())
    }
}

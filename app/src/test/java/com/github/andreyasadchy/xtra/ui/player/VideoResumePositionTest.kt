package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoResumePositionTest {
    @Test fun `completed and overrun positions restart exactly at zero`() {
        assertEquals(0L, videoResumePosition(60_000L, 60_000L))
        assertEquals(0L, videoResumePosition(60_001L, 60_000L))
    }

    @Test fun `unfinished videos retain even the last millisecond`() {
        assertEquals(59_999L, videoResumePosition(59_999L, 60_000L))
        assertEquals(0L, videoResumePosition(null, 60_000L))
        assertEquals(0L, videoResumePosition(0L, 60_000L))
    }

    @Test fun `unknown zero and invalid durations never infer completion`() {
        for (duration in listOf(null, 0L, -1L, Long.MIN_VALUE)) {
            assertEquals(60_000L, videoResumePosition(60_000L, duration))
        }
    }

    @Test fun `long recordings use long millisecond arithmetic`() {
        val duration = Int.MAX_VALUE.toLong() * 1000L
        assertEquals(duration - 1, videoResumePosition(duration - 1, duration))
        assertEquals(0L, videoResumePosition(duration, duration))
    }
}

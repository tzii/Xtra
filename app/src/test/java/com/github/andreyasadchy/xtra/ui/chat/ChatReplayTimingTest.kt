package com.github.andreyasadchy.xtra.ui.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatReplayTimingTest {
    @Test fun `short waits never become a non-suspending delay at supported speeds`() {
        for (speed in listOf(0.25f, 1f, 1.5f, 2f, 4f)) {
            assertEquals((1L / speed).toLong().coerceAtLeast(1L), chatReplayDelayMillis(1L, speed))
        }
        assertEquals(1L, chatReplayDelayMillis(0L, 4f))
        assertEquals(1L, chatReplayDelayMillis(-1L, 4f))
    }

    @Test fun `ordinary timing stays proportional to playback speed`() {
        assertEquals(4000L, chatReplayDelayMillis(1000L, 0.25f))
        assertEquals(1000L, chatReplayDelayMillis(1000L, 1f))
        assertEquals(666L, chatReplayDelayMillis(1000L, 1.5f))
        assertEquals(500L, chatReplayDelayMillis(1000L, 2f))
        assertEquals(250L, chatReplayDelayMillis(1000L, 4f))
    }

    @Test fun `missing or invalid speed uses normal playback timing`() {
        for (speed in listOf(null, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(1000L, chatReplayDelayMillis(1000L, speed))
        }
    }

    @Test fun `high-speed position polling yields and can be cancelled`() = runTest {
        var polls = 0
        val job = launch {
            // Bounded even if the zero-delay regression returns, so failure cannot hang CI.
            repeat(1000) {
                polls++
                delay(chatReplayDelayMillis(1L, 4f))
            }
        }
        runCurrent()
        assertEquals(1, polls)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, polls)
        job.cancel()
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(2, polls)
    }
}

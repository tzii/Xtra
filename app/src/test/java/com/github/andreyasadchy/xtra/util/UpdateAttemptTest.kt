package com.github.andreyasadchy.xtra.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UpdateAttemptTest {
    @Test fun `successful preparation reports success`() = runTest { assertTrue(runUpdateAttempt {}) }
    @Test fun `network or installer preparation failures report failure`() = runTest {
        assertFalse(runUpdateAttempt { throw java.io.IOException("fixture") })
        assertFalse(runUpdateAttempt { throw SecurityException("fixture") })
    }
    @Test fun `cancellation is propagated instead of showing failure UI`() = runTest {
        try { runUpdateAttempt { throw CancellationException("fixture") }; fail("must propagate") }
        catch (_: CancellationException) { }
    }
    @Test fun `already cancelled job never enters download or install preparation`() = runTest {
        var entered = false
        val job = launch {
            cancel()
            runUpdateAttempt { entered = true }
        }
        job.join()
        assertFalse(entered)
        assertTrue(job.isCancelled)
    }
}

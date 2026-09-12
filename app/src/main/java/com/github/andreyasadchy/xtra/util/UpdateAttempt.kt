package com.github.andreyasadchy.xtra.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A cancelled attempt is not a failure and must never trigger retry UI. */
internal suspend fun runUpdateAttempt(block: suspend () -> Unit): Boolean = try {
    currentCoroutineContext().ensureActive()
    block()
    currentCoroutineContext().ensureActive()
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    false
}

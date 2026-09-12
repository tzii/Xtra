package com.github.andreyasadchy.xtra.ui.chat

/** Always suspend between position polls, even when high speed rounds a wait to zero. */
internal fun chatReplayDelayMillis(remainingMs: Long, playbackSpeed: Float?): Long {
    val speed = playbackSpeed?.takeIf { it.isFinite() && it > 0f } ?: 1f
    return (remainingMs / speed).toLong().coerceAtLeast(1L)
}

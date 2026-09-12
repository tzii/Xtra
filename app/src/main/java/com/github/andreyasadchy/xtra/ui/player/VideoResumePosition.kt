package com.github.andreyasadchy.xtra.ui.player

/** Apply only to automatic resume, never an explicit timestamp or an active session. */
internal fun videoResumePosition(savedPositionMs: Long?, durationMs: Long?): Long {
    val position = savedPositionMs ?: 0L
    return if (durationMs != null && durationMs > 0L && position >= durationMs) 0L else position
}

package com.github.andreyasadchy.xtra.ui.player

/**
 * Pure horizontal-gesture zone selection for seekable playback.
 *
 * The upper zone drives playback speed and the lower zone seeks, so the most
 * physical gesture (large horizontal drags at the bottom) maps to scrubbing.
 * A start position exactly on the split line belongs to the lower (seek)
 * zone: the comparison is strictly less-than, matching the boundary behavior
 * the gesture listener has always used.
 */
enum class PlayerHorizontalGestureZone {
    PLAYBACK_SPEED,
    SEEK,
}

object PlayerGestureZonePolicy {

    fun horizontalZone(startY: Float, surfaceHeightPx: Float, zoneSplit: Float): PlayerHorizontalGestureZone {
        if (surfaceHeightPx <= 0f || !zoneSplit.isFinite()) {
            return PlayerHorizontalGestureZone.SEEK
        }
        val split = zoneSplit.coerceIn(0f, 1f)
        return if (startY < surfaceHeightPx * split) {
            PlayerHorizontalGestureZone.PLAYBACK_SPEED
        } else {
            PlayerHorizontalGestureZone.SEEK
        }
    }
}

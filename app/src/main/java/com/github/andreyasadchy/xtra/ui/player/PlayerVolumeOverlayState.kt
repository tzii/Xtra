package com.github.andreyasadchy.xtra.ui.player

/**
 * Remembers the stream volume that mute should restore. Keeping this state
 * independent of views makes first-open and repeated mute/unmute behavior
 * deterministic across every playback backend.
 */
internal class PlayerVolumeOverlayState(initialNonZeroPercent: Int = 100) {

    private var lastNonZeroPercent = initialNonZeroPercent.coerceIn(1, 100)

    fun remember(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        if (clamped > 0) {
            lastNonZeroPercent = clamped
        }
    }

    fun targetAfterToggle(currentPercent: Int): Int {
        val clamped = currentPercent.coerceIn(0, 100)
        return if (clamped > 0) {
            remember(clamped)
            0
        } else {
            lastNonZeroPercent
        }
    }
}

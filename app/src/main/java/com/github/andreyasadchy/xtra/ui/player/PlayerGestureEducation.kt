package com.github.andreyasadchy.xtra.ui.player

/**
 * Pure gesture-education content and state logic.
 */
enum class PlayerGestureGuideContext {
    SEEKABLE,
    LIVE,
    SETTINGS,
}

enum class PlayerGestureGuideRow {
    BRIGHTNESS,
    DEVICE_VOLUME,
    SEEK,
    PLAYBACK_SPEED,
    PINCH,
    DOUBLE_TAP_CHAT,
}

object PlayerGestureGuideContent {

    /**
     * Live playback supports neither horizontal gesture, so those rows are
     * omitted entirely; the settings context keeps them with qualified copy.
     */
    fun rowsFor(context: PlayerGestureGuideContext): List<PlayerGestureGuideRow> {
        return when (context) {
            PlayerGestureGuideContext.SEEKABLE, PlayerGestureGuideContext.SETTINGS -> PlayerGestureGuideRow.entries
            PlayerGestureGuideContext.LIVE -> PlayerGestureGuideRow.entries.filterNot {
                it == PlayerGestureGuideRow.SEEK || it == PlayerGestureGuideRow.PLAYBACK_SPEED
            }
        }
    }
}

/**
 * One-time state rules for the guide and the contextual pinch hint. The guide
 * is versioned so a materially revised guide may be shown once in a later
 * release; the hint is governed by separate shown/used preferences.
 */
object PlayerGestureEducationState {

    /**
     * Version 2: horizontal zones swapped — the upper zone now controls
     * playback speed and the lower zone seeks. Users who dismissed version 1
     * see the corrected guide once.
     */
    const val GUIDE_VERSION = 2

    fun shouldShowGuide(storedVersion: Int): Boolean = storedVersion < GUIDE_VERSION

    fun shouldShowPinchHint(
        guideStoredVersion: Int,
        pinchHintShown: Boolean,
        pinchUsed: Boolean,
        guideShownThisSession: Boolean,
    ): Boolean {
        return guideStoredVersion >= GUIDE_VERSION && !pinchHintShown && !pinchUsed && !guideShownThisSession
    }
}

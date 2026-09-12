package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureEducationTest {

    @Test
    fun `seekable context shows every row`() {
        assertEquals(
            PlayerGestureGuideRow.entries,
            PlayerGestureGuideContent.rowsFor(PlayerGestureGuideContext.SEEKABLE),
        )
    }

    @Test
    fun `live context omits both horizontal rows`() {
        val rows = PlayerGestureGuideContent.rowsFor(PlayerGestureGuideContext.LIVE)
        assertFalse(PlayerGestureGuideRow.SEEK in rows)
        assertFalse(PlayerGestureGuideRow.PLAYBACK_SPEED in rows)
        assertTrue(PlayerGestureGuideRow.BRIGHTNESS in rows)
        assertTrue(PlayerGestureGuideRow.DEVICE_VOLUME in rows)
        assertTrue(PlayerGestureGuideRow.PINCH in rows)
        assertTrue(PlayerGestureGuideRow.DOUBLE_TAP_CHAT in rows)
    }

    @Test
    fun `settings context keeps horizontal rows with qualified copy`() {
        assertEquals(
            PlayerGestureGuideRow.entries,
            PlayerGestureGuideContent.rowsFor(PlayerGestureGuideContext.SETTINGS),
        )
    }

    @Test
    fun `guide shows while stored version is behind`() {
        assertTrue(PlayerGestureEducationState.shouldShowGuide(0))
        assertFalse(PlayerGestureEducationState.shouldShowGuide(PlayerGestureEducationState.GUIDE_VERSION))
        assertFalse(PlayerGestureEducationState.shouldShowGuide(PlayerGestureEducationState.GUIDE_VERSION + 1))
    }

    @Test
    fun `guide version bump resurfaces the guide once for prior dismissals`() {
        val priorVersion = PlayerGestureEducationState.GUIDE_VERSION - 1
        assertTrue(PlayerGestureEducationState.shouldShowGuide(priorVersion))
        // After dismissing the current version it does not appear again.
        assertFalse(PlayerGestureEducationState.shouldShowGuide(PlayerGestureEducationState.GUIDE_VERSION))
    }

    @Test
    fun `pinch hint requires current guide, unshown hint, unused pinch, later session`() {
        val version = PlayerGestureEducationState.GUIDE_VERSION
        assertTrue(
            PlayerGestureEducationState.shouldShowPinchHint(
                guideStoredVersion = version,
                pinchHintShown = false,
                pinchUsed = false,
                guideShownThisSession = false,
            )
        )
        assertFalse(
            PlayerGestureEducationState.shouldShowPinchHint(
                guideStoredVersion = 0,
                pinchHintShown = false,
                pinchUsed = false,
                guideShownThisSession = false,
            )
        )
        assertFalse(
            PlayerGestureEducationState.shouldShowPinchHint(
                guideStoredVersion = version,
                pinchHintShown = true,
                pinchUsed = false,
                guideShownThisSession = false,
            )
        )
        assertFalse(
            PlayerGestureEducationState.shouldShowPinchHint(
                guideStoredVersion = version,
                pinchHintShown = false,
                pinchUsed = true,
                guideShownThisSession = false,
            )
        )
        assertFalse(
            PlayerGestureEducationState.shouldShowPinchHint(
                guideStoredVersion = version,
                pinchHintShown = false,
                pinchUsed = false,
                guideShownThisSession = true,
            )
        )
    }
}

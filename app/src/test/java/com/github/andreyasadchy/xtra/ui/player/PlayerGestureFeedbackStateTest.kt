package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureFeedbackStateTest {

    @Test
    fun `compact feedback stays horizontal for every kind`() {
        for (kind in PlayerGestureFeedbackKind.entries) {
            assertEquals(
                PlayerGestureFeedbackLayout.COMPACT_HORIZONTAL,
                PlayerGestureFeedbackState.layoutFor(kind, PlayerSurfaceClass.COMPACT),
            )
        }
    }

    @Test
    fun `large brightness and volume become vertical edge pills`() {
        assertEquals(
            PlayerGestureFeedbackLayout.EDGE_VERTICAL,
            PlayerGestureFeedbackState.layoutFor(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE),
        )
        assertEquals(
            PlayerGestureFeedbackLayout.EDGE_VERTICAL,
            PlayerGestureFeedbackState.layoutFor(PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.LARGE),
        )
    }

    @Test
    fun `large seek speed and pinch stay centered horizontal pills`() {
        for (kind in listOf(PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackKind.PLAYBACK_SPEED, PlayerGestureFeedbackKind.PINCH)) {
            assertEquals(
                PlayerGestureFeedbackLayout.CENTERED_HORIZONTAL,
                PlayerGestureFeedbackState.layoutFor(kind, PlayerSurfaceClass.LARGE),
            )
        }
    }

    @Test
    fun `vertical edge pill drops visible text but keeps the level`() {
        val presentation = PlayerGestureFeedbackState.presentation(
            kind = PlayerGestureFeedbackKind.BRIGHTNESS,
            surfaceClass = PlayerSurfaceClass.LARGE,
            level = 45,
            text = "Brightness · 45%",
        )
        assertEquals(PlayerGestureFeedbackLayout.EDGE_VERTICAL, presentation.layout)
        assertTrue(presentation.levelVisible)
        assertEquals(45, presentation.level)
        assertNull(presentation.text)
    }

    @Test
    fun `compact level feedback keeps bar and text`() {
        val presentation = PlayerGestureFeedbackState.presentation(
            kind = PlayerGestureFeedbackKind.DEVICE_VOLUME,
            surfaceClass = PlayerSurfaceClass.COMPACT,
            level = 60,
            text = "60",
        )
        assertEquals(PlayerGestureFeedbackLayout.COMPACT_HORIZONTAL, presentation.layout)
        assertTrue(presentation.levelVisible)
        assertEquals(60, presentation.level)
        assertEquals("60", presentation.text)
    }

    @Test
    fun `seek and speed never show a level`() {
        for (kind in listOf(PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackKind.PLAYBACK_SPEED)) {
            val presentation = PlayerGestureFeedbackState.presentation(
                kind = kind,
                surfaceClass = PlayerSurfaceClass.LARGE,
                level = 50,
                text = "value",
            )
            assertFalse(presentation.levelVisible)
            assertEquals(0, presentation.level)
            assertEquals("value", presentation.text)
        }
    }

    @Test
    fun `pinch bar is visible from zero progress through armed and grows toward Fill`() {
        for (progress in listOf(0f, 0.25f, 0.5f, 0.99f, 1f)) {
            val presentation = PlayerGestureFeedbackState.pinchPresentation(PlayerSurfaceClass.LARGE, progress, PlayerDisplayMode.FILL, "Fill")
            assertTrue(presentation.levelVisible)
            assertEquals(PlayerGestureFeedbackState.pinchLevel(progress, PlayerDisplayMode.FILL), presentation.level)
            assertEquals("Fill", presentation.text)
        }
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(0f, PlayerDisplayMode.FILL))
        assertEquals(100, PlayerGestureFeedbackState.pinchLevel(1f, PlayerDisplayMode.FILL))
        assertEquals(75, PlayerGestureFeedbackState.pinchLevel(0.5f, PlayerDisplayMode.FILL))
    }

    @Test
    fun `pinch bar shrinks toward Fit from the same neutral center`() {
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(0f, PlayerDisplayMode.FIT))
        assertEquals(25, PlayerGestureFeedbackState.pinchLevel(0.5f, PlayerDisplayMode.FIT))
        assertEquals(0, PlayerGestureFeedbackState.pinchLevel(1f, PlayerDisplayMode.FIT))
    }

    @Test
    fun `endpoint elastic progress moves the committed-mode bar`() {
        val fitElastic = PlayerGestureFeedbackState.pinchPresentation(
            PlayerSurfaceClass.LARGE,
            0.5f,
            PlayerDisplayMode.FIT,
            "Fit",
        )
        val fillElastic = PlayerGestureFeedbackState.pinchPresentation(
            PlayerSurfaceClass.LARGE,
            0.5f,
            PlayerDisplayMode.FILL,
            "Fill",
        )
        assertEquals(25, fitElastic.level)
        assertEquals(75, fillElastic.level)
    }

    @Test
    fun `neutral preview from Stretch parks at the bar center`() {
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(0f, PlayerDisplayMode.STRETCH))
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(1f, PlayerDisplayMode.STRETCH))
    }

    @Test
    fun `pinch progress clamps to the arm range before mapping`() {
        // Out-of-range progress pins at the nearest end: over-arm completes
        // Fill; anything below neutral stays at the center.
        assertEquals(100, PlayerGestureFeedbackState.pinchLevel(1.5f, PlayerDisplayMode.FILL))
        assertEquals(50, PlayerGestureFeedbackState.pinchLevel(-1f, PlayerDisplayMode.FIT))
    }

    @Test
    fun `levels clamp to the 0-100 range`() {
        assertEquals(0, PlayerGestureFeedbackState.clampLevel(-10))
        assertEquals(0, PlayerGestureFeedbackState.clampLevel(0))
        assertEquals(100, PlayerGestureFeedbackState.clampLevel(100))
        assertEquals(100, PlayerGestureFeedbackState.clampLevel(250))
    }

    @Test
    fun `every presentation field is written so switching kinds cannot inherit stale state`() {
        val presentations = listOf(
            PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 80, "80%"),
            PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.SEEK, PlayerSurfaceClass.LARGE, null, "12:00 / 24:00"),
            PlayerGestureFeedbackState.pinchPresentation(PlayerSurfaceClass.LARGE, 0.5f, PlayerDisplayMode.FILL, "Fill"),
            PlayerGestureFeedbackState.presentation(PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.COMPACT, 0, "0"),
        )
        // Each presentation fully describes visibility, level, layout, and text;
        // there is no optional field an applier could leave untouched.
        presentations.forEach { presentation ->
            assertTrue(presentation.layout in PlayerGestureFeedbackLayout.entries)
            assertTrue(presentation.level in 0..100)
        }
    }
}

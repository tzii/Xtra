package com.github.andreyasadchy.xtra.ui.player

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSurfacePolicyTest {

    @Test
    fun `compact below 600dp`() {
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(599, 1f))
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(1198, 2f))
    }

    @Test
    fun `large at or above 600dp`() {
        assertEquals(PlayerSurfaceClass.LARGE, PlayerSurfacePolicy.classify(600, 1f))
        assertEquals(PlayerSurfaceClass.LARGE, PlayerSurfacePolicy.classify(1200, 2f))
        assertEquals(PlayerSurfaceClass.LARGE, PlayerSurfacePolicy.classify(1920, 2f))
    }

    @Test
    fun `invalid dimensions fall back to compact`() {
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(0, 2f))
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(1200, 0f))
        assertEquals(PlayerSurfaceClass.COMPACT, PlayerSurfacePolicy.classify(-5, 2f))
    }

    @Test
    fun `compact placement keeps top-centered horizontal pill for every kind`() {
        for (kind in PlayerGestureFeedbackKind.entries) {
            val placement = PlayerSurfacePolicy.placementFor(kind, PlayerSurfaceClass.COMPACT, 2f)
            assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, placement.gravity)
            assertEquals((PlayerSurfacePolicy.CENTER_FEEDBACK_MAX_WIDTH_DP * 2f).toInt(), placement.maxWidthPx)
            assertEquals((16 * 2f).toInt(), placement.marginStartPx)
            assertEquals((16 * 2f).toInt(), placement.marginEndPx)
            assertTrue(placement.topPaddingPx > 0)
            assertNull(placement.fixedContainerWidthPx)
            assertNull(placement.fixedContainerHeightPx)
            assertFalse(placement.verticalPill)
        }
    }

    @Test
    fun `large brightness is a start-edge vertical pill with compact fixed size`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 2f,
            surfaceHeightPx = 1600,
        )
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.START, placement.gravity)
        assertEquals((PlayerSurfacePolicy.EDGE_PILL_WIDTH_DP * 2f).toInt(), placement.maxWidthPx)
        assertEquals((PlayerSurfacePolicy.EDGE_PILL_WIDTH_DP * 2f).toInt(), placement.fixedContainerWidthPx)
        assertEquals((PlayerSurfacePolicy.EDGE_PILL_HEIGHT_DP * 2f).toInt(), placement.fixedContainerHeightPx)
        assertEquals(0, placement.topPaddingPx)
        assertTrue(placement.verticalPill)
    }

    @Test
    fun `large device volume is an end-edge vertical pill`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.LARGE, 2f,
            surfaceHeightPx = 1600,
        )
        assertEquals(Gravity.CENTER_VERTICAL or Gravity.END, placement.gravity)
        assertEquals((PlayerSurfacePolicy.EDGE_PILL_WIDTH_DP * 2f).toInt(), placement.fixedContainerWidthPx)
        assertEquals((PlayerSurfacePolicy.EDGE_PILL_HEIGHT_DP * 2f).toInt(), placement.fixedContainerHeightPx)
        assertTrue(placement.verticalPill)
    }

    @Test
    fun `large seek speed and pinch stay top-centered and horizontal`() {
        for (kind in listOf(PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackKind.PLAYBACK_SPEED, PlayerGestureFeedbackKind.PINCH)) {
            val placement = PlayerSurfacePolicy.placementFor(kind, PlayerSurfaceClass.LARGE, 2f)
            assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, placement.gravity)
            assertEquals((PlayerSurfacePolicy.CENTER_FEEDBACK_MAX_WIDTH_DP * 2f).toInt(), placement.maxWidthPx)
            assertFalse(placement.verticalPill)
        }
    }

    @Test
    fun `edge pill is inset aware`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 2f,
            insetStartPx = 20, insetEndPx = 30,
        )
        assertEquals((16 * 2f).toInt() + 20, placement.marginStartPx)
        assertEquals((16 * 2f).toInt(), placement.marginEndPx)

        val volumePlacement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.LARGE, 2f,
            insetStartPx = 20, insetEndPx = 30,
        )
        assertEquals((16 * 2f).toInt() + 30, volumePlacement.marginEndPx)
    }

    @Test
    fun `compact placement ignores insets`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.COMPACT, 2f,
            insetStartPx = 20, insetEndPx = 30,
        )
        assertEquals((16 * 2f).toInt(), placement.marginStartPx)
        assertEquals((16 * 2f).toInt(), placement.marginEndPx)
    }

    @Test
    fun `edge pill height caps to a fraction of short surfaces`() {
        val density = 2f
        val desired = (PlayerSurfacePolicy.EDGE_PILL_HEIGHT_DP * density).toInt()
        assertEquals(desired, PlayerSurfacePolicy.edgePillHeightPx(density, 0))
        assertEquals(desired, PlayerSurfacePolicy.edgePillHeightPx(density, desired * 10))
        val shortSurface = 400
        assertEquals((shortSurface * 0.45f).toInt(), PlayerSurfacePolicy.edgePillHeightPx(density, shortSurface))
    }

    @Test
    fun `unchanged placement refreshes content in place`() {
        val placement = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 2f,
            insetStartPx = 20, surfaceHeightPx = 1600,
        )
        val same = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 2f,
            insetStartPx = 20, surfaceHeightPx = 1600,
        )
        assertFalse(PlayerSurfacePolicy.requiresCleanReposition(placement, same, isVisible = true))
    }

    @Test
    fun `placement change while visible requires a clean reposition`() {
        val brightness = PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.BRIGHTNESS, PlayerSurfaceClass.LARGE, 2f)
        val seek = PlayerSurfacePolicy.placementFor(PlayerGestureFeedbackKind.SEEK, PlayerSurfaceClass.LARGE, 2f)
        assertTrue(PlayerSurfacePolicy.requiresCleanReposition(brightness, seek, isVisible = true))
        // A pill already hidden (for example mid-fade-out) can move freely.
        assertFalse(PlayerSurfacePolicy.requiresCleanReposition(brightness, seek, isVisible = false))
        // A visible pill with no stored placement resets defensively.
        assertTrue(PlayerSurfacePolicy.requiresCleanReposition(null, seek, isVisible = true))
    }

    @Test
    fun `inset-only changes count as placement changes`() {
        val withoutInset = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.LARGE, 2f, surfaceHeightPx = 1600,
        )
        val withInset = PlayerSurfacePolicy.placementFor(
            PlayerGestureFeedbackKind.DEVICE_VOLUME, PlayerSurfaceClass.LARGE, 2f, insetEndPx = 40, surfaceHeightPx = 1600,
        )
        assertTrue(PlayerSurfacePolicy.requiresCleanReposition(withoutInset, withInset, isVisible = true))
    }
}

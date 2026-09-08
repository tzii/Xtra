package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPopupPolicyTest {

    @Test
    fun `compact width is capped with edge insets`() {
        assertEquals(672, PlayerPopupPolicy.panelWidthPx(surfaceWidthPx = 1000, density = 2f))
        assertEquals(536, PlayerPopupPolicy.panelWidthPx(surfaceWidthPx = 600, density = 2f))
    }

    @Test
    fun `large width is bounded instead of expanding with the surface`() {
        assertEquals(768, PlayerPopupPolicy.panelWidthPx(surfaceWidthPx = 2560, density = 2f))
    }

    @Test
    fun `popup prefers the space above its trigger`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1200,
            surfaceHeightPx = 800,
            measuredPanelHeightPx = 300,
            density = 2f,
            trigger = PlayerPopupPolicy.Rect(900, 700, 1000, 760),
        )

        assertEquals(232, placement.left)
        assertEquals(384, placement.top)
    }

    @Test
    fun `popup horizontal edge follows trigger side`() {
        val leftTrigger = PlayerPopupPolicy.place(
            surfaceWidthPx = 2000,
            surfaceHeightPx = 1000,
            measuredPanelHeightPx = 300,
            density = 2f,
            trigger = PlayerPopupPolicy.Rect(120, 700, 220, 760),
        )
        val rightTrigger = PlayerPopupPolicy.place(
            surfaceWidthPx = 2000,
            surfaceHeightPx = 1000,
            measuredPanelHeightPx = 300,
            density = 2f,
            trigger = PlayerPopupPolicy.Rect(1780, 700, 1880, 760),
        )

        assertEquals(120, leftTrigger.left)
        assertEquals(1112, rightTrigger.left)
    }

    @Test
    fun `popup moves below trigger when there is no room above`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1200,
            surfaceHeightPx = 800,
            measuredPanelHeightPx = 300,
            density = 2f,
            trigger = PlayerPopupPolicy.Rect(500, 40, 600, 100),
        )

        assertEquals(116, placement.top)
    }

    @Test
    fun `popup pins to bottom edge when neither side fits and trigger is low`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1080,
            surfaceHeightPx = 420,
            measuredPanelHeightPx = 300,
            density = 3f,
            trigger = PlayerPopupPolicy.Rect(48, 300, 144, 396),
        )

        assertEquals(72, placement.top)
    }

    @Test
    fun `popup pins to top edge when neither side fits and trigger is high`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1080,
            surfaceHeightPx = 420,
            measuredPanelHeightPx = 300,
            density = 3f,
            trigger = PlayerPopupPolicy.Rect(500, 60, 600, 120),
        )

        assertEquals(48, placement.top)
    }

    @Test
    fun `fallback follows end edge and mirrors in rtl`() {
        val ltr = PlayerPopupPolicy.place(1200, 800, 300, 2f)
        val rtl = PlayerPopupPolicy.place(1200, 800, 300, 2f, isRtl = true)

        assertEquals(384, ltr.left)
        assertEquals(48, rtl.left)
        assertEquals(452, ltr.top)
        assertEquals(ltr.top, rtl.top)
    }

    @Test
    fun `physical placement converts to the correct relative start margin`() {
        assertEquals(
            100,
            PlayerPopupPolicy.startMarginPx(1200, 100, 300, isRtl = false),
        )
        assertEquals(
            800,
            PlayerPopupPolicy.startMarginPx(1200, 100, 300, isRtl = true),
        )
    }

    @Test
    fun `system insets constrain width height and fallback position`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 500,
            surfaceHeightPx = 500,
            measuredPanelHeightPx = 900,
            density = 1f,
            insets = PlayerPopupPolicy.Insets(left = 200, top = 20, right = 200, bottom = 30),
        )

        assertEquals(216, placement.left)
        assertEquals(36, placement.top)
        assertEquals(68, placement.width)
        assertEquals(418, placement.maxHeight)
    }

    @Test
    fun `portrait card can extend below video and stay attached to top button`() {
        val placement = PlayerPopupPolicy.place(360, 800, 400, 1f,
            trigger = PlayerPopupPolicy.Rect(288, 16, 336, 64))
        assertEquals(72, placement.top)
        assertTrue(placement.top + 400 > 203) // 16:9 video ends here.
        assertEquals(328, placement.width)
    }

    @Test
    fun `long menu stays bounded without expanding its width`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1080,
            surfaceHeightPx = 600,
            measuredPanelHeightPx = 3000,
            density = 3f,
            trigger = PlayerPopupPolicy.Rect(900, 400, 1000, 500),
        )

        assertEquals(48, placement.left)
        assertEquals(48, placement.top)
        assertEquals(984, placement.width)
        assertEquals(504, placement.maxHeight)
    }

    @Test
    fun `side chat does not change the video popup width class`() {
        val placement = PlayerPopupPolicy.place(
            surfaceWidthPx = 1000,
            surfaceHeightPx = 600,
            measuredPanelHeightPx = 300,
            density = 1f,
            insets = PlayerPopupPolicy.Insets(right = 500),
            trigger = PlayerPopupPolicy.Rect(420, 16, 468, 64),
        )
        assertEquals(PlayerPopupPolicy.panelWidthPx(500, 1f), placement.width)
        assertEquals(72, placement.top)
        assertEquals(468, placement.left + placement.width)
    }
}

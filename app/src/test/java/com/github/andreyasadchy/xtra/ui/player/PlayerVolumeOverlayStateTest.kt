package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerVolumeOverlayStateTest {

    @Test
    fun `first mute restores the volume observed when the overlay opened`() {
        val state = PlayerVolumeOverlayState()

        state.remember(25)

        assertEquals(0, state.targetAfterToggle(25))
        assertEquals(25, state.targetAfterToggle(0))
    }

    @Test
    fun `slider changes replace the remembered non-zero volume`() {
        val state = PlayerVolumeOverlayState()

        state.remember(40)
        state.remember(65)

        assertEquals(0, state.targetAfterToggle(65))
        assertEquals(65, state.targetAfterToggle(0))
    }

    @Test
    fun `zero does not overwrite the remembered volume`() {
        val state = PlayerVolumeOverlayState(initialNonZeroPercent = 30)

        state.remember(0)

        assertEquals(30, state.targetAfterToggle(0))
    }
}

package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSpeedPopupStateTest {

    @Test
    fun `initialSpeed uses current player speed when available`() {
        assertEquals(1.25f, PlayerSpeedPopupState.initialSpeed(1.25f, 1f))
    }

    @Test
    fun `initialSpeed falls back to saved speed before player is ready`() {
        assertEquals(1.5f, PlayerSpeedPopupState.initialSpeed(null, 1.5f))
    }
}

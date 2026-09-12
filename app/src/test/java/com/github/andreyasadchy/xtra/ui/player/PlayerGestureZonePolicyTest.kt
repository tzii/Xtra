package com.github.andreyasadchy.xtra.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGestureZonePolicyTest {

    @Test
    fun `upper zone controls playback speed`() {
        assertEquals(
            PlayerHorizontalGestureZone.PLAYBACK_SPEED,
            PlayerGestureZonePolicy.horizontalZone(startY = 10f, surfaceHeightPx = 1000f, zoneSplit = 0.5f),
        )
    }

    @Test
    fun `lower zone seeks`() {
        assertEquals(
            PlayerHorizontalGestureZone.SEEK,
            PlayerGestureZonePolicy.horizontalZone(startY = 900f, surfaceHeightPx = 1000f, zoneSplit = 0.5f),
        )
    }

    @Test
    fun `start exactly on the split line belongs to the lower seek zone`() {
        assertEquals(
            PlayerHorizontalGestureZone.SEEK,
            PlayerGestureZonePolicy.horizontalZone(startY = 500f, surfaceHeightPx = 1000f, zoneSplit = 0.5f),
        )
    }

    @Test
    fun `configured zone split moves the boundary`() {
        assertEquals(
            PlayerHorizontalGestureZone.SEEK,
            PlayerGestureZonePolicy.horizontalZone(startY = 450f, surfaceHeightPx = 1000f, zoneSplit = 0.4f),
        )
        assertEquals(
            PlayerHorizontalGestureZone.PLAYBACK_SPEED,
            PlayerGestureZonePolicy.horizontalZone(startY = 550f, surfaceHeightPx = 1000f, zoneSplit = 0.6f),
        )
    }

    @Test
    fun `degenerate inputs fall back to the seek zone`() {
        assertEquals(
            PlayerHorizontalGestureZone.SEEK,
            PlayerGestureZonePolicy.horizontalZone(startY = 1f, surfaceHeightPx = 0f, zoneSplit = 0.5f),
        )
        assertEquals(
            PlayerHorizontalGestureZone.SEEK,
            PlayerGestureZonePolicy.horizontalZone(startY = 1f, surfaceHeightPx = 1000f, zoneSplit = Float.NaN),
        )
    }
}

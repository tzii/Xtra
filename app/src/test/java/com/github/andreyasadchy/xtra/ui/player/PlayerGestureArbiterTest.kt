package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.ui.player.PlayerGestureArbiter.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerGestureArbiterTest {

    private lateinit var arbiter: PlayerGestureArbiter

    @Before
    fun setUp() {
        arbiter = PlayerGestureArbiter(scaleClaimDeadzone = 0.02f)
    }

    private fun startSequence() {
        arbiter.onSequenceStarted()
    }

    private fun pinchClaim(): Boolean {
        val secondPointer = arbiter.onPointerAdded(2)
        assertTrue(secondPointer)
        return arbiter.onScaleUpdate(cumulativeScale = 1.1f)
    }

    @Test
    fun `second pointer creates candidate but claims nothing`() {
        startSequence()
        assertTrue(arbiter.onPointerAdded(2))
        assertEquals(Owner.IDLE, arbiter.owner)
        assertTrue(arbiter.isPinchCandidate)
    }

    @Test
    fun `third pointer cannot create a new candidate or action`() {
        startSequence()
        assertTrue(arbiter.onPointerAdded(2))
        assertFalse(arbiter.onPointerAdded(3))
        assertTrue(arbiter.isPinchCandidate)
    }

    @Test
    fun `merely placing two fingers never claims pinch`() {
        startSequence()
        arbiter.onPointerAdded(2)
        assertFalse(arbiter.onScaleUpdate(cumulativeScale = 1.0f))
        assertEquals(Owner.IDLE, arbiter.owner)
    }

    @Test
    fun `relative scale claims independently of absolute finger travel`() {
        startSequence()
        arbiter.onPointerAdded(2)
        assertTrue(arbiter.onScaleUpdate(cumulativeScale = 1.03f))
        assertEquals(Owner.PINCH_DISPLAY_MODE, arbiter.owner)
    }

    @Test
    fun `movement below scale deadzone does not claim pinch`() {
        startSequence()
        arbiter.onPointerAdded(2)
        assertFalse(arbiter.onScaleUpdate(cumulativeScale = 1.01f))
        assertEquals(Owner.IDLE, arbiter.owner)
    }

    @Test
    fun `crossing the scale deadzone claims pinch exactly once`() {
        startSequence()
        arbiter.onPointerAdded(2)
        assertTrue(arbiter.onScaleUpdate(cumulativeScale = 1.1f))
        assertEquals(Owner.PINCH_DISPLAY_MODE, arbiter.owner)
        assertFalse(arbiter.onScaleUpdate(cumulativeScale = 1.2f))
        assertEquals(Owner.PINCH_DISPLAY_MODE, arbiter.owner)
    }

    @Test
    fun `single finger claims each gesture kind when no candidate`() {
        for (owner in PlayerGestureArbiter.SINGLE_FINGER_OWNERS) {
            val fresh = PlayerGestureArbiter(0.02f)
            fresh.onSequenceStarted()
            assertTrue(fresh.tryClaimSingleFinger(owner))
            assertEquals(owner, fresh.owner)
        }
    }

    @Test
    fun `single finger claim denied while pinch candidate active`() {
        startSequence()
        arbiter.onPointerAdded(2)
        assertFalse(arbiter.tryClaimSingleFinger(Owner.BRIGHTNESS))
        assertEquals(Owner.IDLE, arbiter.owner)
    }

    @Test
    fun `second finger cannot convert an owned single-finger gesture`() {
        startSequence()
        assertTrue(arbiter.tryClaimSingleFinger(Owner.PLAYBACK_SPEED))
        assertFalse(arbiter.onPointerAdded(2))
        assertFalse(arbiter.isPinchCandidate)
        assertFalse(arbiter.onScaleUpdate(cumulativeScale = 1.1f))
        assertEquals(Owner.PLAYBACK_SPEED, arbiter.owner)
    }

    @Test
    fun `second owner claim denied while an owner holds the sequence`() {
        startSequence()
        assertTrue(arbiter.tryClaimSingleFinger(Owner.BRIGHTNESS))
        assertFalse(arbiter.tryClaimSingleFinger(Owner.SEEK))
    }

    @Test
    fun `lifting a finger dissolves an unclaimed candidate and restores single-finger eligibility`() {
        startSequence()
        arbiter.onPointerAdded(2)
        assertFalse(arbiter.onPointerRemoved(1))
        assertFalse(arbiter.isPinchCandidate)
        assertTrue(arbiter.tryClaimSingleFinger(Owner.SEEK))
    }

    @Test
    fun `lifting one finger after pinch owns suppresses single-finger events`() {
        assertTrue(pinchClaim())
        assertTrue(arbiter.onPointerRemoved(1))
        assertTrue(arbiter.isSingleFingerSuppressed)
        assertFalse(arbiter.tryClaimSingleFinger(Owner.BRIGHTNESS))
        assertEquals(Owner.PINCH_DISPLAY_MODE, arbiter.owner)
    }

    @Test
    fun `adding a finger after suppression does not resume candidacy`() {
        assertTrue(pinchClaim())
        arbiter.onPointerRemoved(1)
        assertFalse(arbiter.onPointerAdded(2))
        assertTrue(arbiter.isSingleFingerSuppressed)
    }

    @Test
    fun `double tap claims chat when sequence is otherwise idle`() {
        startSequence()
        assertTrue(arbiter.onDoubleTapClaimed())
        assertEquals(Owner.DOUBLE_TAP_CHAT, arbiter.owner)
    }

    @Test
    fun `double tap denied while pinch candidate exists`() {
        startSequence()
        arbiter.onPointerAdded(2)
        assertFalse(arbiter.onDoubleTapClaimed())
    }

    @Test
    fun `pinch may supersede an already claimed double tap`() {
        startSequence()
        assertTrue(arbiter.onDoubleTapClaimed())
        assertTrue(arbiter.onPointerAdded(2))
        assertTrue(arbiter.onScaleUpdate(cumulativeScale = 1.1f))
        assertEquals(Owner.PINCH_DISPLAY_MODE, arbiter.owner)
    }

    @Test
    fun `double tap denied after a single-finger gesture owns the sequence`() {
        startSequence()
        assertTrue(arbiter.tryClaimSingleFinger(Owner.SEEK))
        assertFalse(arbiter.onDoubleTapClaimed())
    }

    @Test
    fun `invalid single-finger claim kinds are denied`() {
        startSequence()
        assertFalse(arbiter.tryClaimSingleFinger(Owner.IDLE))
        assertFalse(arbiter.tryClaimSingleFinger(Owner.PINCH_DISPLAY_MODE))
        assertFalse(arbiter.tryClaimSingleFinger(Owner.DOUBLE_TAP_CHAT))
    }

    @Test
    fun `sequence finish resets everything`() {
        assertTrue(pinchClaim())
        arbiter.onPointerRemoved(1)
        arbiter.onSequenceFinished()
        assertEquals(Owner.IDLE, arbiter.owner)
        assertFalse(arbiter.isPinchCandidate)
        assertFalse(arbiter.isSingleFingerSuppressed)
    }

    @Test
    fun `removing a third pointer does not finish an owned two-finger pinch`() {
        assertTrue(pinchClaim())
        assertFalse(arbiter.onPointerRemoved(2))
        assertEquals(Owner.PINCH_DISPLAY_MODE, arbiter.owner)
        assertFalse(arbiter.isSingleFingerSuppressed)
    }

    @Test
    fun `new sequence starts clean even after pinch`() {
        assertTrue(pinchClaim())
        startSequence()
        assertEquals(Owner.IDLE, arbiter.owner)
        assertFalse(arbiter.isPinchCandidate)
        assertTrue(arbiter.tryClaimSingleFinger(Owner.DEVICE_VOLUME))
    }
}

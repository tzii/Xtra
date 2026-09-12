package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.abs

/**
 * Single owner for every player touch sequence. Pointer lifecycle events must
 * be fed to the arbiter before the single-finger gesture detector decides an
 * unclaimed action.
 *
 * Rules encoded here:
 *  - A second pointer creates a pinch candidate before an unclaimed
 *    single-finger gesture can win; merely placing two fingers claims nothing.
 *  - Pinch claims the sequence after the configured cumulative relative-scale
 *    deadzone is crossed; no fixed pixel gate makes sensitivity depend on the
 *    fingers' initial distance.
 *  - If a single-finger swipe (seek, playback speed, brightness, device
 *    volume) already owns the sequence, a second finger cannot convert it.
 *  - Once pinch owns the sequence, lifting one finger suppresses remaining
 *    single-finger events until the final release or cancellation.
 *  - A third finger cannot start another player action.
 *  - A pinch may supersede a just-claimed double-tap (the wiring layer
 *    compensates by reverting the chat toggle) so tap-then-pinch does not
 *    accidentally toggle chat.
 */
class PlayerGestureArbiter(
    private val scaleClaimDeadzone: Float,
) {

    enum class Owner {
        IDLE,
        SEEK,
        PLAYBACK_SPEED,
        BRIGHTNESS,
        DEVICE_VOLUME,
        DOUBLE_TAP_CHAT,
        PINCH_DISPLAY_MODE,
    }

    var owner: Owner = Owner.IDLE
        private set

    /** Two pointers are present and pinch may still claim the sequence. */
    var isPinchCandidate: Boolean = false
        private set

    /** Pinch owned the sequence and a finger lifted; remaining single-finger events are suppressed. */
    var isSingleFingerSuppressed: Boolean = false
        private set

    /** Called on ACTION_DOWN of a new sequence. */
    fun onSequenceStarted() {
        owner = Owner.IDLE
        isPinchCandidate = false
        isSingleFingerSuppressed = false
    }

    /**
     * Called on ACTION_POINTER_DOWN. Returns true when a pinch candidate was
     * created. A third or later pointer never creates or restores a candidate.
     */
    fun onPointerAdded(activePointerCount: Int): Boolean {
        if (activePointerCount != 2) return false
        if (owner != Owner.IDLE && owner != Owner.DOUBLE_TAP_CHAT) return false
        isPinchCandidate = true
        return true
    }

    /**
     * Called on ACTION_POINTER_UP with the number of pointers that remain
     * after the lift. A candidate dissolves back to a single-finger sequence;
     * an owned pinch starts suppressing single-finger events. Returns true
     * exactly when the owned two-finger pinch has ended and its caller should
     * commit or restore immediately.
     */
    fun onPointerRemoved(remainingPointerCount: Int): Boolean {
        val pinchEnded = owner == Owner.PINCH_DISPLAY_MODE && remainingPointerCount < 2
        if (remainingPointerCount < 2) {
            if (isPinchCandidate && owner == Owner.IDLE) {
                isPinchCandidate = false
            }
            if (owner == Owner.PINCH_DISPLAY_MODE) {
                isSingleFingerSuppressed = true
            }
        }
        return pinchEnded
    }

    /**
     * Cumulative two-finger scale update while a candidate exists. Claiming is
     * relative-scale based so sensitivity does not change with the fingers'
     * initial distance. Returns true at the single moment pinch claims.
     */
    fun onScaleUpdate(cumulativeScale: Float): Boolean {
        if (!isPinchCandidate) return false
        if (owner == Owner.PINCH_DISPLAY_MODE) return false
        if (abs(cumulativeScale - 1f) < scaleClaimDeadzone) return false
        owner = Owner.PINCH_DISPLAY_MODE
        return true
    }

    /**
     * The single-finger detector asks to claim the sequence. Denied while a
     * pinch candidate exists, while another owner holds the sequence, or while
     * events are suppressed after an owned pinch.
     */
    fun tryClaimSingleFinger(candidate: Owner): Boolean {
        if (candidate !in SINGLE_FINGER_OWNERS) return false
        if (owner != Owner.IDLE) return false
        if (isPinchCandidate) return false
        if (isSingleFingerSuppressed) return false
        owner = candidate
        return true
    }

    /**
     * The double-tap detector claimed the chat toggle. Denied when a pinch
     * candidate exists (two fingers already down).
     */
    fun onDoubleTapClaimed(): Boolean {
        if (owner != Owner.IDLE || isPinchCandidate) return false
        owner = Owner.DOUBLE_TAP_CHAT
        return true
    }

    /** Final release or cancellation of the whole sequence. */
    fun onSequenceFinished() {
        owner = Owner.IDLE
        isPinchCandidate = false
        isSingleFingerSuppressed = false
    }

    companion object {
        val SINGLE_FINGER_OWNERS = setOf(Owner.SEEK, Owner.PLAYBACK_SPEED, Owner.BRIGHTNESS, Owner.DEVICE_VOLUME)
    }
}

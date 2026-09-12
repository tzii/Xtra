package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.media.AudioManager
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.github.andreyasadchy.xtra.R
import kotlin.math.abs

interface PlayerGestureCallback {
    val isPortrait: Boolean
    val isMaximized: Boolean
    val isControlsVisible: Boolean
    val controlsVisibleAtGestureStart: Boolean
    val playerWidth: Int
    val playerHeight: Int
    val windowAttributes: android.view.WindowManager.LayoutParams
    val isEdgeSwipe: Boolean
    val playerGestureInsets: androidx.core.graphics.Insets?
    
    fun setWindowAttributes(params: android.view.WindowManager.LayoutParams)
    fun showController()
    fun hideController()
    fun updateProgress()
    fun cycleChatMode()
    fun getGestureFeedbackView(): View
    fun getHideGestureRunnable(): Runnable
    fun isControllerHideOnTouch(): Boolean

    // New methods for split-screen gestures
    fun getPlayerVideoType(): String?
    fun getCurrentPosition(): Long?
    fun getDuration(): Long
    fun seek(position: Long)
    fun setPlaybackSpeed(speed: Float)
    fun getCurrentSpeed(): Float?
    
    // Notify when gesture detector has claimed a swipe gesture
    fun onSwipeGestureStarted()
    fun onSwipeGestureEnded()

    // Gesture arbitration: the single owner of the touch sequence
    fun claimSingleFingerGesture(owner: PlayerGestureArbiter.Owner): Boolean
    fun claimDoubleTapChat(): Boolean
}

class PlayerGestureListener(
    private val context: Context,
    private val callback: PlayerGestureCallback,
    private val doubleTapEnabled: Boolean,
    private val gesturesEnabled: Boolean = true,
    private val hapticEnabled: Boolean = false,
    private val sensitivity: Float = 1.0f, // Multiplier: 0.5f, 1.0f, 2.0f
    private val zoneSplit: Float = 0.5f    // Top zone ratio: 0.4f, 0.5f, 0.6f
) : GestureDetector.SimpleOnGestureListener() {

    private val helper = PlayerGestureHelper(context)
    
    // State machine flags to track which gesture mode we are currently in
    private var isVolume = false
    private var isBrightness = false
    private var isSeek = false
    private var isSpeed = false
    
    // Flag to ensure we only notify the callback once per gesture
    private var hasNotifiedGestureStart = false
    
    // General flag to indicate any scroll gesture is active (prevents taps)
    private var isScrolling = false 
    
    // Initial values captured at the start of the gesture (ACTION_DOWN)
    private var startVolume = 0
    private var startBrightness = 0f
    private var startPosition = 0L
    private var startSpeed = 1f
    private var gestureStartY = 0f
    private var gestureStartX = 0f
    private var duration = 0L

    override fun onDown(e: MotionEvent): Boolean {
        // End any previous gesture cleanup if needed
        if (hasNotifiedGestureStart) {
            callback.onSwipeGestureEnded()
            hasNotifiedGestureStart = false
        }
        
        // Reset all state flags for the new gesture sequence
        isVolume = false
        isBrightness = false
        isSeek = false
        isSpeed = false
        isScrolling = false
        
        // Capture start coordinates
        gestureStartY = e.y
        gestureStartX = e.x
        return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        // Master toggle check
        if (!gesturesEnabled) return false

        // Block all gestures when:
        // - touch started in edge zone (system gesture area)
        // - controls were visible when gesture started (use controlsVisibleAtGestureStart to lock this for entire gesture)
        // - player is in portrait or minimized mode
        if (e1 == null || callback.isPortrait || !callback.isMaximized || callback.isEdgeSwipe || callback.controlsVisibleAtGestureStart) return false
        
        val width = callback.playerWidth.toFloat()
        val height = callback.playerHeight.toFloat()
        
        // If we haven't locked onto a specific gesture type yet, determine it now
        if (!isVolume && !isBrightness && !isSeek && !isSpeed) {
             if (abs(distanceY) > abs(distanceX)) {
                 // Vertical Swipes (Volume / Brightness)
                 // Split left/right halves
                 if (e1.x < width / 2) {
                     isBrightness = true
                     startBrightness = helper.getCurrentBrightness(callback.windowAttributes)
                 } else {
                     isVolume = true
                     val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                     startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                 }
             } else {
                 // Horizontal Swipes (seekable media only: Speed / Seek)
                 if (callback.getPlayerVideoType() != PlayerFragment.STREAM) {
                     // Split top/bottom using the configured zone split ratio
                     when (PlayerGestureZonePolicy.horizontalZone(e1.y, height, zoneSplit)) {
                         PlayerHorizontalGestureZone.PLAYBACK_SPEED -> {
                             // Upper zone -> Playback speed
                             isSpeed = true
                             startSpeed = callback.getCurrentSpeed() ?: 1f
                         }
                         PlayerHorizontalGestureZone.SEEK -> {
                             // Lower zone -> Seek
                             isSeek = true
                             startPosition = callback.getCurrentPosition() ?: 0L
                             duration = callback.getDuration()
                         }
                     }
                 }
             }
             
             // Notify that we've claimed this gesture (prevents minimize gesture from triggering)
             if (isVolume || isBrightness || isSeek || isSpeed) {
                val owner = when {
                    isBrightness -> PlayerGestureArbiter.Owner.BRIGHTNESS
                    isVolume -> PlayerGestureArbiter.Owner.DEVICE_VOLUME
                    isSeek -> PlayerGestureArbiter.Owner.SEEK
                    else -> PlayerGestureArbiter.Owner.PLAYBACK_SPEED
                }
                if (!callback.claimSingleFingerGesture(owner)) {
                    // A pinch candidate owns this sequence; abandon the claim.
                    isVolume = false
                    isBrightness = false
                    isSeek = false
                    isSpeed = false
                    return false
                }
                isScrolling = true  // Mark that we're in a scroll gesture
                if (!hasNotifiedGestureStart) {
                    callback.onSwipeGestureStarted()
                    performHapticFeedback() // Feedback on gesture start
                    hasNotifiedGestureStart = true
                }
            }
        }

        val percentY = (gestureStartY - e2.y) / height
        val percentX = (e2.x - gestureStartX) / width // Left to Right is positive

        if (isBrightness) {
            val rawBrightness = startBrightness + percentY
            val isAuto = rawBrightness < 0.05f
            val newBrightness = if (isAuto) -1f else rawBrightness.coerceIn(0.05f, 1.0f)

            val lp = callback.windowAttributes
            lp.screenBrightness = newBrightness
            callback.setWindowAttributes(lp)

            val percent = if (isAuto) 0 else (newBrightness * 100).toInt()
            val valueText = if (isAuto) "Auto" else "%d%%".format(percent)
            presentFeedback(
                kind = PlayerGestureFeedbackKind.BRIGHTNESS,
                iconRes = R.drawable.ic_brightness_medium_black_24dp,
                level = percent,
                visibleText = valueText,
                a11yText = if (isAuto) {
                    context.getString(R.string.gesture_feedback_auto_brightness)
                } else {
                    labeledA11y(PlayerGestureFeedbackKind.BRIGHTNESS, valueText)
                },
            )
            return true
        }

        if (isVolume) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val newVolume = (startVolume + (percentY * maxVolume)).toInt().coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

            val percent = ((newVolume.toFloat() / maxVolume.toFloat()) * 100).toInt()
            presentFeedback(
                kind = PlayerGestureFeedbackKind.DEVICE_VOLUME,
                iconRes = if (newVolume == 0) R.drawable.baseline_volume_off_black_24 else R.drawable.baseline_volume_up_black_24,
                level = percent,
                visibleText = "%d".format(percent),
                a11yText = labeledA11y(
                    PlayerGestureFeedbackKind.DEVICE_VOLUME,
                    if (newVolume == 0) context.getString(R.string.gesture_feedback_muted) else "%d%%".format(percent),
                ),
            )
            return true
        }

        if (isSeek) {
            if (duration > 0) {
                val newPosition = helper.calculateResponsiveSeekPosition(
                    currentPosition = startPosition,
                    duration = duration,
                    gestureDelta = e2.x - gestureStartX,
                    screenWidth = callback.playerWidth,
                    sensitivity = sensitivity
                )
                val seekAmount = newPosition - startPosition
                callback.seek(newPosition)

                val valueText = "${helper.formatDuration(newPosition)} / ${helper.formatDuration(duration)}"
                presentFeedback(
                    kind = PlayerGestureFeedbackKind.SEEK,
                    iconRes = if (seekAmount > 0) R.drawable.baseline_add_black_24 else R.drawable.baseline_remove_black_24,
                    level = null,
                    visibleText = labeledText(PlayerGestureFeedbackKind.SEEK, valueText),
                    a11yText = labeledA11y(PlayerGestureFeedbackKind.SEEK, valueText),
                )
            }
            return true
        }

        if (isSpeed) {
            // Speed logic: 0.05x increments
            // Swipe full width = 1.0x change, adjusted by sensitivity
            val speedChange = (percentX * 2.0f * sensitivity)
            // Round to nearest 0.05
            var newSpeed = startSpeed + speedChange
            newSpeed = (Math.round(newSpeed * 20) / 20.0f).coerceIn(0.25f, 4.0f)

            if (newSpeed != callback.getCurrentSpeed()) {
                callback.setPlaybackSpeed(newSpeed)
            }

            val valueText = "%.2fx".format(newSpeed)
            presentFeedback(
                kind = PlayerGestureFeedbackKind.PLAYBACK_SPEED,
                iconRes = R.drawable.baseline_speed_black_24,
                level = null,
                visibleText = labeledText(PlayerGestureFeedbackKind.PLAYBACK_SPEED, valueText),
                a11yText = labeledA11y(PlayerGestureFeedbackKind.PLAYBACK_SPEED, valueText),
            )
            return true
        }

        return false
    }

    private fun surfaceClass(): PlayerSurfaceClass {
        return PlayerSurfacePolicy.classify(callback.playerWidth, context.resources.displayMetrics.density)
    }

    private fun labelRes(kind: PlayerGestureFeedbackKind): Int {
        return when (kind) {
            PlayerGestureFeedbackKind.BRIGHTNESS -> R.string.gesture_feedback_brightness
            PlayerGestureFeedbackKind.DEVICE_VOLUME -> R.string.gesture_feedback_device_volume
            PlayerGestureFeedbackKind.SEEK -> R.string.gesture_feedback_seek
            PlayerGestureFeedbackKind.PLAYBACK_SPEED -> R.string.gesture_feedback_playback_speed
            PlayerGestureFeedbackKind.PINCH -> R.string.gesture_feedback_pinch
        }
    }

    private fun labeledText(kind: PlayerGestureFeedbackKind, value: String): String {
        return if (surfaceClass() == PlayerSurfaceClass.LARGE) {
            context.getString(labelRes(kind)) + " \u00B7 " + value
        } else {
            value
        }
    }

    /**
     * Accessibility text always carries the control name; visible text on
     * large surfaces does too, while the vertical edge pill drops visible text
     * entirely and relies on this description.
     */
    private fun labeledA11y(kind: PlayerGestureFeedbackKind, value: String): String {
        return context.getString(labelRes(kind)) + " \u00B7 " + value
    }

    private fun presentFeedback(
        kind: PlayerGestureFeedbackKind,
        iconRes: Int,
        level: Int?,
        visibleText: String,
        a11yText: String,
    ) {
        PlayerSurfacePolicy.presentFeedback(
            context = context,
            feedbackRoot = callback.getGestureFeedbackView(),
            kind = kind,
            surfaceWidthPx = callback.playerWidth,
            surfaceHeightPx = callback.playerHeight,
            insets = callback.playerGestureInsets,
            presentation = PlayerGestureFeedbackState.presentation(
                kind = kind,
                surfaceClass = surfaceClass(),
                level = level,
                text = visibleText,
            ),
            iconRes = iconRes,
            a11yText = a11yText,
            hideRunnable = callback.getHideGestureRunnable(),
        )
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        // Don't trigger tap if a scroll gesture occurred
        if (isScrolling) return false
        
        return if (!doubleTapEnabled || callback.isPortrait) {
            handleSingleTap()
            true
        } else {
            false
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        // Don't trigger tap if a scroll gesture occurred
        if (isScrolling) return false
        
        return if (doubleTapEnabled && !callback.isPortrait) {
            handleSingleTap()
            true
        } else {
            false
        }
    }

    private fun handleSingleTap() {
        val visible = callback.isControlsVisible
        if (visible) {
            if (callback.isControllerHideOnTouch()) {
                callback.hideController()
            }
        } else {
            callback.showController()
        }
        if (!visible) {
            callback.updateProgress()
        }
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        return if (doubleTapEnabled && !callback.isPortrait && callback.isMaximized) {
            if (callback.claimDoubleTapChat()) {
                callback.cycleChatMode()
                performHapticFeedback() // Feedback for double tap action
            }
            true
        } else {
            false
        }
    }

    private fun performHapticFeedback() {
        if (hapticEnabled) {
            // Try to use the feedback view to perform haptic feedback
            try {
                val view = callback.getGestureFeedbackView()
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            } catch (e: Exception) {
                // Ignore if view not available or haptics failed
            }
        }
    }
}

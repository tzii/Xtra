package com.github.andreyasadchy.xtra.ui.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.trackPipAnimationHintView
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.TimeBar
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerMorePopupBinding
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerQualityPopupBinding
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerSpeedPopupBinding
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerVolumeOverlayBinding
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.RadioButtonDialogFragment
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.isKeyboardShown
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import java.util.Locale

@OptIn(UnstableApi::class)
@AndroidEntryPoint
abstract class PlayerFragment : BaseNetworkFragment(), RadioButtonDialogFragment.OnSortOptionChanged, IntegrityDialog.CallbackListener, PlayerGestureCallback {

    private var _binding: FragmentPlayerBinding? = null
    private val hideGestureRunnable = Runnable {
        _binding?.playerLayout?.findViewById<View>(R.id.gestureFeedback)?.let { feedback ->
            feedback.animate().cancel()
            if (feedback.isVisible) {
                feedback.animate()
                    .alpha(0f)
                    .setDuration(PlayerSurfacePolicy.FEEDBACK_FADE_MS)
                    .withEndAction {
                        feedback.visibility = View.GONE
                        feedback.alpha = 1f
                        PlayerSurfacePolicy.resetFeedback(feedback)
                    }
                    .start()
            } else {
                PlayerSurfacePolicy.resetFeedback(feedback)
            }
        }
    }
    protected val binding get() = _binding!!
    protected val viewModel: PlayerViewModel by viewModels()
    protected var chatFragment: ChatFragment? = null
    protected val prefs get() = requireContext().prefs()

    protected var videoType: String? = null
    override var isPortrait = false
    override var isMaximized = true
    private var isChatOpen = true
    private var isKeyboardShown = false
    protected var displayMode = PlayerDisplayMode.FIT
    private lateinit var displayModeStore: PlayerDisplayModeStore
    protected var videoAspectRatio = 0f
    private var chatWidthLandscape = 0

    private var activePointerId = -1
    private var lastX = 0f
    private var lastY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var isTap = false
    private var tapEventTime = 0L
    private var startTranslationX = 0f
    private var startTranslationY = 0f
    private var statusBarSwipe = false
    override var isEdgeSwipe = false
    private var gestureInsets: androidx.core.graphics.Insets? = null
    private var chatStatusBarSwipe = false
    private var isAnimating = false
    private var moveAnimation: ViewPropertyAnimator? = null
    protected var useController = true
    protected var controllerAutoHide = true
    private var controllerHideOnTouch = true
    private val controllerHideAction = Runnable {
        if (view != null && activePlayerPopup == null) hideController()
    }
    private var controllerIsAnimating = false
    private var controllerAnimation: ViewPropertyAnimator? = null
    private var backgroundColor: Int? = null
    private var backgroundVisible = false
    private val brightnessState = PlayerBrightnessState()
    
    // Gesture conflict prevention: track state at gesture start
    override var controlsVisibleAtGestureStart = false
    private var isSwipeGestureInProgress = false

    // Gesture arbitration and pinch display-mode control
    private var controllerTapDetector: GestureDetector? = null
    private lateinit var gestureArbiter: PlayerGestureArbiter
    private val pinchController = PinchDisplayModeController()
    private var pinchPointerId1 = -1
    private var pinchPointerId2 = -1
    private var pinchAnchorSpan = 0f
    private var pinchLastArmedTarget: PlayerDisplayMode? = null
    private var pinchSettleAnimator: ViewPropertyAnimator? = null
    private var pinchCommitGeneration = 0

    // Stream volume popup state (ThystTV playback volume, independent of device volume)
    private val volumeOverlayState = PlayerVolumeOverlayState()

    // One player-owned popup host replaces the former mixed dialog/overlay ownership.
    private var activePlayerPopup: PlayerPopupType? = null
    private var activePopupTrigger: View? = null
    // First valid trigger rect of the open popup; frozen for its lifetime.
    private var popupAnchorRect: PlayerPopupPolicy.Rect? = null
    private var activeSpeedPopupBinder: PlayerSpeedPopupBinder? = null
    private var activeQualityPopupBinder: PlayerQualityPopupBinder? = null
    private var activeVolumePopupBinder: PlayerVolumePopupBinder? = null
    private var activeMorePopupBinder: PlayerMorePopupBinder? = null
    private var activePopupLayoutListener: View.OnLayoutChangeListener? = null
    private var activePopupTriggerLayoutListener: View.OnLayoutChangeListener? = null
    private var popupGeneration = 0
    private var popupBackgroundAccessibility = emptyList<Pair<View, Int>>()

    // Gesture education
    private var gestureGuideShownThisSession = false

    // Floating Chat Properties
    private var isFloatingChatEnabled = false
    private var dX = 0f
    private var dY = 0f
    private var initialWidth = 0
    private var initialHeight = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isResizing = false

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (activePlayerPopup != null) {
                hidePlayerPopup()
            } else {
                minimize()
            }
        }
    }

    open fun startStream(url: String?) {}
    open fun startVideo(url: String?, playbackPosition: Long?, multivariantPlaylist: Boolean) {}
    open fun startClip(url: String?) {}
    open fun startOfflineVideo(url: String?, position: Long) {}
    override fun getPlayerVideoType() = videoType
    override fun getCurrentPosition(): Long? = null
    override fun getDuration(): Long = 0L
    override fun getCurrentSpeed(): Float? = null
    open fun getCurrentVolume(): Float? = null
    open fun playPause() {}
    open fun rewind() {}
    open fun fastForward() {}
    override fun seek(position: Long) {}
    open fun seekToLivePosition() {}
    override fun setPlaybackSpeed(speed: Float) {}
    open fun changeVolume(volume: Float) {}
    override fun updateProgress() {}
    open fun toggleAudioCompressor() {}
    open fun setSubtitlesButton() {}
    open fun toggleSubtitles(enabled: Boolean) {}
    open fun showPlaylistTags(mediaPlaylist: Boolean) {}
    open fun changeQuality(selectedQuality: VideoQuality?) {}
    open fun startAudioOnly() {}
    open fun downloadVideo() {}
    open fun close() {}

    protected fun updateMorePopupSubtitles(subtitles: Tracks.Group?) {
        activeMorePopupBinder?.setSubtitles(subtitles)
    }

    protected fun formatPlaybackSpeed(speed: Float?): String? {
        if (speed == null) return null
        val rounded = ((speed * 100).toInt() / 100f)
        return if (rounded % 1f == 0f) {
            String.format(Locale.US, "%.0fx", rounded)
        } else if ((rounded * 10f) % 1f == 0f) {
            String.format(Locale.US, "%.1fx", rounded)
        } else {
            String.format(Locale.US, "%.2fx", rounded)
        }
    }

    protected fun updatePlaybackSpeedUi(playbackSpeed: Float? = getCurrentSpeed() ?: requireContext().prefs().getFloat(C.PLAYER_SPEED, 1f)) {
        val formattedSpeed = formatPlaybackSpeed(playbackSpeed) ?: return
        with(binding.playerControls) {
            speed.text = formattedSpeed
            speed.contentDescription = getString(R.string.playback_speed) + ": " + formattedSpeed
        }
        activeMorePopupBinder?.setSpeed(formattedSpeed)
    }

    private fun updateQuickPlayerControls() {
        with(binding.playerControls) {
            if (requireContext().prefs().getBoolean(C.PLAYER_SETTINGS, true)) {
                // Large/windowed surfaces expose quality as text (e.g. 1080p);
                // compact surfaces keep the settings icon.
                val label = currentQualityLabel()
                val useTextControl = label != null &&
                        PlayerSurfacePolicy.classify(
                            binding.playerLayout.width,
                            resources.displayMetrics.density,
                        ) == PlayerSurfaceClass.LARGE
                if (useTextControl) {
                    quality.visibility = View.GONE
                    qualityValue.visibility = View.VISIBLE
                    qualityValue.text = label
                    qualityValue.setOnClickListener { showQualityDialog() }
                } else {
                    qualityValue.visibility = View.GONE
                    quality.visibility = View.VISIBLE
                    quality.setOnClickListener { showQualityDialog() }
                }
            } else {
                quality.visibility = View.GONE
                qualityValue.visibility = View.GONE
            }

            if (videoType != STREAM && requireContext().prefs().getBoolean(C.PLAYER_SPEEDBUTTON, true)) {
                speed.visibility = View.VISIBLE
                updatePlaybackSpeedUi()
                speed.setOnClickListener { showSpeedDialog() }
            } else {
                speed.visibility = View.GONE
            }
        }
    }

    private fun currentQualityLabel(): String? {
        return getQualityMap()?.entries?.find { it.value == viewModel.quality }?.key
    }

    private fun applyMinimizedPlayerVisualState() {
        cancelPinchSettle()
        binding.aspectRatioFrameLayout.scaleX = 1f
        binding.aspectRatioFrameLayout.scaleY = 1f
        binding.aspectRatioFrameLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        binding.playerLayout.setBackgroundColor(
            MaterialColors.getColor(binding.playerLayout, com.google.android.material.R.attr.colorSurface)
        )
    }

    private fun applyMaximizedPlayerVisualState() {
        cancelPinchSettle()
        binding.aspectRatioFrameLayout.scaleX = 1f
        binding.aspectRatioFrameLayout.scaleY = 1f
        binding.aspectRatioFrameLayout.resizeMode = if (isPortrait) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        } else {
            displayMode.resizeMode
        }
        binding.playerLayout.setBackgroundColor(Color.BLACK)
    }

    /**
     * Canonical setter for the non-portrait maximized display mode. Portrait
     * maximized playback and the mini-player always render Fit and never
     * mutate this state.
     */
    fun selectDisplayMode(mode: PlayerDisplayMode) {
        displayMode = mode
        displayModeStore.saveDisplayMode(mode)
        finalizePinchSurface()
    }

    /**
     * Commits an armed preview without exposing the asynchronous resize-mode
     * layout. The old renderer plus its completed preview scale already has
     * the target geometry, so retain that scale until the target renderer has
     * laid out, then normalize to unit scale before draw.
     */
    private fun commitPinchDisplayMode(mode: PlayerDisplayMode) {
        displayMode = mode
        displayModeStore.saveDisplayMode(mode)
        cancelPinchSettle()
        val frame = _binding?.aspectRatioFrameLayout ?: return
        val generation = ++pinchCommitGeneration
        frame.resizeMode = mode.resizeMode
        frame.doOnLayout {
            if (_binding?.aspectRatioFrameLayout === frame &&
                pinchCommitGeneration == generation &&
                displayMode == mode
            ) {
                frame.scaleX = 1f
                frame.scaleY = 1f
            }
        }
    }

    fun getCurrentDisplayMode(): PlayerDisplayMode = displayMode

    open fun updateVideoAspectRatio(aspectRatio: Float) {
        videoAspectRatio = aspectRatio
        binding.aspectRatioFrameLayout.setAspectRatio(aspectRatio)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        videoType = requireArguments().getString(KEY_TYPE)
        if (videoType == OFFLINE_VIDEO) {
            enableNetworkCheck = false
        }
        super.onCreate(savedInstanceState)
        isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
        WindowCompat.getInsetsController(
            requireActivity().window,
            requireActivity().window.decorView
        ).systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.integrity.collectLatest {
                        if (it != null &&
                            it != "done" &&
                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) &&
                            requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
                        ) {
                            IntegrityDialog.show(childFragmentManager, it)
                            viewModel.integrity.value = "done"
                        }
                    }
                }
            }
            val ignoreCutouts = requireContext().prefs().getBoolean(C.UI_DRAW_BEHIND_CUTOUTS, false)
            val cornerPadding = requireContext().prefs().getBoolean(C.PLAYER_ROUNDED_CORNER_PADDING, false)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = if (!isPortrait && ignoreCutouts) {
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                } else {
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.displayCutout())
                }
                if (isPortrait) {
                    slidingLayout.updatePadding(left = 0, top = insets.top, right = 0)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cornerPadding) {
                        val rootWindowInsets = view.rootView.rootWindowInsets
                        val topLeft = rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                        val topRight = rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                        val bottomLeft = rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                        val bottomRight = rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
                        val leftRadius = max(topLeft?.radius ?: 0, bottomLeft?.radius ?: 0)
                        val rightRadius = max(topRight?.radius ?: 0, bottomRight?.radius ?: 0)
                        if (ignoreCutouts) {
                            slidingLayout.updatePadding(left = leftRadius, top = 0, right = rightRadius)
                        } else {
                            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                            slidingLayout.updatePadding(left = max(cutoutInsets.left, leftRadius), top = 0, right = max(cutoutInsets.right, rightRadius))
                        }
                    } else {
                        if (ignoreCutouts) {
                            slidingLayout.updatePadding(left = 0, top = 0, right = 0)
                        } else {
                            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                            slidingLayout.updatePadding(left = cutoutInsets.left, top = 0, right = cutoutInsets.right)
                        }
                    }
                }
                chatLayout.updatePadding(bottom = insets.bottom)
                // Capture system gesture insets for edge detection
                gestureInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
                WindowInsetsCompat.CONSUMED
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        requireActivity().trackPipAnimationHintView(playerLayout)
                    }
                }
            }
            if (requireContext().prefs().getBoolean(C.PLAYER_KEEP_SCREEN_ON_WHEN_PAUSED, false)) {
                view.keepScreenOn = true
            }
            if (isMaximized) {
                enableBackground()
            } else {
                disableBackground()
            }
            isChatOpen = requireContext().prefs().getBoolean(C.KEY_CHAT_OPENED, true) && !requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)
            chatWidthLandscape = requireContext().prefs().getInt(C.LANDSCAPE_CHAT_WIDTH, 0)
            displayModeStore = PlayerDisplayModeStore(SharedPreferencesDisplayModeStorage(prefs))
            displayMode = displayModeStore.loadDisplayMode()
            aspectRatioFrameLayout.setAspectRatio(16f / 9f)
            initLayout()
            playerLayout.doOnLayout { updateQuickPlayerControls() }
            playerLayout.post { maybeShowGestureGuide() }
            changePlayerMode()
            val viewConfiguration = ViewConfiguration.get(requireContext())
            val touchSlop = viewConfiguration.scaledTouchSlop
            val touchSlopRange = -touchSlop.toFloat()..touchSlop.toFloat()
            val longPressTimeout = ViewConfiguration.getLongPressTimeout()
            val moveFreely = prefs.getBoolean(C.PLAYER_MOVE_FREELY, false)
            val doubleTap = prefs.getBoolean(C.PLAYER_DOUBLETAP, true) && !prefs.getBoolean(C.CHAT_DISABLE, false)

            // Gesture Settings
            val gesturesEnabled = prefs.getBoolean(C.PLAYER_GESTURES_ENABLED, true)
            val hapticEnabled = prefs.getBoolean(C.PLAYER_GESTURES_HAPTIC, false)

            val sensitivityPref = prefs.getString(C.PLAYER_GESTURES_SENSITIVITY, "1")?.toIntOrNull() ?: 1
            val sensitivity = when (sensitivityPref) {
                0 -> 0.5f // Low
                2 -> 2.0f // High
                else -> 1.0f // Medium (default)
            }

            val zoneSplit = prefs.getString(C.PLAYER_GESTURES_ZONE_SPLIT, "0.5")?.toFloatOrNull() ?: 0.5f
            controllerTapDetector = GestureDetector(
                requireContext(),
                PlayerGestureListener(
                    requireContext(),
                    this@PlayerFragment,
                    doubleTap,
                    gesturesEnabled,
                    hapticEnabled,
                    sensitivity,
                    zoneSplit
                )
            )
            gestureArbiter = PlayerGestureArbiter(
                scaleClaimDeadzone = PINCH_SCALE_CLAIM_DEADZONE,
            )

            // Edge zone detection for system gesture areas
            val defaultEdgeThreshold = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 30f, resources.displayMetrics
            ).toInt()
            
            fun isInEdgeZone(x: Float, y: Float): Boolean {
                val width = playerLayout.width
                val height = playerLayout.height
                val insets = gestureInsets
                
                // Use system gesture insets if available, otherwise use fallback
                val leftEdge = insets?.left?.takeIf { it > 0 } ?: defaultEdgeThreshold
                val rightEdge = insets?.right?.takeIf { it > 0 } ?: defaultEdgeThreshold
                val topEdge = insets?.top?.takeIf { it > 0 } ?: defaultEdgeThreshold
                val bottomEdge = insets?.bottom?.takeIf { it > 0 } ?: defaultEdgeThreshold
                
                return x <= leftEdge || 
                       x >= (width - rightEdge) || 
                       y <= topEdge || 
                       y >= (height - bottomEdge)
            }

            fun downAction(event: MotionEvent) {
                moveAnimation?.cancel()
                isTap = true
                tapEventTime = event.eventTime
                // Capture controls visibility at gesture start - this determines routing for entire gesture
                controlsVisibleAtGestureStart = playerControls.root.isVisible
                // Reset swipe gesture flag for new gesture
                isSwipeGestureInProgress = false
                if (isMaximized) {
                    if (playerControls.root.isVisible) {
                        playerControls.root.dispatchTouchEvent(event)
                    } else {
                        controllerTapDetector?.onTouchEvent(event)
                    }
                } else {
                    velocityTracker?.clear()
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain()
                    }
                    velocityTracker?.addMovement(
                        MotionEvent.obtain(
                            event.downTime,
                            event.eventTime,
                            event.action,
                            slidingLayout.translationX,
                            slidingLayout.translationY,
                            event.metaState
                        )
                    )
                    startTranslationX = slidingLayout.translationX
                    startTranslationY = slidingLayout.translationY
                }
            }

            fun upAction(event: MotionEvent) {
                if (isMaximized) {
                    if (playerControls.progressBar.isPressed) {
                        playerControls.root.dispatchTouchEvent(event)
                    } else {
                        if (slidingLayout.translationY in touchSlopRange) {
                            // Use controlsVisibleAtGestureStart for consistent routing
                            if (controlsVisibleAtGestureStart) {
                                playerControls.root.dispatchTouchEvent(event)
                            } else {
                                controllerTapDetector?.onTouchEvent(event)
                            }
                        }
                        // Only check minimize threshold if controls were visible and no swipe gesture claimed this touch
                        if (controlsVisibleAtGestureStart && !isSwipeGestureInProgress) {
                            val minimizeThreshold = slidingLayout.height / 5
                            if (slidingLayout.translationY < minimizeThreshold) {
                                moveAnimation = slidingLayout.animate().apply {
                                    translationX(0f)
                                    translationY(0f)
                                    setDuration(250L)
                                    setListener(
                                        object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                setListener(null)
                                                if (this@PlayerFragment.view != null && slidingLayout.translationY < touchSlop) {
                                                    enableBackground()
                                                }
                                            }
                                        }
                                    )
                                    start()
                                }
                            } else {
                                minimize()
                            }
                        } else if (!controlsVisibleAtGestureStart) {
                            // Controls were hidden at start - reset any accidental translation
                            if (slidingLayout.translationY != 0f) {
                                moveAnimation = slidingLayout.animate().apply {
                                    translationY(0f)
                                    setDuration(150L)
                                    start()
                                }
                            }
                        }
                    }
                } else {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVelocity = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    when {
                        xVelocity > 1500 -> {
                            isAnimating = true
                            slidingLayout.animate().apply {
                                translationX(slidingLayout.translationX + (slidingLayout.width * slidingLayout.scaleX))
                                setDuration(250L)
                                start()
                            }
                            (activity as? MainActivity)?.closePlayer(this@PlayerFragment)
                        }
                        xVelocity < -1500 -> {
                            isAnimating = true
                            slidingLayout.animate().apply {
                                translationX(slidingLayout.translationX - (slidingLayout.width * slidingLayout.scaleX))
                                setDuration(250L)
                                start()
                            }
                            (activity as? MainActivity)?.closePlayer(this@PlayerFragment)
                        }
                        else -> {
                            if (isTap && (event.eventTime - tapEventTime) < longPressTimeout) {
                                maximize()
                            } else {
                                if (moveFreely) {
                                    val windowInsets = ViewCompat.getRootWindowInsets(requireView())
                                    val insets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                                    val scaledXDiff = (slidingLayout.width * (1f - slidingLayout.scaleX)) / 2
                                    val scaledYDiff = (slidingLayout.height * (1f - slidingLayout.scaleY)) / 2
                                    val minX = 0f - scaledXDiff - ((insets?.left ?: 0) * slidingLayout.scaleX) + (insets?.left ?: 0)
                                    val minY = 0f - scaledYDiff - ((insets?.top ?: 0) * slidingLayout.scaleY) + (insets?.top ?: 0)
                                    val maxX = 0f - scaledXDiff - ((insets?.left ?: 0) * slidingLayout.scaleX) + slidingLayout.width - (playerLayout.width * slidingLayout.scaleX) - (insets?.right ?: 0)
                                    val maxY = 0f - scaledYDiff - ((insets?.top ?: 0) * slidingLayout.scaleY) + slidingLayout.height - (playerLayout.height * slidingLayout.scaleY) - (insets?.bottom ?: 0)
                                    val newX = when {
                                        slidingLayout.translationX < minX -> minX
                                        slidingLayout.translationX > maxX -> maxX
                                        else -> null
                                    }
                                    val newY = when {
                                        slidingLayout.translationY < minY -> minY
                                        slidingLayout.translationY > maxY -> maxY
                                        else -> null
                                    }
                                    if (newX != null || newY != null) {
                                        moveAnimation = slidingLayout.animate().apply {
                                            newX?.let { translationX(it) }
                                            newY?.let { translationY(it) }
                                            setDuration(250L)
                                            start()
                                        }
                                    }
                                } else {
                                    val windowInsets = ViewCompat.getRootWindowInsets(requireView())
                                    val insets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                                    val keyboardInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.let { if (it > 0) it - (insets?.bottom ?: 0) else it } ?: 0
                                    val scaledXDiff = (slidingLayout.width * (1f - slidingLayout.scaleX)) / 2
                                    val scaledYDiff = (slidingLayout.height * (1f - slidingLayout.scaleY)) / 2
                                    val navBarHeight = requireView().rootView.findViewById<LinearLayout>(R.id.navBarContainer)?.height?.takeIf { it > 0 }?.let { it - keyboardInsets } ?: (insets?.bottom ?: 0)
                                    val newX = slidingLayout.width - (insets?.right ?: 0) - (playerLayout.width * slidingLayout.scaleX) - (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20F, resources.displayMetrics) * slidingLayout.scaleX)
                                    val newY = slidingLayout.height - navBarHeight - (playerLayout.height * slidingLayout.scaleY) - (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30F, resources.displayMetrics) * slidingLayout.scaleY)
                                    moveAnimation = slidingLayout.animate().apply {
                                        translationX(0f - scaledXDiff - ((insets?.left ?: 0) * slidingLayout.scaleX) + newX)
                                        translationY(0f - scaledYDiff - ((insets?.top ?: 0) * slidingLayout.scaleY) + newY)
                                        setDuration(250L)
                                        start()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            dragView.setOnTouchListener { _, event ->
                if (!isAnimating) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (activePlayerPopup == PlayerPopupType.VOLUME) {
                                hideVolumeOverlay()
                                return@setOnTouchListener true
                            }
                            gestureArbiter.onSequenceStarted()
                            resetPinchTracking()
                            activePointerId = event.getPointerId(0)
                            val x = event.x
                            val y = event.y
                            lastX = x * slidingLayout.scaleX
                            lastY = y * slidingLayout.scaleY
                            statusBarSwipe = !isPortrait && y <= 100
                            isEdgeSwipe = !isPortrait && isInEdgeZone(x, y)
                            downAction(event)
                        }
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            if (!isPortrait && isMaximized && gesturesEnabled && gestureArbiter.onPointerAdded(event.pointerCount)) {
                                pinchPointerId1 = event.getPointerId(0)
                                pinchPointerId2 = event.getPointerId(1)
                                pinchAnchorSpan = twoFingerSpan(event, pinchPointerId1, pinchPointerId2)
                            }
                            if (activePointerId == -1) {
                                val pointerIndex = event.actionIndex
                                val pointerId = event.getPointerId(pointerIndex)
                                val x = event.getX(pointerIndex)
                                val y = event.getY(pointerIndex)
                                if (x in 0f..playerLayout.width.toFloat() && y in 0f..playerLayout.height.toFloat()) {
                                    activePointerId = pointerId
                                    lastX = x * slidingLayout.scaleX
                                    lastY = y * slidingLayout.scaleY
                                    statusBarSwipe = !isPortrait && y <= 100
                                    isEdgeSwipe = !isPortrait && isInEdgeZone(x, y)
                                    downAction(event)
                                }
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (gestureArbiter.owner == PlayerGestureArbiter.Owner.PINCH_DISPLAY_MODE) {
                                updatePinch(event)
                            } else {
                                if (gestureArbiter.isPinchCandidate && pinchPointerId1 != -1 && pinchPointerId2 != -1 && pinchAnchorSpan > 0f) {
                                    val span = twoFingerSpan(event, pinchPointerId1, pinchPointerId2)
                                    if (span > 0f) {
                                        val supersededDoubleTap = gestureArbiter.owner == PlayerGestureArbiter.Owner.DOUBLE_TAP_CHAT
                                        if (gestureArbiter.onScaleUpdate(span / pinchAnchorSpan)) {
                                            beginPinch(supersededDoubleTap, event)
                                            updatePinch(event)
                                        }
                                    }
                                }
                            }
                            if (gestureArbiter.owner != PlayerGestureArbiter.Owner.PINCH_DISPLAY_MODE) {
                            if (isMaximized) {
                                // Use controlsVisibleAtGestureStart for consistent routing throughout the gesture
                                if (controlsVisibleAtGestureStart) {
                                    // Controls were visible at gesture start: dispatch to controls and handle minimize gesture
                                    playerControls.root.dispatchTouchEvent(event)
                                    // Skip minimize gesture if a swipe gesture (seek/volume/brightness/speed) claimed this gesture
                                    if (!playerControls.progressBar.isPressed && !statusBarSwipe && activePointerId != -1 && !isSwipeGestureInProgress) {
                                        val pointerIndex = event.findPointerIndex(activePointerId)
                                        if (pointerIndex != -1) {
                                            val y = event.getY(pointerIndex)
                                            val translationY = y - lastY
                                            if (slidingLayout.translationY + translationY < 0) {
                                                slidingLayout.translationY = 0f
                                                lastY = y
                                            } else {
                                                slidingLayout.translationY += translationY
                                                lastY = y - translationY
                                            }
                                            if (slidingLayout.translationY < touchSlop) {
                                                if (!backgroundVisible) {
                                                    enableBackground()
                                                }
                                            } else {
                                                if (backgroundVisible) {
                                                    disableBackground()
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Controls were hidden at gesture start: let gesture detector handle scroll gestures
                                    controllerTapDetector?.onTouchEvent(event)
                                }
                            } else {
                                if (activePointerId != -1) {
                                    val pointerIndex = event.findPointerIndex(activePointerId)
                                    if (pointerIndex != -1) {
                                        val x = event.getX(pointerIndex) * slidingLayout.scaleX
                                        val y = event.getY(pointerIndex) * slidingLayout.scaleY
                                        val translationX = x - lastX
                                        val translationY = y - lastY
                                        slidingLayout.translationX += translationX
                                        if (moveFreely) {
                                            slidingLayout.translationY += translationY
                                        }
                                        lastX = x - translationX
                                        lastY = y - translationY
                                        velocityTracker?.addMovement(
                                            MotionEvent.obtain(
                                                event.downTime,
                                                event.eventTime,
                                                event.action,
                                                slidingLayout.translationX,
                                                slidingLayout.translationY,
                                                event.metaState
                                            )
                                        )
                                        if (isTap && ((startTranslationX - slidingLayout.translationX) !in touchSlopRange || (startTranslationY - slidingLayout.translationY) !in touchSlopRange)) {
                                            isTap = false
                                        }
                                    }
                                }
                            }
                            }
                        }
                        MotionEvent.ACTION_POINTER_UP -> {
                            val pinchEnded = gestureArbiter.onPointerRemoved(event.pointerCount - 1)
                            if (pinchEnded && pinchAnchorSpan > 0f) {
                                // A pinch ends when either of its two fingers
                                // lifts. The remaining finger stays suppressed
                                // by the arbiter until the sequence's final UP.
                                finishPinch(cancelled = false)
                                resetPinchTracking()
                            }
                            if (gestureArbiter.owner != PlayerGestureArbiter.Owner.PINCH_DISPLAY_MODE) {
                                val pointerIndex = event.actionIndex
                                val pointerId = event.getPointerId(pointerIndex)
                                if (pointerId == activePointerId) {
                                    var newId = -1
                                    for (i in 0 until event.pointerCount) {
                                        val id = event.getPointerId(i)
                                        if (id != activePointerId) {
                                            val x = event.getX(i)
                                            val y = event.getY(i)
                                            if (x in 0f..playerLayout.width.toFloat() && y in 0f..playerLayout.height.toFloat()) {
                                                newId = id
                                                lastX = x * slidingLayout.scaleX
                                                lastY = y * slidingLayout.scaleY
                                                break
                                            }
                                        }
                                    }
                                    if (newId == -1) {
                                        upAction(event)
                                    }
                                    activePointerId = newId
                                }
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (gestureArbiter.owner == PlayerGestureArbiter.Owner.PINCH_DISPLAY_MODE) {
                                if (pinchAnchorSpan > 0f) {
                                    finishPinch(cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL)
                                }
                                gestureArbiter.onSequenceFinished()
                                resetPinchTracking()
                            } else {
                                gestureArbiter.onSequenceFinished()
                                resetPinchTracking()
                                upAction(event)
                            }
                        }
                    }
                }
                true
            }
            chatTouchView.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        chatStatusBarSwipe = !isPortrait && event.y <= 100
                        chatLinearLayout.dispatchTouchEvent(event)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (chatStatusBarSwipe) {
                            chatLinearLayout.dispatchTouchEvent(
                                MotionEvent.obtain(event).apply {
                                    action = MotionEvent.ACTION_CANCEL
                                }
                            )
                        } else {
                            chatLinearLayout.dispatchTouchEvent(event)
                        }
                    }
                    else -> chatLinearLayout.dispatchTouchEvent(event)
                }
                true
            }
            with(playerControls) {
                root.setOnTouchListener { _, event ->
                    controllerTapDetector?.onTouchEvent(event) == true
                }
                playPause.setOnClickListener { playPause() }
                rewind.text = ((requireContext().prefs().getString(C.PLAYER_REWIND, "10000")?.toLongOrNull() ?: 10000) / 1000).toString()
                rewind.setOnClickListener { rewind() }
                fastForward.text = ((requireContext().prefs().getString(C.PLAYER_FORWARD, "10000")?.toLongOrNull() ?: 10000) / 1000).toString()
                fastForward.setOnClickListener { fastForward() }
                progressBar.addListener(
                    object : TimeBar.OnScrubListener {
                        override fun onScrubStart(timeBar: TimeBar, position: Long) {
                            binding.playerControls.position.text = DateUtils.formatElapsedTime(position / 1000)
                            binding.playerControls.root.removeCallbacks(controllerHideAction)
                        }

                        override fun onScrubMove(timeBar: TimeBar, position: Long) {
                            binding.playerControls.position.text = DateUtils.formatElapsedTime(position / 1000)
                        }

                        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                            if (!canceled) {
                                seek(position)
                            } else {
                                if (controllerAutoHide && controllerHideOnTouch) {
                                    binding.playerControls.root.postDelayed(controllerHideAction, 3000)
                                }
                            }
                        }
                    }
                )
                position.text = DateUtils.formatElapsedTime(0)
                duration.text = DateUtils.formatElapsedTime(0)
                subtitleView.setUserDefaultStyle()
                subtitleView.setUserDefaultTextSize()
                val channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN)
                val channelName = requireArguments().getString(KEY_CHANNEL_NAME)
                val displayName = if (channelLogin != null && !channelLogin.equals(channelName, true)) {
                    when (requireContext().prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                        "0" -> "${channelName}(${channelLogin})"
                        "1" -> channelName
                        else -> channelLogin
                    }
                } else {
                    channelName
                }
                if (requireContext().prefs().getBoolean(C.PLAYER_CHANNEL, true)) {
                    channel.visibility = View.VISIBLE
                    channel.text = displayName
                    channel.setOnClickListener {
                        findNavController().navigate(
                            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                                channelId = requireArguments().getString(KEY_CHANNEL_ID),
                                channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN),
                                channelName = requireArguments().getString(KEY_CHANNEL_NAME),
                            )
                        )
                        minimize()
                    }
                }
                val titleText = requireArguments().getString(KEY_TITLE)
                if (!titleText.isNullOrBlank() && requireContext().prefs().getBoolean(C.PLAYER_TITLE, true)) {
                    title.visibility = View.VISIBLE
                    title.text = titleText
                }
                val gameName = requireArguments().getString(KEY_GAME_NAME)
                if (!gameName.isNullOrBlank() && requireContext().prefs().getBoolean(C.PLAYER_CATEGORY, true)) {
                    category.visibility = View.VISIBLE
                    category.text = gameName
                    category.setOnClickListener {
                        findNavController().navigate(
                            if (requireContext().prefs().getBoolean(C.UI_GAMEPAGER, true)) {
                                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                    gameId = requireArguments().getString(KEY_GAME_ID),
                                    gameSlug = requireArguments().getString(KEY_GAME_SLUG),
                                    gameName = gameName
                                )
                            } else {
                                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                    gameId = requireArguments().getString(KEY_GAME_ID),
                                    gameSlug = requireArguments().getString(KEY_GAME_SLUG),
                                    gameName = gameName
                                )
                            }
                        )
                        minimize()
                    }
                }
                if (requireContext().prefs().getBoolean(C.PLAYER_MINIMIZE, true)) {
                    minimize.visibility = View.VISIBLE
                    minimize.setOnClickListener { minimize() }
                }
                if (requireContext().prefs().getBoolean(C.PLAYER_VOLUMEBUTTON, true)) {
                    volume.visibility = View.VISIBLE
                    volume.setOnClickListener {
                        if (activePlayerPopup == PlayerPopupType.VOLUME) hideVolumeOverlay() else showVolumeOverlay()
                    }
                }
                if (requireContext().prefs().getBoolean(C.PLAYER_SETTINGS, true)) {
                    quality.visibility = View.VISIBLE
                    quality.setOnClickListener { showQualityDialog() }
                }
                if (requireContext().prefs().getBoolean(C.PLAYER_MODE, false)) {
                    audioOnly.visibility = View.VISIBLE
                    audioOnly.setOnClickListener {
                        if (viewModel.quality?.name == AUDIO_ONLY_QUALITY) {
                            changeQuality(viewModel.previousQuality)
                        } else {
                            changeQuality(viewModel.qualities?.find { it.name == AUDIO_ONLY_QUALITY })
                        }
                        viewModel.userHasChangedQuality = true
                        changePlayerMode()
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && requireContext().prefs().getBoolean(C.PLAYER_AUDIO_COMPRESSOR_BUTTON, true)) {
                    audioCompressor.visibility = View.VISIBLE
                    if (requireContext().prefs().getBoolean(C.PLAYER_AUDIO_COMPRESSOR, false)) {
                        audioCompressor.setImageResource(R.drawable.baseline_audio_compressor_on_24dp)
                    } else {
                        audioCompressor.setImageResource(R.drawable.baseline_audio_compressor_off_24dp)
                    }
                    audioCompressor.setOnClickListener {
                        toggleAudioCompressor()
                    }
                }
                if (requireContext().prefs().getBoolean(C.PLAYER_MENU, true)) {
                    menu.visibility = View.VISIBLE
                    menu.setOnClickListener { showMorePopup() }
                }
                if (videoType == STREAM) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.streamResult.collectLatest {
                                if (it != null) {
                                    startStream(it)
                                    viewModel.streamResult.value = null
                                }
                            }
                        }
                    }
                    if (!requireContext().tokenPrefs().getString(C.USERNAME, null).isNullOrBlank() &&
                        (!TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                                !TwitchApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank())
                    ) {
                        if (requireContext().prefs().getBoolean(C.PLAYER_CHATBARTOGGLE, false) && !requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)) {
                            toggleChatInput.visibility = View.VISIBLE
                            toggleChatInput.setOnClickListener { toggleChatBar() }
                        }
                        slidingLayout.viewTreeObserver.addOnGlobalLayoutListener {
                            if (slidingLayout.isKeyboardShown) {
                                if (!isKeyboardShown) {
                                    isKeyboardShown = true
                                    if (!isPortrait) {
                                        chatLayout.updateLayoutParams { width = (slidingLayout.width / 1.8f).toInt() }
                                        showStatusBar()
                                    }
                                }
                            } else {
                                if (isKeyboardShown) {
                                    isKeyboardShown = false
                                    chatLayout.clearFocus()
                                    if (!isPortrait) {
                                        chatLayout.updateLayoutParams { width = chatWidthLandscape }
                                        if (isMaximized) {
                                            hideStatusBar()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.stream.collectLatest { stream ->
                                if (stream != null) {
                                    stream.id?.let { chatFragment?.updateStreamId(it) }
                                    if (requireContext().prefs().getBoolean(C.CHAT_DISABLE, false) ||
                                        !requireContext().prefs().getBoolean(C.CHAT_PUBSUB_ENABLED, true) ||
                                        viewersText.text.isNullOrBlank()
                                    ) {
                                        updateViewerCount(stream.viewerCount)
                                    }
                                    if (requireContext().prefs().getBoolean(C.CHAT_DISABLE, false) ||
                                        !requireContext().prefs().getBoolean(C.CHAT_PUBSUB_ENABLED, true) ||
                                        title.text.isNullOrBlank() ||
                                        category.text.isNullOrBlank()
                                    ) {
                                        updateStreamInfo(stream.title, stream.gameId, stream.gameSlug, stream.gameName)
                                    }
                                    if (requireContext().prefs().getBoolean(C.PLAYER_SHOW_UPTIME, true) &&
                                        !uptimeLayout.isVisible
                                    ) {
                                        stream.createdAt?.let { date ->
                                            TwitchApiHelper.parseIso8601DateUTC(date)?.let { startedAtMs ->
                                                updateUptime(startedAtMs)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (requireContext().prefs().getBoolean(C.PLAYER_RESTART, true)) {
                        restart.visibility = View.VISIBLE
                        restart.setOnClickListener { restartPlayer() }
                    }
                    if (requireContext().prefs().getBoolean(C.PLAYER_SEEKLIVE, false)) {
                        seekLive.visibility = View.VISIBLE
                        seekLive.setOnClickListener { seekToLivePosition() }
                    }
                    if (requireContext().prefs().getBoolean(C.PLAYER_VIEWERLIST, false)) {
                        viewersLayout.setOnClickListener { openViewerList() }
                    }
                    if (requireContext().prefs().getBoolean(C.PLAYER_SHOW_UPTIME, true)) {
                        requireArguments().getString(KEY_STARTED_AT)?.let {
                            TwitchApiHelper.parseIso8601DateUTC(it)?.let { startedAtMs ->
                                updateUptime(startedAtMs)
                            }
                        }
                    }
                    rewind.visibility = View.GONE
                    fastForward.visibility = View.GONE
                    position.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    duration.visibility = View.GONE
                    updateStreamInfo(
                        requireArguments().getString(KEY_TITLE),
                        requireArguments().getString(KEY_GAME_ID),
                        requireArguments().getString(KEY_GAME_SLUG),
                        requireArguments().getString(KEY_GAME_NAME)
                    )
                    updateViewerCount(requireArguments().getInt(KEY_VIEWER_COUNT).takeIf { it != -1 })
                } else {
                    if (requireContext().prefs().getBoolean(C.PLAYER_SPEEDBUTTON, true)) {
                        speed.visibility = View.VISIBLE
                        updatePlaybackSpeedUi()
                        speed.setOnClickListener { showSpeedDialog() }
                    }
                }
                if (videoType == VIDEO) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.videoResult.collectLatest {
                                if (it != null) {
                                    startVideo(it, viewModel.playbackPosition, true)
                                    viewModel.videoResult.value = null
                                }
                            }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.savedPosition.collectLatest {
                                if (it != null) {
                                    playVideo((requireContext().prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, it)
                                    viewModel.savedPosition.value = null
                                }
                            }
                        }
                    }
                    if (requireContext().prefs().getBoolean(C.PLAYER_MENU_BOOKMARK, true)) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.isBookmarked.collectLatest {
                                    if (it != null) {
                                        activeMorePopupBinder?.setBookmarkText(it)
                                        viewModel.isBookmarked.value = null
                                    }
                                }
                            }
                        }
                    }
                    if (!requireArguments().getString(KEY_VIDEO_ID).isNullOrBlank() && (requireContext().prefs().getBoolean(C.PLAYER_GAMESBUTTON, true) || requireContext().prefs().getBoolean(C.PLAYER_MENU_GAMES, false))) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.gamesList.collectLatest { list ->
                                    if (!list.isNullOrEmpty()) {
                                        if (requireContext().prefs().getBoolean(C.PLAYER_GAMESBUTTON, true)) {
                                            vodGames.visibility = View.VISIBLE
                                            vodGames.setOnClickListener { showVodGames() }
                                        }
                                        activeMorePopupBinder?.setVodGames()
                                    }
                                }
                            }
                        }
                    }
                }
                if (videoType == CLIP) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.clipUrls.collectLatest { list ->
                                if (list != null) {
                                    val supportedCodecs = requireContext().prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264")?.split(',') ?: emptyList()
                                    val filtered = list.filterNot {
                                        it.codecs?.substringBefore('.').let { codec ->
                                            (codec == "av01" && !supportedCodecs.contains("av1")) || ((codec == "hev1" || codec == "hvc1") && !supportedCodecs.contains("h265"))
                                        }
                                    }
                                    viewModel.qualities = filtered
                                        .sortedByDescending {
                                            it.name?.substringAfter("p", "")?.takeWhile { it.isDigit() }?.toIntOrNull()
                                        }
                                        .sortedByDescending {
                                            it.name?.substringBefore("p", "")?.takeWhile { it.isDigit() }?.toIntOrNull()
                                        }
                                        .toMutableList().apply {
                                            add(VideoQuality(AUDIO_ONLY_QUALITY))
                                        }
                                    setDefaultQuality()
                                    changePlayerMode()
                                    val url = viewModel.quality?.url ?: viewModel.qualities?.firstOrNull()?.url
                                    if (url != null) {
                                        startClip(url)
                                    }
                                    viewModel.clipUrls.value = null
                                }
                            }
                        }
                    }
                    val videoId = requireArguments().getString(KEY_VIDEO_ID)
                    if (!videoId.isNullOrBlank()) {
                        binding.watchVideo.visibility = View.VISIBLE
                        binding.watchVideo.setOnClickListener {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val offset = requireArguments().getInt(KEY_VIDEO_OFFSET_SECONDS).takeIf { it != -1 }?.let {
                                    (it * 1000) + (getCurrentPosition() ?: 0)
                                } ?: 0
                                if (requireContext().prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                                    videoId.toLongOrNull()?.let { id ->
                                        viewModel.savePosition(id, offset)
                                    }
                                }
                                (requireActivity() as MainActivity).startVideo(
                                    Video(
                                        id = videoId,
                                        channelId = requireArguments().getString(KEY_CHANNEL_ID),
                                        channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN),
                                        channelName = requireArguments().getString(KEY_CHANNEL_NAME),
                                        channelImageURL = requireArguments().getString(KEY_PROFILE_IMAGE_URL),
                                        animatedPreviewURL = requireArguments().getString(KEY_VIDEO_ANIMATED_PREVIEW),
                                    ),
                                    offset,
                                    true
                                )
                            }
                        }
                    }
                } else {
                    if (requireContext().prefs().getBoolean(C.PLAYER_SLEEP, false)) {
                        sleepTimer.visibility = View.VISIBLE
                        sleepTimer.setOnClickListener { showSleepTimerDialog() }
                    }
                }
                if (videoType == OFFLINE_VIDEO) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.savedOfflineVideoPosition.collectLatest {
                                if (it != null) {
                                    val url = requireArguments().getString(KEY_URL)
                                    viewModel.qualities = listOf(
                                        VideoQuality("source", null, url),
                                        VideoQuality(AUDIO_ONLY_QUALITY),
                                    )
                                    setDefaultQuality()
                                    changePlayerMode()
                                    startOfflineVideo(url, it)
                                    viewModel.savedOfflineVideoPosition.value = null
                                }
                            }
                        }
                    }
                } else {
                    quality.isEnabled = false
                    quality.setColorFilter(Color.GRAY)
                    download.isEnabled = false
                    download.setColorFilter(Color.GRAY)
                    audioOnly.isEnabled = false
                    audioOnly.setColorFilter(Color.GRAY)
                    viewLifecycleOwner.lifecycleScope.launch {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            viewModel.loaded.collectLatest {
                                if (it) {
                                    quality.isEnabled = true
                                    quality.setColorFilter(Color.WHITE)
                                    download.isEnabled = true
                                    download.setColorFilter(Color.WHITE)
                                    audioOnly.isEnabled = true
                                    audioOnly.setColorFilter(Color.WHITE)
                                    setQualityText()
                                }
                            }
                        }
                    }
                    if (requireContext().prefs().getBoolean(C.PLAYER_DOWNLOAD, false)) {
                        download.visibility = View.VISIBLE
                        download.setOnClickListener { showDownloadDialog() }
                    }
                    val setting = requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
                    if (requireContext().prefs().getBoolean(C.PLAYER_FOLLOW, false) && (setting == 0 || setting == 1)) {
                        follow.visibility = View.VISIBLE
                        follow.setOnClickListener {
                            viewModel.isFollowing.value?.let {
                                if (it) {
                                    requireContext().getAlertDialogBuilder()
                                        .setMessage(getString(R.string.unfollow_channel, displayName))
                                        .setNegativeButton(getString(R.string.no), null)
                                        .setPositiveButton(getString(R.string.yes)) { _, _ ->
                                            viewModel.deleteFollowChannel(
                                                requireContext().tokenPrefs().getString(C.USER_ID, null),
                                                requireArguments().getString(KEY_CHANNEL_ID),
                                                setting,
                                                requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                                TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                                requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                                            )
                                        }
                                        .show()
                                } else {
                                    viewModel.saveFollowChannel(
                                        requireContext().tokenPrefs().getString(C.USER_ID, null),
                                        requireArguments().getString(KEY_CHANNEL_ID),
                                        requireArguments().getString(KEY_CHANNEL_LOGIN),
                                        requireArguments().getString(KEY_CHANNEL_NAME),
                                        setting,
                                        requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                                        !requireContext().prefs().getBoolean(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true),
                                        requireArguments().getString(KEY_STARTED_AT),
                                        requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                        TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                        requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                                    )
                                }
                            }
                        }
                        viewLifecycleOwner.lifecycleScope.launch {
                            repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.isFollowing.collectLatest {
                                    if (it != null) {
                                        if (it) {
                                            follow.setImageResource(R.drawable.baseline_favorite_black_24)
                                        } else {
                                            follow.setImageResource(R.drawable.baseline_favorite_border_black_24)
                                        }
                                    }
                                }
                            }
                        }
                        viewLifecycleOwner.lifecycleScope.launch {
                            repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.follow.collectLatest { pair ->
                                    if (pair != null) {
                                        val following = pair.first
                                        val errorMessage = pair.second
                                        if (!errorMessage.isNullOrBlank()) {
                                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                                        } else {
                                            if (following) {
                                                Toast.makeText(requireContext(), getString(R.string.now_following, displayName), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(requireContext(), getString(R.string.unfollowed, displayName), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        viewModel.follow.value = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val currentChatFragment = (childFragmentManager.findFragmentById(R.id.chatFragmentContainer) as? ChatFragment)
            if (currentChatFragment != null) {
                chatFragment = currentChatFragment
            } else {
                val fragment = createChatFragment(false)
                if (fragment != null) {
                    childFragmentManager.beginTransaction().replace(R.id.chatFragmentContainer, fragment).commit()
                }
                chatFragment = fragment
            }
            setupFloatingChatInteraction()
        }
    }

    private fun initLayout() {
        with(binding) {
            if (isPortrait) {
                // Hide floating chat in portrait mode
                if (isFloatingChatEnabled) {
                    isFloatingChatEnabled = false
                    floatingChatRoot.visibility = View.GONE
                    // Reparent chat view back to sidebar (don't recreate fragment)
                    reparentChatView(toFloating = false)
                }
                requireActivity().window.decorView.setOnSystemUiVisibilityChangeListener(null)
                showStatusBar()
                playerLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                    marginEnd = 0
                }
                chatLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                    gravity = Gravity.BOTTOM
                }
                if (isMaximized) {
                    chatLayout.visibility = View.VISIBLE
                } else {
                    chatLayout.visibility = View.GONE
                    val (minimizedScaleX, minimizedScaleY) = getScaleValues()
                    slidingLayout.scaleX = minimizedScaleX
                    slidingLayout.scaleY = minimizedScaleY
                    slidingLayout.doOnPreDraw {
                        val (minimizedScaleX, minimizedScaleY) = getScaleValues()
                        val windowInsets = ViewCompat.getRootWindowInsets(requireView())
                        val insets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                        val keyboardInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.let { if (it > 0) it - (insets?.bottom ?: 0) else it } ?: 0
                        val playerHeight = (slidingLayout.width / (16f / 9f)).toInt()
                        val scaledXDiff = (slidingLayout.width * (1f - minimizedScaleX)) / 2
                        val scaledYDiff = (slidingLayout.height * (1f - minimizedScaleY)) / 2
                        val navBarHeight = requireView().rootView.findViewById<LinearLayout>(R.id.navBarContainer)?.height?.takeIf { it > 0 }?.let { it - keyboardInsets } ?: (insets?.bottom ?: 0)
                        val newX = slidingLayout.width - (insets?.right ?: 0) - (slidingLayout.width * minimizedScaleX) - (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20F, resources.displayMetrics) * minimizedScaleX)
                        val newY = slidingLayout.height - navBarHeight - (playerHeight * minimizedScaleY) - (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30F, resources.displayMetrics) * minimizedScaleY)
                        slidingLayout.translationX = 0f - scaledXDiff - ((insets?.left ?: 0) * minimizedScaleX) + newX
                        slidingLayout.translationY = 0f - scaledYDiff - ((insets?.top ?: 0) * minimizedScaleY) + newY
                    }
                }
                if (isMaximized) {
                    applyMaximizedPlayerVisualState()
                } else {
                    applyMinimizedPlayerVisualState()
                }
                aspectRatioFrameLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                    gravity = Gravity.NO_GRAVITY
                }
                playerLayout.isPortrait = true
                chatLayout.isPortrait = true
                with(playerControls) {
                    if (requireContext().prefs().getBoolean(C.PLAYER_FULLSCREEN, true)) {
                        fullscreen.visibility = View.VISIBLE
                        fullscreen.setImageResource(R.drawable.baseline_fullscreen_black_24)
                        fullscreen.setOnClickListener {
                            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                    toggleChat.visibility = View.GONE
                }            } else {
                requireActivity().window.decorView.setOnSystemUiVisibilityChangeListener {
                    if (!isKeyboardShown && isMaximized && activity != null) {
                        hideStatusBar()
                    }
                }
                if (isMaximized) {
                    hideStatusBar()
                    val showSidebarChat = PlayerChatModeHelper.shouldShowSidebarChat(isChatOpen, isFloatingChatEnabled)
                    val chatWidth = if (showSidebarChat) chatWidthLandscape else 0
                    playerLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                        marginEnd = chatWidth
                    }
                    chatLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                        width = chatWidthLandscape
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                        gravity = Gravity.END
                    }
                    if (showSidebarChat) {
                        chatLayout.visibility = View.VISIBLE
                        if (requireView().findViewById<Button>(R.id.btnDown)?.isVisible == false) {
                            requireView().findViewById<RecyclerView>(R.id.recyclerView)?.let { recyclerView ->
                                recyclerView.adapter?.itemCount?.let { recyclerView.scrollToPosition(it - 1) }
                            }
                        }
                    } else {
                        chatLayout.visibility = View.GONE
                    }
                } else {
                    showStatusBar()
                    playerLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                        marginEnd = 0
                    }
                    chatLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                        width = chatWidthLandscape
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                        gravity = Gravity.END
                    }
                    chatLayout.visibility = View.GONE
                    val (minimizedScaleX, minimizedScaleY) = getScaleValues()
                    slidingLayout.scaleX = minimizedScaleX
                    slidingLayout.scaleY = minimizedScaleY
                    slidingLayout.doOnPreDraw {
                        val (minimizedScaleX, minimizedScaleY) = getScaleValues()
                        val windowInsets = ViewCompat.getRootWindowInsets(requireView())
                        val insets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                        val keyboardInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.let { if (it > 0) it - (insets?.bottom ?: 0) else it } ?: 0
                        val playerWidth = slidingLayout.width - getHorizontalInsets(windowInsets)
                        val scaledXDiff = (slidingLayout.width * (1f - minimizedScaleX)) / 2
                        val scaledYDiff = (slidingLayout.height * (1f - minimizedScaleY)) / 2
                        val navBarHeight = requireView().rootView.findViewById<LinearLayout>(R.id.navBarContainer)?.height?.takeIf { it > 0 }?.let { it - keyboardInsets } ?: (insets?.bottom ?: 0)
                        val newX = slidingLayout.width - (insets?.right ?: 0) - (playerWidth * minimizedScaleX) - (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20F, resources.displayMetrics) * minimizedScaleX)
                        val newY = slidingLayout.height - navBarHeight - (slidingLayout.height * minimizedScaleY) - (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30F, resources.displayMetrics) * minimizedScaleY)
                        slidingLayout.translationX = 0f - scaledXDiff - ((insets?.left ?: 0) * minimizedScaleX) + newX
                        slidingLayout.translationY = 0f - scaledYDiff - ((insets?.top ?: 0) * minimizedScaleY) + newY
                    }
                }
                if (isMaximized) {
                    applyMaximizedPlayerVisualState()
                } else {
                    applyMinimizedPlayerVisualState()
                }
                aspectRatioFrameLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                    gravity = Gravity.CENTER
                }
                playerLayout.isPortrait = false
                chatLayout.isPortrait = false
                with(playerControls) {
                    if (requireContext().prefs().getBoolean(C.PLAYER_FULLSCREEN, true)) {
                        fullscreen.visibility = View.VISIBLE
                        fullscreen.setImageResource(R.drawable.baseline_fullscreen_exit_black_24)
                        fullscreen.setOnClickListener {
                            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                    if (requireContext().prefs().getBoolean(C.PLAYER_CHATTOGGLE, true) && !requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)) {
                        toggleChat.visibility = View.VISIBLE
                        updateChatButtonIcon()
                        
                        toggleChat.setOnClickListener { 
                            cycleChatMode()
                            updateChatButtonIcon()
                        }
                        }
                        // Separate floating chat button is now hidden as functionality is merged
                        toggleFloatingChat.visibility = View.GONE
                }
            }
            updateQuickPlayerControls()
        }
    }

    fun showSleepTimerDialog() {
        if (requireContext().prefs().getBoolean(C.SLEEP_TIMER_USE_TIME_PICKER, false)) {
            if (((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0) > 0L) {
                requireContext().getAlertDialogBuilder()
                    .setMessage(getString(R.string.stop_sleep_timer_message))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        onSleepTimerChanged(-1L, 0, 0, requireContext().prefs().getBoolean(C.SLEEP_TIMER_LOCK, false))
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .show()
            } else {
                val savedValue = requireContext().prefs().getInt(C.SLEEP_TIMER_TIME, 15)
                val picker = MaterialTimePicker.Builder()
                    .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                    .setHour(savedValue / 60)
                    .setMinute(savedValue % 60)
                    .build()
                picker.addOnPositiveButtonClickListener {
                    val minutes = TwitchApiHelper.getMinutesLeft(picker.hour, picker.minute)
                    onSleepTimerChanged(minutes * 60_000L, minutes / 60, minutes % 60, requireContext().prefs().getBoolean(C.SLEEP_TIMER_LOCK, false))
                    requireContext().prefs().edit {
                        putInt(C.SLEEP_TIMER_TIME, picker.hour * 60 + picker.minute)
                    }
                }
                picker.show(childFragmentManager, null)
            }
        } else {
            SleepTimerDialog.newInstance((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0).show(childFragmentManager, null)
        }
    }

    fun getQualityMap(): Map<String, VideoQuality>? {
        val qualities = viewModel.qualities
        return if (!qualities.isNullOrEmpty()) {
            val hideCodecs = qualities.all {
                val codec = it.codecs?.substringBefore('.')
                codec == "avc1" || codec == "mp4a" || codec.isNullOrBlank()
            }
            qualities.filterNot { quality ->
                quality.name.isNumericQualityFallback()
            }.associateBy { quality ->
                when (normalizeQualityName(quality.name)) {
                    "auto" -> getString(R.string.auto)
                    "source" -> getString(R.string.source)
                    "audio_only" -> getString(R.string.audio_only)
                    "chat_only" -> getString(R.string.chat_only)
                    else -> {
                        if (hideCodecs) {
                            quality.name.toString()
                        } else {
                            val codec = quality.codecs?.substringBefore('.')
                            val codecName = when {
                                codec == "av01" -> "AV1"
                                codec == "hev1" || codec == "hvc1" -> "H.265"
                                codec == "avc1" || codec.isNullOrBlank() -> "H.264"
                                else -> codec
                            }
                            "${quality.name} $codecName"
                        }
                    }
                }
            }
        } else null
    }

    fun showQualityDialog() {
        if (!isMaximized) return
        val qualities = viewModel.qualities?.takeIf { it.isNotEmpty() } ?: return
        val panelWidth = PlayerPopupPolicy.panelWidthPx(
            binding.playerLayout.width,
            resources.displayMetrics.density,
        )
        if (panelWidth <= 0) return
        val popupBinding = LayoutPlayerQualityPopupBinding.inflate(
            layoutInflater,
            binding.playerPopupHost.playerPopupPanelContainer,
            false,
        )
        val trigger = if (binding.playerControls.qualityValue.isVisible) {
            binding.playerControls.qualityValue
        } else {
            binding.playerControls.quality
        }
        // Bind before showing: the host measures the finished grid to place
        // the panel before its first frame is drawn.
        val binder = PlayerQualityPopupBinder(
            context = requireContext(),
            binding = popupBinding,
            qualities = qualities,
            selectedTag = viewModel.quality?.name,
            panelWidthPx = panelWidth,
            onQualitySelected = ::selectQuality,
            onDismissRequested = { hidePlayerPopup() },
        ).also { it.bind() }
        showPlayerPopup(
            type = PlayerPopupType.QUALITY,
            trigger = trigger,
            content = popupBinding.root,
            panelWidth = panelWidth,
        )
        activeQualityPopupBinder = binder
    }

    fun selectQuality(qualityName: String?) {
        viewModel.userHasChangedQuality = true
        val normalizedQualityName = normalizeQualityName(qualityName)
        changeQuality(
            viewModel.qualities?.find { it.name == qualityName }
                ?: viewModel.qualities?.find { normalizeQualityName(it.name) == normalizedQualityName }
        )
        changePlayerMode()
        setQualityText()
    }

    private fun normalizeQualityName(name: String?): String {
        return name
            ?.trim()
            ?.lowercase(Locale.US)
            ?.replace(' ', '_')
            ?.replace('-', '_')
            .orEmpty()
    }

    fun showSpeedDialog() {
        if (!isMaximized) return
        val speed = PlayerSpeedPopupState.initialSpeed(
            currentSpeed = getCurrentSpeed(),
            savedSpeed = requireContext().prefs().getFloat(C.PLAYER_SPEED, 1f)
        )
        val panelWidth = PlayerPopupPolicy.panelWidthPx(
            binding.playerLayout.width,
            resources.displayMetrics.density,
        )
        if (panelWidth <= 0) return
        val popupBinding = LayoutPlayerSpeedPopupBinding.inflate(
            layoutInflater,
            binding.playerPopupHost.playerPopupPanelContainer,
            false,
        )
        val binder = PlayerSpeedPopupBinder(
            context = requireContext(),
            binding = popupBinding,
            initialSpeed = speed,
            panelWidthPx = panelWidth,
            onSpeedChanged = ::setPlaybackSpeed,
            onDismissRequested = { hidePlayerPopup() },
        ).also { it.bind() }
        showPlayerPopup(
            type = PlayerPopupType.SPEED,
            trigger = binding.playerControls.speed,
            content = popupBinding.root,
            panelWidth = panelWidth,
        )
        activeSpeedPopupBinder = binder
    }

    private fun showMorePopup() {
        if (!isMaximized) return
        val panelWidth = PlayerPopupPolicy.panelWidthPx(
            binding.playerLayout.width,
            resources.displayMetrics.density,
        )
        if (panelWidth <= 0) return
        val popupBinding = LayoutPlayerMorePopupBinding.inflate(
            layoutInflater,
            binding.playerPopupHost.playerPopupPanelContainer,
            false,
        )
        val currentQuality = getQualityMap()?.entries?.find { it.value == viewModel.quality }?.key
        val currentSpeed = getCurrentSpeed()?.let { speed ->
            requireContext().prefs()
                .getString(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0")
                ?.split("\n")
                ?.find { it == speed.toString() }
        }
        val binder = PlayerMorePopupBinder(
            fragment = this,
            popupBinding = popupBinding,
            videoType = videoType,
            speedText = currentSpeed,
            qualityText = currentQuality,
            vodGamesAvailable = !viewModel.gamesList.value.isNullOrEmpty(),
            onDismissRequested = { hidePlayerPopup() },
        ).also { it.bind() }
        showPlayerPopup(
            type = PlayerPopupType.MORE,
            trigger = binding.playerControls.menu,
            content = popupBinding.root,
            panelWidth = panelWidth,
        )
        activeMorePopupBinder = binder
    }

    private fun showPlayerPopup(
        type: PlayerPopupType,
        trigger: View,
        content: View,
        panelWidth: Int,
    ) {
        hidePlayerPopup(restoreFocus = false, animate = false)
        popupAnchorRect = null
        val generation = ++popupGeneration
        val host = binding.playerPopupHost
        val container = host.playerPopupPanelContainer
        // GONE overlays have no first-open geometry. Resolve the full fragment
        // bounds before measuring/placing content, while the host is invisible.
        host.root.visibility = View.INVISIBLE
        host.root.measure(
            View.MeasureSpec.makeMeasureSpec(binding.root.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(binding.root.height, View.MeasureSpec.EXACTLY),
        )
        host.root.layout(0, 0, binding.root.width, binding.root.height)
        activePlayerPopup = type
        activePopupTrigger = trigger
        // TalkBack should traverse the popup, not the obscured video/chat.
        popupBackgroundAccessibility = listOf(binding.slidingLayout, binding.floatingChatRoot).map { background ->
            val previousMode = background.importantForAccessibility
            background.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            background to previousMode
        }
        container.removeAllViews()
        // Reset every geometry field so a new popup never inherits margins or
        // size from the previously dismissed one; exact values follow in
        // positionPlayerPopup once content is measurable.
        container.layoutParams = (container.layoutParams as? FrameLayout.LayoutParams)?.apply {
            width = panelWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            marginStart = 0
            topMargin = 0
            gravity = Gravity.TOP or Gravity.START
        } ?: container.layoutParams
        if (content is com.google.android.material.card.MaterialCardView && type != PlayerPopupType.VOLUME) {
            PlayerPopupContent.prepare(content)
        }
        container.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        host.root.setOnClickListener { hidePlayerPopup() }
        container.setOnClickListener { /* Consume panel taps; children own their actions. */ }
        container.animate().cancel()
        container.alpha = 0f
        container.scaleX = PLAYER_POPUP_START_SCALE
        container.scaleY = PLAYER_POPUP_START_SCALE
        // Place the panel before it is ever drawn: measuring uses explicit
        // specs and does not need a layout pass, so the reveal animation's
        // first frame already sits at the anchored geometry instead of the
        // reset top-left slot that only corrects on the next traversal.
        positionPlayerPopup(container, trigger)
        host.root.visibility = View.VISIBLE
        showController(force = true)
        binding.playerControls.root.removeCallbacks(controllerHideAction)

        container.doOnLayout {
            if (popupGeneration != generation || activePlayerPopup != type) return@doOnLayout
            // Safety net for surfaces that resize between show and layout;
            // idempotent once the pre-reveal pass above has placed the panel.
            positionPlayerPopup(container, trigger)
            val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                // Reposition on any geometry delta, not just size changes:
                // margin-driven moves must also re-clamp against the surface.
                val changed = right - left != oldRight - oldLeft ||
                    bottom - top != oldBottom - oldTop ||
                    left != oldLeft ||
                    top != oldTop
                if (changed && popupGeneration == generation && activePlayerPopup == type) {
                    positionPlayerPopup(container, trigger)
                }
            }
            activePopupLayoutListener = layoutListener
            host.root.addOnLayoutChangeListener(layoutListener)
            val triggerListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (popupGeneration == generation && activePlayerPopup == type) {
                    positionPlayerPopup(container, trigger)
                }
            }
            activePopupTriggerLayoutListener = triggerListener
            // The trigger can gain its real bounds after this popup positioned
            // from a fallback (controls still GONE at open time). Once a valid
            // anchor rect is cached the popup stops tracking the trigger, so
            // late control-bar reflows (quality label or viewer count text
            // changes) never drag a visible popup around.
            trigger.addOnLayoutChangeListener(triggerListener)
            // Touch opening must not scroll the body to a focused row. Keyboard
            // navigation still starts from the persistent close/header controls.
            if (!container.isInTouchMode) {
                val focusableChildren = arrayListOf<View>()
                content.addFocusables(focusableChildren, View.FOCUS_FORWARD)
                focusableChildren.firstOrNull()?.requestFocus()
            }
            val anchor = popupAnchorRect
            container.pivotX = anchor?.let {
                (it.centerX - container.left).toFloat().coerceIn(0f, container.width.toFloat())
            } ?: (container.width / 2f)
            container.pivotY = anchor?.let {
                ((it.top + it.bottom) / 2f - container.top).coerceIn(0f, container.height.toFloat())
            } ?: (container.height / 2f)
            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                .setDuration(PLAYER_POPUP_OPEN_MS)
                .withLayer()
                .start()
        }
    }

    /** Visible content bounds in overlay coordinates; portrait includes the area over chat. */
    private fun popupSurfaceInsets(): PlayerPopupPolicy.Insets {
        val root = binding.playerPopupHost.root
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val visible = android.graphics.Rect()
        root.getWindowVisibleDisplayFrame(visible)
        // Keep all three rectangles in screen coordinates. GlobalVisibleRect
        // is root-relative and can disagree with window/screen offsets.
        val playerLocation = IntArray(2)
        binding.playerLayout.getLocationOnScreen(playerLocation)
        val player = android.graphics.Rect(
            playerLocation[0], playerLocation[1],
            playerLocation[0] + binding.playerLayout.width,
            playerLocation[1] + binding.playerLayout.height,
        )
        val left = max(player.left, visible.left) - location[0]
        val top = max(player.top, visible.top) - location[1]
        val right = min(player.right, visible.right) - location[0]
        val bottom = (if (binding.playerLayout.isPortrait) visible.bottom else min(player.bottom, visible.bottom)) - location[1]
        return PlayerPopupPolicy.Insets(
            left = left.coerceIn(0, root.width),
            top = top.coerceIn(0, root.height),
            right = (root.width - right).coerceIn(0, root.width),
            bottom = (root.height - bottom).coerceIn(0, root.height),
        )
    }

    private fun positionPlayerPopup(container: FrameLayout, trigger: View): PlayerPopupPolicy.Placement {
        val surface = binding.playerPopupHost.root
        val insets = popupSurfaceInsets()
        val isRtl = surface.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val anchor = popupAnchorRect ?: popupTriggerRect(trigger)?.also { popupAnchorRect = it }
        // Measure the card at the final width, never the constrained viewport.
        fun place(height: Int) = PlayerPopupPolicy.place(
            surfaceWidthPx = surface.width,
            surfaceHeightPx = surface.height,
            measuredPanelHeightPx = height,
            density = resources.displayMetrics.density,
            insets = insets,
            trigger = anchor,
            isRtl = isRtl,
        )
        val content = container.getChildAt(0)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(place(0).width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val placement = place(content.measuredHeight)
        applyPopupGeometry(
            container = container,
            width = placement.width,
            height = min(content.measuredHeight, placement.maxHeight),
            marginStart = PlayerPopupPolicy.startMarginPx(
                surfaceWidthPx = surface.width,
                placementLeftPx = placement.left,
                placementWidthPx = placement.width,
                isRtl = isRtl,
            ),
            topMargin = placement.top,
        )
        val scrim = binding.playerPopupHost.playerPopupScrim
        if (binding.playerLayout.isPortrait && !scrim.isVisible) {
            scrim.alpha = 0f
            scrim.isVisible = true
            scrim.animate().alpha(1f).setDuration(PLAYER_POPUP_OPEN_MS).start()
        }
        return placement
    }

    /** Writes popup geometry only when something actually changed to avoid relayout loops. */
    private fun applyPopupGeometry(
        container: FrameLayout,
        width: Int,
        height: Int,
        marginStart: Int,
        topMargin: Int,
    ) {
        val params = container.layoutParams as? FrameLayout.LayoutParams ?: return
        val unchanged = params.width == width &&
            params.height == height &&
            params.marginStart == marginStart &&
            params.topMargin == topMargin &&
            params.gravity == (Gravity.TOP or Gravity.START)
        if (unchanged) return
        params.width = width
        params.height = height
        params.marginStart = marginStart
        params.topMargin = topMargin
        params.gravity = Gravity.TOP or Gravity.START
        container.layoutParams = params
    }

    private fun popupTriggerRect(trigger: View): PlayerPopupPolicy.Rect? {
        if (!trigger.isAttachedToWindow || trigger.width <= 0 || trigger.height <= 0) return null
        val playerLocation = IntArray(2)
        val triggerLocation = IntArray(2)
        binding.playerPopupHost.root.getLocationInWindow(playerLocation)
        trigger.getLocationInWindow(triggerLocation)
        val left = triggerLocation[0] - playerLocation[0]
        val top = triggerLocation[1] - playerLocation[1]
        return PlayerPopupPolicy.Rect(left, top, left + trigger.width, top + trigger.height)
    }

    private fun hidePlayerPopup(
        restoreFocus: Boolean = true,
        animate: Boolean = true,
    ) {
        val binding = _binding ?: return
        if (activePlayerPopup == null && !binding.playerPopupHost.root.isVisible) return
        val generation = ++popupGeneration
        val trigger = activePopupTrigger
        val host = binding.playerPopupHost
        val container = host.playerPopupPanelContainer
        val content = container.getChildAt(0)

        fun finish() {
            if (popupGeneration != generation) return
            activeSpeedPopupBinder?.dispose()
            activeSpeedPopupBinder = null
            activeQualityPopupBinder?.dispose()
            activeQualityPopupBinder = null
            activeVolumePopupBinder?.dispose()
            activeVolumePopupBinder = null
            activeMorePopupBinder?.dispose()
            activeMorePopupBinder = null
            activePopupLayoutListener?.let(host.root::removeOnLayoutChangeListener)
            activePopupLayoutListener = null
            activePopupTriggerLayoutListener?.let { listener ->
                trigger?.removeOnLayoutChangeListener(listener)
            }
            activePopupTriggerLayoutListener = null
            activePlayerPopup = null
            activePopupTrigger = null
            popupAnchorRect = null
            container.animate().cancel()
            content?.animate()?.cancel()
            container.removeAllViews()
            host.root.setOnClickListener(null)
            host.playerPopupScrim.animate().cancel()
            host.playerPopupScrim.isVisible = false
            host.root.visibility = View.GONE
            popupBackgroundAccessibility.forEach { (view, mode) -> view.importantForAccessibility = mode }
            popupBackgroundAccessibility = emptyList()
            if (restoreFocus) {
                trigger?.requestFocus()
                if (controllerAutoHide && controllerHideOnTouch && !binding.playerControls.progressBar.isPressed) {
                    binding.playerControls.root.removeCallbacks(controllerHideAction)
                    binding.playerControls.root.postDelayed(controllerHideAction, PLAYER_POPUP_CONTROLLER_HIDE_DELAY_MS)
                }
            }
        }

        if (animate && content != null && host.root.isVisible) {
            host.playerPopupScrim.animate().cancel()
            host.playerPopupScrim.animate()
                .alpha(0f)
                .setDuration(PLAYER_POPUP_CLOSE_MS)
                .start()
            container.animate().cancel()
            container.animate()
                .alpha(0f)
                .scaleX(PLAYER_POPUP_START_SCALE)
                .scaleY(PLAYER_POPUP_START_SCALE)
                .setInterpolator(android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f))
                .setDuration(PLAYER_POPUP_CLOSE_MS)
                .withLayer()
                .withEndAction(::finish)
                .start()
        } else {
            finish()
        }
    }

    fun showVolumeOverlay() {
        if (!isMaximized) return
        val current = getCurrentVolume() ?: (prefs.getInt(C.PLAYER_VOLUME, 100) / 100f)
        val panelWidth = PlayerPopupPolicy.panelWidthPx(
            binding.playerLayout.width,
            resources.displayMetrics.density,
        )
        if (panelWidth <= 0) return
        val popupBinding = LayoutPlayerVolumeOverlayBinding.inflate(
            layoutInflater,
            binding.playerPopupHost.playerPopupPanelContainer,
            false,
        )
        val binder = PlayerVolumePopupBinder(
            context = requireContext(),
            binding = popupBinding,
            state = volumeOverlayState,
            initialValue = current,
            dismissDelayMs = VOLUME_OVERLAY_DISMISS_MS,
            onVolumeChanged = ::changeVolume,
            onDismissRequested = { hidePlayerPopup() },
        ).also { it.bind() }
        showPlayerPopup(
            type = PlayerPopupType.VOLUME,
            trigger = binding.playerControls.volume,
            content = popupBinding.root,
            panelWidth = panelWidth,
        )
        activeVolumePopupBinder = binder
    }

    fun hideVolumeOverlay() {
        if (activePlayerPopup == PlayerPopupType.VOLUME) {
            hidePlayerPopup()
        }
    }

    fun getTranslateAllMessages(): Boolean? {
        return if (!requireArguments().getString(KEY_CHANNEL_ID).isNullOrBlank()) {
            chatFragment?.getTranslateAllMessages()
        } else null
    }

    fun saveTranslateAllMessagesUser() {
        requireArguments().getString(KEY_CHANNEL_ID)?.let {
            chatFragment?.toggleTranslateAllMessages(true)
            viewModel.saveTranslateAllMessagesUser(it)
        }
    }

    fun deleteTranslateAllMessagesUser() {
        requireArguments().getString(KEY_CHANNEL_ID)?.let {
            chatFragment?.toggleTranslateAllMessages(false)
            viewModel.deleteTranslateAllMessagesUser(it)
        }
    }

    fun toggleChatBar() {
        with(binding) {
            requireView().findViewById<LinearLayout>(R.id.messageView)?.let {
                if (it.isVisible) {
                    (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(chatLayout.windowToken, 0)
                    chatLayout.clearFocus()
                    if (videoType == STREAM && chatFragment?.emoteMenuIsVisible() == true) {
                        chatFragment?.toggleEmoteMenu(false)
                    }
                    it.visibility = View.GONE
                    requireContext().prefs().edit { putBoolean(C.KEY_CHAT_BAR_VISIBLE, false) }
                } else {
                    it.visibility = View.VISIBLE
                    requireContext().prefs().edit { putBoolean(C.KEY_CHAT_BAR_VISIBLE, true) }
                }
            }
        }
    }

    fun hideChat() {
        isChatOpen = false
        hideChatLayout()
        if (requireContext().prefs().getBoolean(C.PLAYER_CHATTOGGLE, true)) {
            binding.playerControls.toggleChat.apply {
                visibility = View.VISIBLE
                updateChatButtonIcon()
            }
        }
        requireContext().prefs().edit { putBoolean(C.KEY_CHAT_OPENED, false) }
    }

    fun showChat() {
        isChatOpen = true
        showChatLayout()
        if (requireContext().prefs().getBoolean(C.PLAYER_CHATTOGGLE, true)) {
            binding.playerControls.toggleChat.apply {
                visibility = View.VISIBLE
                updateChatButtonIcon()
            }
        }
        requireContext().prefs().edit { putBoolean(C.KEY_CHAT_OPENED, true) }
        if (requireView().findViewById<Button>(R.id.btnDown)?.isVisible == false) {
            requireView().findViewById<RecyclerView>(R.id.recyclerView)?.let { recyclerView ->
                recyclerView.adapter?.itemCount?.let { recyclerView.scrollToPosition(it - 1) }
            }
        }
    }

    private fun hideChatLayout() {
        with(binding) {
            playerLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                marginEnd = 0
            }
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(chatLayout.windowToken, 0)
            chatLayout.clearFocus()
            chatLayout.visibility = View.GONE
        }
    }

    private fun showChatLayout() {
        with(binding) {
            playerLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                marginEnd = chatWidthLandscape
            }
            chatLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                width = chatWidthLandscape
                height = ViewGroup.LayoutParams.MATCH_PARENT
                gravity = Gravity.END
            }
            chatLayout.visibility = View.VISIBLE
        }
    }

    fun setQualityText() {
        val label = getQualityMap()?.entries?.find { it.value == viewModel.quality }?.key
        activeMorePopupBinder?.setQuality(label)
        if (binding.playerControls.qualityValue.isVisible) {
            binding.playerControls.qualityValue.text = label
        }
    }

    fun updateViewerCount(viewerCount: Int?) {
        with(binding.playerControls) {
            if (viewerCount != null) {
                viewersText.text = TwitchApiHelper.formatCount(viewerCount, requireContext().prefs().getBoolean(C.UI_TRUNCATEVIEWCOUNT, true))
                if (requireContext().prefs().getBoolean(C.PLAYER_VIEWERICON, true)) {
                    viewersIcon.visibility = View.VISIBLE
                }
            } else {
                viewersText.text = null
                viewersIcon.visibility = View.GONE
            }
        }
    }

    fun updateLiveStatus(live: Boolean, serverTime: Long?, channelLogin: String?) {
        if (channelLogin == requireArguments().getString(KEY_CHANNEL_LOGIN)) {
            if (live) {
                restartPlayer()
            }
            updateUptime(serverTime?.times(1000))
        }
    }

    private fun updateUptime(uptimeMs: Long?) {
        with(binding.playerControls) {
            uptimeTimer.stop()
            if (uptimeMs != null && requireContext().prefs().getBoolean(C.PLAYER_SHOW_UPTIME, true)) {
                uptimeLayout.visibility = View.VISIBLE
                uptimeTimer.base = SystemClock.elapsedRealtime() + uptimeMs - System.currentTimeMillis()
                uptimeTimer.start()
                if (requireContext().prefs().getBoolean(C.PLAYER_VIEWERICON, true)) {
                    uptimeIcon.visibility = View.VISIBLE
                } else {
                    uptimeIcon.visibility = View.GONE
                }
            } else {
                uptimeLayout.visibility = View.GONE
            }
        }
    }

    fun updateStreamInfo(title: String?, gameId: String?, gameSlug: String?, gameName: String?) {
        binding.playerControls.title.apply {
            if (!title.isNullOrBlank() && requireContext().prefs().getBoolean(C.PLAYER_TITLE, true)) {
                text = title.trim()
                visibility = View.VISIBLE
                setOnClickListener {
                    Toast.makeText(requireContext(), title.trim(), Toast.LENGTH_SHORT).show()
                }
            } else {
                text = null
                visibility = View.GONE
            }
        }
        binding.playerControls.category.apply {
            if (!gameName.isNullOrBlank() && requireContext().prefs().getBoolean(C.PLAYER_CATEGORY, true)) {
                text = gameName
                visibility = View.VISIBLE
                setOnClickListener {
                    findNavController().navigate(
                        if (requireContext().prefs().getBoolean(C.UI_GAMEPAGER, true)) {
                            GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                gameId = gameId,
                                gameSlug = gameSlug,
                                gameName = gameName
                            )
                        } else {
                            GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                gameId = gameId,
                                gameSlug = gameSlug,
                                gameName = gameName
                            )
                        }
                    )
                    minimize()
                }
            } else {
                text = null
                visibility = View.GONE
            }
        }
    }

    fun restartPlayer() {
        if (viewModel.quality?.name != CHAT_ONLY_QUALITY) {
            loadStream()
        }
    }

    fun openViewerList() {
        requireArguments().getString(KEY_CHANNEL_LOGIN)?.let { login ->
            PlayerViewerListDialog.newInstance(login).show(childFragmentManager, "closeOnPip")
        }
    }

    fun showVodGames() {
        viewModel.gamesList.value?.let {
            PlayerGamesDialog.newInstance(it).show(childFragmentManager, "closeOnPip")
        }
    }

    fun checkBookmark() {
        requireArguments().getString(KEY_VIDEO_ID)?.let { viewModel.checkBookmark(it) }
    }

    fun saveBookmark() {
        viewModel.saveBookmark(
            filesDir = requireContext().filesDir.path,
            networkLibrary = requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
            helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext()),
            gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
            videoId = requireArguments().getString(KEY_VIDEO_ID),
            title = requireArguments().getString(KEY_TITLE),
            uploadDate = requireArguments().getString(KEY_CREATED_AT),
            durationSeconds = requireArguments().getInt(KEY_DURATION_SECONDS),
            type = requireArguments().getString(KEY_VIDEO_TYPE),
            animatedPreviewUrl = requireArguments().getString(KEY_VIDEO_ANIMATED_PREVIEW),
            channelId = requireArguments().getString(KEY_CHANNEL_ID),
            channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN),
            channelName = requireArguments().getString(KEY_CHANNEL_NAME),
            channelImage = requireArguments().getString(KEY_CHANNEL_IMAGE),
            thumbnail = requireArguments().getString(KEY_THUMBNAIL),
            gameId = requireArguments().getString(KEY_GAME_ID),
            gameSlug = requireArguments().getString(KEY_GAME_SLUG),
            gameName = requireArguments().getString(KEY_GAME_NAME),
        )
    }

    protected fun updateAvailableQualities(qualities: List<VideoQuality>?): Boolean {
        val selectableQualities = qualities?.toSelectableQualities(includeChatOnly = videoType == STREAM) ?: return false
        if (!selectableQualities.shouldReplaceCurrentQualities(viewModel.qualities)) {
            return false
        }
        viewModel.qualities = selectableQualities
        setDefaultQuality()
        changePlayerMode()
        if (viewModel.quality?.name == AUDIO_ONLY_QUALITY) {
            changeQuality(viewModel.quality)
        }
        return true
    }

    protected fun setDefaultQuality() {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val cellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val defaultQuality = if (cellular) {
            requireContext().prefs().getString(C.PLAYER_DEFAULT_CELLULAR_QUALITY, "saved")
        } else {
            requireContext().prefs().getString(C.PLAYER_DEFAULTQUALITY, "saved")
        }?.substringBefore(" ")
        viewModel.quality = when (defaultQuality) {
            "saved" -> {
                val savedQuality = requireContext().prefs().getString(C.PLAYER_QUALITY, "720p60")?.substringBefore(" ")
                when (savedQuality) {
                    AUTO_QUALITY -> viewModel.qualities?.find { it.name == AUTO_QUALITY }
                    AUDIO_ONLY_QUALITY -> viewModel.qualities?.find { it.name == AUDIO_ONLY_QUALITY }
                    CHAT_ONLY_QUALITY -> viewModel.qualities?.find { it.name == CHAT_ONLY_QUALITY }
                    else -> findQuality(savedQuality)
                }
            }
            AUTO_QUALITY -> viewModel.qualities?.find { it.name == AUTO_QUALITY }
            "Source" -> viewModel.qualities?.find { it.name != AUTO_QUALITY }
            AUDIO_ONLY_QUALITY -> viewModel.qualities?.find { it.name == AUDIO_ONLY_QUALITY }
            CHAT_ONLY_QUALITY -> viewModel.qualities?.find { it.name == CHAT_ONLY_QUALITY }
            else -> findQuality(defaultQuality)
        } ?: viewModel.qualities?.firstOrNull()
    }

    private fun findQuality(targetQualityString: String?): VideoQuality? {
        val targetQuality = targetQualityString?.split("p")
        return targetQuality?.getOrNull(0)?.takeWhile { it.isDigit() }?.toIntOrNull()?.let { targetResolution ->
            val targetFps = targetQuality.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 30
            val last = viewModel.qualities?.last { it.name != AUDIO_ONLY_QUALITY && it.name != CHAT_ONLY_QUALITY }
            viewModel.qualities?.find { qualityString ->
                val quality = qualityString.name?.split("p")
                val resolution = quality?.getOrNull(0)?.takeWhile { it.isDigit() }?.toIntOrNull()
                val fps = quality?.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 30
                resolution != null && ((targetResolution == resolution && targetFps >= fps) || targetResolution > resolution || qualityString == last)
            }
        }
    }

    fun changePlayerMode() {
        with(binding) {
            if (canEnterPictureInPicture()) {
                if (!controllerHideOnTouch && !controllerIsAnimating && controllerAutoHide && !binding.playerControls.progressBar.isPressed) {
                    playerControls.root.postDelayed(controllerHideAction, 3000)
                }
                controllerHideOnTouch = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
                    requireContext().prefs().getBoolean(C.PLAYER_PICTURE_IN_PICTURE, true)
                ) {
                    requireActivity().setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(true).build())
                }
            } else {
                controllerHideOnTouch = false
                showController(true)
                updateProgress()
                requireView().keepScreenOn = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                ) {
                    requireActivity().setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
                }
            }
        }
    }

    protected fun showController(force: Boolean = false) {
        // Don't show controls while a swipe gesture (seek/volume/brightness/speed) is in progress
        if (!force && isSwipeGestureInProgress) return
        if (!controllerIsAnimating) {
            if (!binding.playerControls.root.isVisible) {
                binding.playerControls.root.removeCallbacks(controllerHideAction)
                controllerAnimation = binding.playerControls.root.animate().apply {
                    alpha(1f)
                    setDuration(250L)
                    setListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                controllerIsAnimating = true
                                if (view != null) {
                                    binding.playerControls.root.visibility = View.VISIBLE
                                    updateChatButtonIcon()
                                }
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                controllerIsAnimating = false
                                setListener(null)
                                if (view != null && controllerAutoHide && controllerHideOnTouch && !binding.playerControls.progressBar.isPressed) {
                                    binding.playerControls.root.postDelayed(controllerHideAction, 3000)
                                }
                            }
                        }
                    )
                    start()
                }
                // Also show floating chat controls if floating chat is active
                if (isFloatingChatEnabled) {
                    binding.dragHandleZone.animate().alpha(1f).setDuration(250).start()
                }
            } else {
                binding.playerControls.root.removeCallbacks(controllerHideAction)
                if (controllerAutoHide && controllerHideOnTouch && !binding.playerControls.progressBar.isPressed) {
                    binding.playerControls.root.postDelayed(controllerHideAction, 3000)
                }
            }
        } else {
            if (force) {
                controllerAnimation?.cancel()
                binding.playerControls.root.removeCallbacks(controllerHideAction)
                binding.playerControls.root.alpha = 1f
                binding.playerControls.root.visibility = View.VISIBLE
                updateChatButtonIcon()
                // Also show floating chat controls if floating chat is active
                if (isFloatingChatEnabled) {
                    binding.dragHandleZone.alpha = 1f
                }
                if (controllerAutoHide && controllerHideOnTouch && !binding.playerControls.progressBar.isPressed) {
                    binding.playerControls.root.postDelayed(controllerHideAction, 3000)
                }
            }
        }
    }

    private fun hideController(force: Boolean = false) {
        if (!force) {
            maybeShowPinchHint()
        }
        if (!controllerIsAnimating && binding.playerControls.root.isVisible) {
            controllerAnimation = binding.playerControls.root.animate().apply {
                alpha(0f)
                setDuration(250L)
                setListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            controllerIsAnimating = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            controllerIsAnimating = false
                            setListener(null)
                            if (view != null) {
                                binding.playerControls.root.visibility = View.GONE
                            }
                        }
                    }
                )
                start()
            }
            // Also hide floating chat controls if floating chat is active
            if (isFloatingChatEnabled) {
                binding.dragHandleZone.animate().alpha(0f).setDuration(250).start()
            }
        } else {
            if (force) {
                controllerAnimation?.cancel()
                binding.playerControls.root.alpha = 0f
                binding.playerControls.root.visibility = View.GONE
                // Also hide floating chat controls if floating chat is active
                if (isFloatingChatEnabled) {
                    binding.dragHandleZone.alpha = 0f
                }
            }
        }
    }

    private fun showStatusBar() {
        WindowCompat.getInsetsController(
            requireActivity().window,
            requireActivity().window.decorView
        ).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun hideStatusBar() {
        WindowCompat.getInsetsController(
            requireActivity().window,
            requireActivity().window.decorView
        ).hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun enableBackground() {
        backgroundVisible = true
        binding.playerBackground.setBackgroundColor(
            if (isPortrait) {
                backgroundColor ?: MaterialColors.getColor(binding.playerBackground, com.google.android.material.R.attr.colorSurface).also { backgroundColor = it }
            } else {
                Color.BLACK
            }
        )
        binding.playerBackground.isClickable = true
    }

    private fun disableBackground() {
        backgroundVisible = false
        binding.playerBackground.setBackgroundColor(Color.TRANSPARENT)
        binding.playerBackground.isClickable = false
    }

    private fun getHorizontalInsets(windowInsets: WindowInsetsCompat?): Int {
        return if (windowInsets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && requireContext().prefs().getBoolean(C.PLAYER_ROUNDED_CORNER_PADDING, false)) {
                val rootWindowInsets = requireView().rootWindowInsets
                val topLeft = rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                val topRight = rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                val bottomLeft = rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                val bottomRight = rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
                val leftRadius = max(topLeft?.radius ?: 0, bottomLeft?.radius ?: 0)
                val rightRadius = max(topRight?.radius ?: 0, bottomRight?.radius ?: 0)
                if (requireContext().prefs().getBoolean(C.UI_DRAW_BEHIND_CUTOUTS, false)) {
                    leftRadius + rightRadius
                } else {
                    val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                    max(cutoutInsets.left, leftRadius) + max(cutoutInsets.right, rightRadius)
                }
            } else {
                if (requireContext().prefs().getBoolean(C.UI_DRAW_BEHIND_CUTOUTS, false)) {
                    0
                } else {
                    val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                    cutoutInsets.left + cutoutInsets.right
                }
            }
        } else 0
    }

    private fun getScaleValues(): Pair<Float, Float> {
        return if (isPortrait) {
            0.5f to 0.5f
        } else {
            0.3f to 0.325f
        }
    }

    fun getIsPortrait() = isPortrait

    fun reloadEmotes() = chatFragment?.reloadEmotes()

    fun isActive() = chatFragment?.isActive()

    fun disconnect() = chatFragment?.disconnect()

    fun reconnect() = chatFragment?.reconnect()

    fun secondViewIsHidden() = !binding.chatLayout.isVisible && isMaximized

    fun canEnterPictureInPicture(): Boolean {
        val quality = if (viewModel.restoreQuality) {
            viewModel.previousQuality
        } else {
            viewModel.quality
        }
        return quality?.name != AUDIO_ONLY_QUALITY && quality?.name != CHAT_ONLY_QUALITY
    }

    protected fun setPipActions(playing: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            requireContext().prefs().getBoolean(C.PLAYER_PICTURE_IN_PICTURE, true)
        ) {
            requireActivity().setPictureInPictureParams(
                PictureInPictureParams.Builder().apply {
                    setActions(listOf(
                        RemoteAction(
                            Icon.createWithResource(requireContext(), R.drawable.baseline_audiotrack_black_24),
                            getString(R.string.audio_only),
                            getString(R.string.audio_only),
                            PendingIntent.getBroadcast(
                                requireContext(),
                                REQUEST_CODE_AUDIO_ONLY,
                                Intent(MainActivity.INTENT_START_AUDIO_ONLY).setPackage(requireContext().packageName),
                                PendingIntent.FLAG_IMMUTABLE
                            )
                        ),
                        if (playing) {
                            RemoteAction(
                                Icon.createWithResource(requireContext(), R.drawable.baseline_pause_black_48),
                                getString(R.string.pause),
                                getString(R.string.pause),
                                PendingIntent.getBroadcast(
                                    requireContext(),
                                    REQUEST_CODE_PLAY_PAUSE,
                                    Intent(MainActivity.INTENT_PLAY_PAUSE_PLAYER).setPackage(requireContext().packageName),
                                    PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        } else {
                            RemoteAction(
                                Icon.createWithResource(requireContext(), R.drawable.baseline_play_arrow_black_48),
                                getString(R.string.resume),
                                getString(R.string.resume),
                                PendingIntent.getBroadcast(
                                    requireContext(),
                                    REQUEST_CODE_PLAY_PAUSE,
                                    Intent(MainActivity.INTENT_PLAY_PAUSE_PLAYER).setPackage(requireContext().packageName),
                                    PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        }
                    ))
                }.build()
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val isInPIPMode = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> requireActivity().isInPictureInPictureMode
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> !useController && isMaximized
            else -> false
        }
        if (isInPIPMode) {
            if (isPortrait) {
                binding.chatLayout.visibility = View.GONE
            } else {
                hideChatLayout()
            }
            useController = false
        }
    }

    override fun initialize() {
        if (requireArguments().getString(KEY_TYPE) != OFFLINE_VIDEO) {
            viewModel.isFollowingChannel(
                requireContext().tokenPrefs().getString(C.USER_ID, null),
                requireArguments().getString(KEY_CHANNEL_ID),
                requireArguments().getString(KEY_CHANNEL_LOGIN),
                requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                TwitchApiHelper.getGQLHeaders(requireContext(), true),
                TwitchApiHelper.getHelixHeaders(requireContext()),
            )
            if (videoType == VIDEO) {
                val videoId = requireArguments().getString(KEY_VIDEO_ID)
                if (!videoId.isNullOrBlank() && (requireContext().prefs().getBoolean(C.PLAYER_GAMESBUTTON, true) || requireContext().prefs().getBoolean(C.PLAYER_MENU_GAMES, false))) {
                    viewModel.loadGamesList(
                        videoId,
                        requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                        TwitchApiHelper.getGQLHeaders(requireContext()),
                        requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                    )
                }
            }
        }
    }

    protected fun startPlayer() {
        viewModel.started = true
        when (videoType) {
            STREAM -> {
                viewModel.useCustomProxy = requireContext().prefs().getBoolean(C.PLAYER_STREAM_PROXY, false)
                loadStream()
                viewModel.loadStreamInfo(
                    channelId = requireArguments().getString(KEY_CHANNEL_ID),
                    channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN),
                    viewerCount = requireArguments().getInt(KEY_VIEWER_COUNT).takeIf { it != -1 },
                    loop = requireContext().prefs().getBoolean(C.CHAT_DISABLE, false) ||
                            !requireContext().prefs().getBoolean(C.CHAT_PUBSUB_ENABLED, true) ||
                            (requireContext().prefs().getBoolean(C.CHAT_POINTS_COLLECT, true) &&
                                    !requireContext().tokenPrefs().getString(C.USER_ID, null).isNullOrBlank() &&
                                    !TwitchApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank()),
                    networkLibrary = requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                    helixHeaders = TwitchApiHelper.getHelixHeaders(requireContext()),
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                    enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
            }
            VIDEO -> {
                if (requireContext().prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                    val id = requireArguments().getString(KEY_VIDEO_ID)?.toLongOrNull()
                    if (id != null) {
                        viewModel.getVideoPosition(id)
                    } else {
                        playVideo((requireContext().prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, 0)
                    }
                } else {
                    if (requireArguments().getBoolean(KEY_IGNORE_SAVED_POSITION)) {
                        playVideo((requireContext().prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, requireArguments().getLong(KEY_OFFSET).takeIf { it != -1L } ?: 0)
                        requireArguments().putBoolean(KEY_IGNORE_SAVED_POSITION, false)
                        requireArguments().putLong(KEY_OFFSET, -1)
                    } else {
                        playVideo((requireContext().prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1, 0)
                    }
                }
            }
            CLIP -> {
                viewModel.loadClip(
                    networkLibrary = requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                    id = requireArguments().getString(KEY_CLIP_ID),
                    enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                )
            }
            OFFLINE_VIDEO -> {
                if (requireContext().prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                    viewModel.getOfflineVideoPosition(requireArguments().getInt(KEY_OFFLINE_VIDEO_ID))
                } else {
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.savedOfflineVideoPosition.value = 0
                    }
                }
            }
        }
    }

    private fun loadStream() {
        requireArguments().getString(KEY_CHANNEL_LOGIN)?.let { channelLogin ->
            val proxyUrl = requireContext().prefs().getString(C.PLAYER_PROXY_URL, "")
            if (viewModel.useCustomProxy && !proxyUrl.isNullOrBlank()) {
                startStream(proxyUrl.replace("\$channel", channelLogin))
            } else {
                if (viewModel.useCustomProxy) {
                    viewModel.useCustomProxy = false
                }
                viewModel.loadStreamResult(
                    networkLibrary = requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), requireContext().prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                    channelLogin = channelLogin,
                    randomDeviceId = requireContext().prefs().getBoolean(C.TOKEN_RANDOM_DEVICEID, true),
                    xDeviceId = requireContext().prefs().getString(C.TOKEN_XDEVICEID, "twitch-web-wall-mason"),
                    playerType = requireContext().prefs().getString(C.TOKEN_PLAYERTYPE, "site"),
                    supportedCodecs = requireContext().prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                    proxyPlaybackAccessToken = requireContext().prefs().getBoolean(C.PROXY_PLAYBACK_ACCESS_TOKEN, false),
                    proxyHost = requireContext().prefs().getString(C.PROXY_HOST, null),
                    proxyPort = requireContext().prefs().getString(C.PROXY_PORT, null)?.toIntOrNull(),
                    proxyUser = requireContext().prefs().getString(C.PROXY_USER, null),
                    proxyPassword = requireContext().prefs().getString(C.PROXY_PASSWORD, null),
                    enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false)
                )
            }
        }
    }

    protected fun playVideo(skipAccessToken: Boolean, playbackPosition: Long?) {
        if (skipAccessToken && !requireArguments().getString(KEY_VIDEO_ANIMATED_PREVIEW).isNullOrBlank()) {
            requireArguments().getString(KEY_VIDEO_ANIMATED_PREVIEW)?.let { preview ->
                val urls = TwitchApiHelper.getVideoUrlsFromPreview(preview, requireArguments().getString(KEY_VIDEO_TYPE), viewModel.backupQualities)
                val list = urls.map {
                    VideoQuality(it.key, null, it.value)
                }
                viewModel.qualities = list
                    .sortedByDescending {
                        it.name?.substringAfter("p", "")?.takeWhile { it.isDigit() }?.toIntOrNull()
                    }
                    .sortedByDescending {
                        it.name?.substringBefore("p", "")?.takeWhile { it.isDigit() }?.toIntOrNull()
                    }
                    .sortedByDescending {
                        it.name == "source"
                    }
                viewModel.quality = viewModel.qualities?.firstOrNull()
                viewModel.quality?.url
            }?.let { url ->
                startVideo(url, playbackPosition, false)
            }
        } else {
            viewModel.playbackPosition = playbackPosition
            viewModel.loadVideo(
                networkLibrary = requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), requireContext().prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                videoId = requireArguments().getString(KEY_VIDEO_ID),
                playerType = requireContext().prefs().getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live"),
                supportedCodecs = requireContext().prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        with(binding) {
            val wasPortrait = isPortrait
            isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
            // Restore brightness when switching to portrait
            if (isPortrait && !wasPortrait) {
                restoreBrightness()
            }
            hidePlayerPopup(restoreFocus = false, animate = false)
            if (isMaximized) {
                enableBackground()
            } else {
                disableBackground()
            }
            val isInPIPMode = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> requireActivity().isInPictureInPictureMode
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> !useController && isMaximized
                else -> false
            }
            if (!isInPIPMode) {
                (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(chatLayout.windowToken, 0)
                chatLayout.clearFocus()
                initLayout()
            }
            if (!isPortrait && isMaximized) {
                maybeShowGestureGuide()
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        with(binding) {
            if (isInPictureInPictureMode) {
                restoreBrightness()
                hidePlayerPopup(restoreFocus = false, animate = false)
                if (!isMaximized) {
                    isMaximized = true
                    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
                    if (videoType == STREAM && chatFragment?.emoteMenuIsVisible() == true) {
                        chatFragment?.toggleBackPressedCallback(true)
                    }
                    slidingLayout.translationX = 0f
                    slidingLayout.translationY = 0f
                    slidingLayout.scaleX = 1f
                    slidingLayout.scaleY = 1f
                }
                if (isPortrait) {
                    chatLayout.visibility = View.GONE
                } else {
                    hideChatLayout()
                }
                useController = false
                controllerAnimation?.cancel()
                binding.playerControls.root.alpha = 0f
                binding.playerControls.root.visibility = View.GONE
                // player dialog
                (childFragmentManager.findFragmentByTag("closeOnPip") as? BottomSheetDialogFragment)?.dismiss()
                // player chat message dialog
                (chatFragment?.childFragmentManager?.findFragmentByTag("messageDialog") as? BottomSheetDialogFragment)?.dismiss()
                (chatFragment?.childFragmentManager?.findFragmentByTag("replyDialog") as? BottomSheetDialogFragment)?.dismiss()
                (chatFragment?.childFragmentManager?.findFragmentByTag("imageDialog") as? BottomSheetDialogFragment)?.dismiss()
            } else {
                useController = true
            }
        }
    }

    override fun onStop() {
        restoreBrightness()
        super.onStop()
        binding.playerControls.root.removeCallbacks(controllerHideAction)
    }

    protected fun savePosition() {
        when (videoType) {
            VIDEO -> {
                if (requireContext().prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                    requireArguments().getString(KEY_VIDEO_ID)?.toLongOrNull()?.let { id ->
                        getCurrentPosition()?.let { position ->
                            viewModel.saveVideoPosition(id, position)
                        }
                    }
                }
            }
            OFFLINE_VIDEO -> {
                if (requireContext().prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                    getCurrentPosition()?.let { position ->
                        viewModel.saveOfflineVideoPosition(requireArguments().getInt(KEY_OFFLINE_VIDEO_ID), position)
                    }
                }
            }
        }
    }

    fun minimize() {
        with(binding) {
            isMaximized = false
            // Restore original brightness when minimizing
            restoreBrightness()
            hidePlayerPopup(restoreFocus = false, animate = false)
            // Hide floating chat when minimizing - it should only appear in fullscreen
            if (isFloatingChatEnabled) {
                floatingChatRoot.visibility = View.GONE
            }
            if (videoType == STREAM && chatFragment?.emoteMenuIsVisible() == true) {
                chatFragment?.toggleBackPressedCallback(false)
            }
            backPressedCallback.remove()
            useController = false
            hideController(true)
            applyMinimizedPlayerVisualState()
            fun animate() {
                val (minimizedScaleX, minimizedScaleY) = getScaleValues()
                val windowInsets = ViewCompat.getRootWindowInsets(requireView())
                val insets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val keyboardInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.let { if (it > 0) it - (insets?.bottom ?: 0) else it } ?: 0
                val scaledXDiff = (slidingLayout.width * (1f - minimizedScaleX)) / 2
                val scaledYDiff = (slidingLayout.height * (1f - minimizedScaleY)) / 2
                val navBarHeight = requireView().rootView.findViewById<LinearLayout>(R.id.navBarContainer)?.height?.takeIf { it > 0 }?.let { it - keyboardInsets } ?: (insets?.bottom ?: 0)
                val playerWidth = if (isPortrait) {
                    playerLayout.width
                } else {
                    slidingLayout.width - getHorizontalInsets(windowInsets)
                }
                val newX = slidingLayout.width - (insets?.right ?: 0) - (playerWidth * minimizedScaleX) - (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20F, resources.displayMetrics) * minimizedScaleX)
                val newY = slidingLayout.height - navBarHeight - (playerLayout.height * minimizedScaleY) - (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30F, resources.displayMetrics) * minimizedScaleY)
                slidingLayout.animate().apply {
                    translationX(0f - scaledXDiff - ((insets?.left ?: 0) * minimizedScaleX) + newX)
                    translationY(0f - scaledYDiff - ((insets?.top ?: 0) * minimizedScaleY) + newY)
                    scaleX(minimizedScaleX)
                    scaleY(minimizedScaleY)
                    setDuration(250L)
                    setListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                isAnimating = true
                                if (view != null) {
                                    disableBackground()
                                }
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                isAnimating = false
                                setListener(null)
                                activePointerId = -1
                            }
                        }
                    )
                    start()
                }
            }
            if (isPortrait) {
                chatLayout.visibility = View.GONE
                slidingLayout.doOnLayout {
                    animate()
                }
            } else {
                showStatusBar()
                hideChatLayout()
                slidingLayout.doOnPreDraw {
                    animate()
                }
                val activity = requireActivity()
                activity.lifecycleScope.launch {
                    delay(500L)
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    fun maximize() {
        with(binding) {
            isMaximized = true
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
            if (videoType == STREAM && chatFragment?.emoteMenuIsVisible() == true) {
                chatFragment?.toggleBackPressedCallback(true)
            }
            useController = true
            applyMaximizedPlayerVisualState()
            if (!controllerHideOnTouch) {
                showController(true)
                updateProgress()
            }
            if (isPortrait) {
                chatLayout.visibility = View.VISIBLE
            } else {
                hideStatusBar()
                // Show floating chat again if it was enabled
                if (isFloatingChatEnabled) {
                    floatingChatRoot.visibility = View.VISIBLE
                    chatLayout.visibility = View.GONE
                    playerLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                        marginEnd = 0
                    }
                } else if (PlayerChatModeHelper.shouldShowSidebarChat(isChatOpen, isFloatingChatEnabled)) {
                    showChatLayout()
                }
            }
            slidingLayout.animate().apply {
                translationX(0f)
                translationY(0f)
                scaleX(1f)
                scaleY(1f)
                setDuration(250L)
                setListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            isAnimating = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            isAnimating = false
                            setListener(null)
                            if (view != null) {
                                enableBackground()
                            }
                            activePointerId = -1
                        }
                    }
                )
                start()
            }
        }
    }

    fun showDownloadDialog() {
        if (viewModel.loaded.value) {
            when (videoType) {
                STREAM -> {
                    val qualities = viewModel.qualities?.filter { !it.url.isNullOrBlank() }
                    DownloadDialog.newStreamInstance(
                        id = requireArguments().getString(KEY_STREAM_ID),
                        channelId = requireArguments().getString(KEY_CHANNEL_ID),
                        channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN),
                        channelName = requireArguments().getString(KEY_CHANNEL_NAME),
                        channelImage = requireArguments().getString(KEY_CHANNEL_IMAGE),
                        gameId = requireArguments().getString(KEY_GAME_ID),
                        gameSlug = requireArguments().getString(KEY_GAME_SLUG),
                        gameName = requireArguments().getString(KEY_GAME_NAME),
                        title = requireArguments().getString(KEY_TITLE),
                        thumbnail = requireArguments().getString(KEY_THUMBNAIL),
                        createdAt = requireArguments().getString(KEY_STARTED_AT),
                        qualityNames = qualities?.map { it.name.toString() }?.toTypedArray(),
                        qualityCodecs = qualities?.map { it.codecs.toString() }?.toTypedArray(),
                        qualityUrls = qualities?.map { it.url.toString() }?.toTypedArray(),
                    ).show(childFragmentManager, null)
                }
                VIDEO -> {
                    downloadVideo()
                }
                CLIP -> {
                    val qualities = viewModel.qualities?.filter { !it.url.isNullOrBlank() }
                    DownloadDialog.newClipInstance(
                        id = requireArguments().getString(KEY_CLIP_ID),
                        channelId = requireArguments().getString(KEY_CHANNEL_ID),
                        channelLogin = requireArguments().getString(KEY_CHANNEL_LOGIN),
                        channelName = requireArguments().getString(KEY_CHANNEL_NAME),
                        channelImage = requireArguments().getString(KEY_CHANNEL_IMAGE),
                        gameId = requireArguments().getString(KEY_GAME_ID),
                        gameSlug = requireArguments().getString(KEY_GAME_SLUG),
                        gameName = requireArguments().getString(KEY_GAME_NAME),
                        title = requireArguments().getString(KEY_TITLE),
                        thumbnail = requireArguments().getString(KEY_THUMBNAIL),
                        createdAt = requireArguments().getString(KEY_CREATED_AT),
                        videoId = requireArguments().getString(KEY_VIDEO_ID),
                        videoOffsetSeconds = requireArguments().getInt(KEY_VIDEO_OFFSET_SECONDS),
                        qualityNames = qualities?.map { it.name.toString() }?.toTypedArray(),
                        qualityCodecs = qualities?.map { it.codecs.toString() }?.toTypedArray(),
                        qualityUrls = qualities?.map { it.url.toString() }?.toTypedArray(),
                    ).show(childFragmentManager, null)
                }
            }
        }
    }

    fun onSleepTimerChanged(durationMs: Long, hours: Int, minutes: Int, lockScreen: Boolean) {
        if (durationMs > 0L) {
            Toast.makeText(
                requireContext(),
                when {
                    hours == 0 -> getString(
                        R.string.playback_will_stop,
                        resources.getQuantityString(R.plurals.minutes, minutes, minutes)
                    )
                    minutes == 0 -> getString(
                        R.string.playback_will_stop,
                        resources.getQuantityString(R.plurals.hours, hours, hours)
                    )
                    else -> getString(
                        R.string.playback_will_stop_hours_minutes,
                        resources.getQuantityString(R.plurals.hours, hours, hours),
                        resources.getQuantityString(R.plurals.minutes, minutes, minutes)
                    )
                },
                Toast.LENGTH_LONG
            ).show()
        } else if (((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0) > 0L) {
            Toast.makeText(requireContext(), R.string.timer_canceled, Toast.LENGTH_LONG).show()
        }
        if (lockScreen != requireContext().prefs().getBoolean(C.SLEEP_TIMER_LOCK, false)) {
            requireContext().prefs().edit { putBoolean(C.SLEEP_TIMER_LOCK, lockScreen) }
        }
        (activity as? MainActivity)?.setSleepTimer(durationMs)
    }

    override fun onChange(requestCode: Int, index: Int, text: CharSequence, tag: String?) {
        when (requestCode) {
            REQUEST_CODE_QUALITY -> {
                selectQuality(tag)
            }
            REQUEST_CODE_SPEED -> {
                requireContext().prefs().getString(C.PLAYER_SPEED_LIST, "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0")?.split("\n")?.let { speeds ->
                    speeds.getOrNull(index)?.toFloatOrNull()?.let { speed ->
                        setPlaybackSpeed(speed)
                        requireContext().prefs().edit { putFloat(C.PLAYER_SPEED, speed) }
                        updatePlaybackSpeedUi(speed)
                    }
                }
            }
        }
    }

    override fun onIntegrityDialogCallback(callback: String?) {
        if (callback != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    when (callback) {
                        "refreshStream" -> {
                            requireArguments().getString(KEY_CHANNEL_LOGIN)?.let { channelLogin ->
                                viewModel.loadStreamResult(
                                    networkLibrary = requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                    gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), requireContext().prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true)),
                                    channelLogin = channelLogin,
                                    randomDeviceId = requireContext().prefs().getBoolean(C.TOKEN_RANDOM_DEVICEID, true),
                                    xDeviceId = requireContext().prefs().getString(C.TOKEN_XDEVICEID, "twitch-web-wall-mason"),
                                    playerType = requireContext().prefs().getString(C.TOKEN_PLAYERTYPE, "site"),
                                    supportedCodecs = requireContext().prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                                    proxyPlaybackAccessToken = requireContext().prefs().getBoolean(C.PROXY_PLAYBACK_ACCESS_TOKEN, false),
                                    proxyHost = requireContext().prefs().getString(C.PROXY_HOST, null),
                                    proxyPort = requireContext().prefs().getString(C.PROXY_PORT, null)?.toIntOrNull(),
                                    proxyUser = requireContext().prefs().getString(C.PROXY_USER, null),
                                    proxyPassword = requireContext().prefs().getString(C.PROXY_PASSWORD, null),
                                    enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false)
                                )
                            }
                            viewModel.isFollowingChannel(
                                requireContext().tokenPrefs().getString(C.USER_ID, null),
                                requireArguments().getString(KEY_CHANNEL_ID),
                                requireArguments().getString(KEY_CHANNEL_LOGIN),
                                requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                                requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                TwitchApiHelper.getHelixHeaders(requireContext()),
                            )
                        }
                        "refreshVideo" -> {
                            val videoId = requireArguments().getString(KEY_VIDEO_ID)
                            viewModel.loadVideo(
                                networkLibrary = requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext(), requireContext().prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)),
                                videoId = videoId,
                                playerType = requireContext().prefs().getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live"),
                                supportedCodecs = requireContext().prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264"),
                                enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                            )
                            viewModel.isFollowingChannel(
                                requireContext().tokenPrefs().getString(C.USER_ID, null),
                                requireArguments().getString(KEY_CHANNEL_ID),
                                requireArguments().getString(KEY_CHANNEL_LOGIN),
                                requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                                requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                TwitchApiHelper.getHelixHeaders(requireContext()),
                            )
                            if (!videoId.isNullOrBlank() && (requireContext().prefs().getBoolean(C.PLAYER_GAMESBUTTON, true) || requireContext().prefs().getBoolean(C.PLAYER_MENU_GAMES, false))) {
                                viewModel.loadGamesList(
                                    videoId,
                                    requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                    TwitchApiHelper.getGQLHeaders(requireContext()),
                                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                                )
                            }
                        }
                        "refreshClip" -> {
                            viewModel.loadClip(
                                networkLibrary = requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                gqlHeaders = TwitchApiHelper.getGQLHeaders(requireContext()),
                                id = requireArguments().getString(KEY_CLIP_ID),
                                enableIntegrity = requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                            )
                            viewModel.isFollowingChannel(
                                requireContext().tokenPrefs().getString(C.USER_ID, null),
                                requireArguments().getString(KEY_CHANNEL_ID),
                                requireArguments().getString(KEY_CHANNEL_LOGIN),
                                requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                                requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                TwitchApiHelper.getGQLHeaders(requireContext(), true),
                                TwitchApiHelper.getHelixHeaders(requireContext()),
                            )
                        }
                        "follow" -> viewModel.saveFollowChannel(
                            requireContext().tokenPrefs().getString(C.USER_ID, null),
                            requireArguments().getString(KEY_CHANNEL_ID),
                            requireArguments().getString(KEY_CHANNEL_LOGIN),
                            requireArguments().getString(KEY_CHANNEL_NAME),
                            requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                            requireContext().prefs().getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                            !requireContext().prefs().getBoolean(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true),
                            requireArguments().getString(KEY_STARTED_AT),
                            requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                            TwitchApiHelper.getGQLHeaders(requireContext(), true),
                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                        )
                        "unfollow" -> viewModel.deleteFollowChannel(
                            requireContext().tokenPrefs().getString(C.USER_ID, null),
                            requireArguments().getString(KEY_CHANNEL_ID),
                            requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                            requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                            TwitchApiHelper.getGQLHeaders(requireContext(), true),
                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                        )
                    }
                }
            }
        }
    }

    protected fun getStreamArguments(item: Stream): Bundle {
        return Bundle().apply {
            putString(KEY_TYPE, STREAM)
            putString(KEY_STREAM_ID, item.id)
            putString(KEY_CHANNEL_ID, item.channelId)
            putString(KEY_CHANNEL_LOGIN, item.channelLogin)
            putString(KEY_CHANNEL_NAME, item.channelName)
            putString(KEY_CHANNEL_IMAGE, item.channelImage)
            putString(KEY_GAME_ID, item.gameId)
            putString(KEY_GAME_SLUG, item.gameSlug)
            putString(KEY_GAME_NAME, item.gameName)
            putString(KEY_TITLE, item.title)
            putString(KEY_THUMBNAIL, item.thumbnail)
            putString(KEY_STARTED_AT, item.createdAt)
            putInt(KEY_VIEWER_COUNT, item.viewerCount ?: -1)
        }
    }

    protected fun getVideoArguments(item: Video, offset: Long?, ignoreSavedPosition: Boolean): Bundle {
        return Bundle().apply {
            putString(KEY_TYPE, VIDEO)
            putString(KEY_VIDEO_ID, item.id)
            putString(KEY_CHANNEL_ID, item.channelId)
            putString(KEY_CHANNEL_LOGIN, item.channelLogin)
            putString(KEY_CHANNEL_NAME, item.channelName)
            putString(KEY_CHANNEL_IMAGE, item.channelImage)
            putString(KEY_GAME_ID, item.gameId)
            putString(KEY_GAME_SLUG, item.gameSlug)
            putString(KEY_GAME_NAME, item.gameName)
            putString(KEY_TITLE, item.title)
            putString(KEY_THUMBNAIL, item.thumbnail)
            putString(KEY_CREATED_AT, item.createdAt)
            putInt(KEY_DURATION_SECONDS, item.durationSeconds ?: 0)
            putString(KEY_VIDEO_TYPE, item.type)
            putString(KEY_VIDEO_ANIMATED_PREVIEW, item.animatedPreviewURL)
            putLong(KEY_OFFSET, offset ?: -1L)
            putBoolean(KEY_IGNORE_SAVED_POSITION, ignoreSavedPosition)
        }
    }

    protected fun getClipArguments(item: Clip): Bundle {
        return Bundle().apply {
            putString(KEY_TYPE, CLIP)
            putString(KEY_CLIP_ID, item.id)
            putString(KEY_CHANNEL_ID, item.channelId)
            putString(KEY_CHANNEL_LOGIN, item.channelLogin)
            putString(KEY_CHANNEL_NAME, item.channelName)
            putString(KEY_PROFILE_IMAGE_URL, item.channelImageURL)
            putString(KEY_CHANNEL_IMAGE, item.channelImage)
            putString(KEY_GAME_ID, item.gameId)
            putString(KEY_GAME_SLUG, item.gameSlug)
            putString(KEY_GAME_NAME, item.gameName)
            putString(KEY_TITLE, item.title)
            putString(KEY_THUMBNAIL, item.thumbnail)
            putString(KEY_CREATED_AT, item.createdAt)
            putInt(KEY_DURATION_SECONDS, item.durationSeconds ?: 0)
            putString(KEY_VIDEO_ID, item.videoId)
            putInt(KEY_VIDEO_OFFSET_SECONDS, item.videoOffsetSeconds ?: -1)
            putString(KEY_VIDEO_ANIMATED_PREVIEW, item.videoAnimatedPreviewURL)
        }
    }

    protected fun getOfflineVideoArguments(item: OfflineVideo): Bundle {
        return Bundle().apply {
            putString(KEY_TYPE, OFFLINE_VIDEO)
            putInt(KEY_OFFLINE_VIDEO_ID, item.id)
            putString(KEY_URL, item.url)
            putString(KEY_CHAT_URL, item.chatUrl)
            putString(KEY_CHANNEL_ID, item.channelId)
            putString(KEY_CHANNEL_LOGIN, item.channelLogin)
            putString(KEY_CHANNEL_NAME, item.channelName)
            putString(KEY_CHANNEL_IMAGE, item.channelLogo)
            putString(KEY_GAME_ID, item.gameId)
            putString(KEY_GAME_SLUG, item.gameSlug)
            putString(KEY_GAME_NAME, item.gameName)
            putString(KEY_TITLE, item.name)
        }
    }

    override fun onDestroyView() {
        finalizePinchSurface()
        hidePlayerPopup(restoreFocus = false, animate = false)
        _binding?.playerLayout?.findViewById<View>(R.id.gestureFeedback)?.let { feedback ->
            feedback.animate().cancel()
            feedback.removeCallbacks(hideGestureRunnable)
        }
        // Restore original brightness when fragment is destroyed
        restoreBrightness()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        protected const val AUTO_QUALITY = "auto"
        protected const val AUDIO_ONLY_QUALITY = "audio_only"
        protected const val CHAT_ONLY_QUALITY = "chat_only"

        private const val REQUEST_CODE_QUALITY = 0
        private const val REQUEST_CODE_SPEED = 1
        private const val REQUEST_CODE_AUDIO_ONLY = 2
        private const val REQUEST_CODE_PLAY_PAUSE = 3

        private const val PINCH_SCALE_CLAIM_DEADZONE = 0.02f
        private const val PINCH_FEEDBACK_LINGER_MS = 400L
        private const val PINCH_SETTLE_MS = 240L
        private const val PINCH_SETTLE_EPSILON = 0.001f
        private const val VOLUME_OVERLAY_DISMISS_MS = 1500L
        private const val PINCH_HINT_LINGER_MS = 3000L
        private const val PLAYER_POPUP_OPEN_MS = 220L
        private const val PLAYER_POPUP_CLOSE_MS = 140L
        private const val PLAYER_POPUP_CONTROLLER_HIDE_DELAY_MS = 3000L
        private const val PLAYER_POPUP_START_SCALE = 0.97f

        internal const val STREAM = "stream"
        internal const val VIDEO = "video"
        internal const val CLIP = "clip"
        internal const val OFFLINE_VIDEO = "offlineVideo"

        protected const val KEY_TYPE = "type"
        protected const val KEY_STREAM_ID = "streamId"
        protected const val KEY_VIDEO_ID = "videoId"
        protected const val KEY_CLIP_ID = "clipId"
        protected const val KEY_OFFLINE_VIDEO_ID = "offlineVideoId"
        protected const val KEY_URL = "url"
        protected const val KEY_CHAT_URL = "chatUrl"
        protected const val KEY_CHANNEL_ID = "channelId"
        protected const val KEY_CHANNEL_LOGIN = "channelLogin"
        protected const val KEY_CHANNEL_NAME = "channelName"
        protected const val KEY_PROFILE_IMAGE_URL = "profileImageUrl"
        protected const val KEY_CHANNEL_IMAGE = "channelImage"
        protected const val KEY_GAME_ID = "gameId"
        protected const val KEY_GAME_SLUG = "gameSlug"
        protected const val KEY_GAME_NAME = "gameName"
        protected const val KEY_TITLE = "title"
        protected const val KEY_THUMBNAIL = "thumbnail"
        protected const val KEY_STARTED_AT = "startedAt"
        protected const val KEY_CREATED_AT = "createdAt"
        protected const val KEY_VIEWER_COUNT = "viewerCount"
        protected const val KEY_DURATION_SECONDS = "durationSeconds"
        protected const val KEY_VIDEO_TYPE = "videoType"
        protected const val KEY_VIDEO_OFFSET_SECONDS = "videoOffsetSeconds"
        protected const val KEY_VIDEO_ANIMATED_PREVIEW = "videoAnimatedPreview"
        protected const val KEY_OFFSET = "offset"
        protected const val KEY_IGNORE_SAVED_POSITION = "ignoreSavedPosition"
    }

    private fun createChatFragment(isFloating: Boolean): ChatFragment? {
        val fragment = when (videoType) {
            STREAM -> ChatFragment.newInstance(
                requireArguments().getString(KEY_CHANNEL_ID),
                requireArguments().getString(KEY_CHANNEL_LOGIN),
                requireArguments().getString(KEY_CHANNEL_NAME),
                requireArguments().getString(KEY_STREAM_ID)
            )
            VIDEO -> ChatFragment.newInstance(
                requireArguments().getString(KEY_CHANNEL_ID),
                requireArguments().getString(KEY_CHANNEL_LOGIN),
                requireArguments().getString(KEY_VIDEO_ID),
                0
            )
            CLIP -> ChatFragment.newInstance(
                requireArguments().getString(KEY_CHANNEL_ID),
                requireArguments().getString(KEY_CHANNEL_LOGIN),
                requireArguments().getString(KEY_VIDEO_ID),
                requireArguments().getInt(KEY_VIDEO_OFFSET_SECONDS).takeIf { it != -1 }
            )
            OFFLINE_VIDEO -> ChatFragment.newLocalInstance(
                requireArguments().getString(KEY_CHANNEL_ID),
                requireArguments().getString(KEY_CHANNEL_LOGIN),
                requireArguments().getString(KEY_CHAT_URL)
            )
            else -> null
        }
        fragment?.arguments?.putBoolean("isFloatingMode", isFloating)
        return fragment
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingChatInteraction() {
        with(binding) {
            floatingChatRoot.doOnLayout {
                if (isFloatingChatEnabled) restoreFloatingChatPosition()
            }

            dragHandleZone.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = floatingChatRoot.x - event.rawX
                        dY = floatingChatRoot.y - event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Direct property assignment is more efficient than animate() with duration 0
                        val parent = floatingChatRoot.parent as? View
                        val parentWidth = parent?.width ?: 0
                        val parentHeight = parent?.height ?: 0
                        val viewWidth = floatingChatRoot.width
                        val viewHeight = floatingChatRoot.height

                        val newX = (event.rawX + dX).coerceIn(0f, (parentWidth - viewWidth).coerceAtLeast(0).toFloat())
                        val newY = (event.rawY + dY).coerceIn(0f, (parentHeight - viewHeight).coerceAtLeast(0).toFloat())

                        floatingChatRoot.x = newX
                        floatingChatRoot.y = newY
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        saveFloatingChatPosition()
                        true
                    }
                    else -> false
                }
            }

            resizeHandleZone.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = floatingChatRoot.width
                        initialHeight = floatingChatRoot.height
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isResizing = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isResizing) {
                            val deltaX = event.rawX - initialTouchX
                            val deltaY = event.rawY - initialTouchY
                            
                            // DPI-aware min/max constraints
                            val density = resources.displayMetrics.density
                            val minSizePx = (150 * density).toInt() // 150dp minimum
                            val parent = floatingChatRoot.parent as? View
                            val maxWidthPx = ((parent?.width ?: 800) * 0.6).toInt() // 60% max width
                            val maxHeightPx = ((parent?.height ?: 600) * 0.8).toInt() // 80% max height
                            
                            val newWidth = (initialWidth + deltaX).toInt().coerceIn(minSizePx, maxWidthPx)
                            val newHeight = (initialHeight + deltaY).toInt().coerceIn(minSizePx, maxHeightPx)
                            
                            // Update size - top-left corner stays fixed, bottom-right follows finger
                            floatingChatRoot.updateLayoutParams {
                                width = newWidth
                                height = newHeight
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        isResizing = false
                        saveFloatingChatPosition()
                        true
                    }
                    else -> false
                }
            }

            highVisibilityToggle.setOnClickListener {
                toggleHighVisibility()
            }
        }
    }

    private fun updateChatButtonIcon() {
        if (prefs.getBoolean(C.PLAYER_CHATTOGGLE, true)) {
            binding.playerControls.toggleChat.apply {
                if (isFloatingChatEnabled) {
                    setImageResource(R.drawable.baseline_chat_bubble_outline_black_24)
                } else if (isChatOpen) {
                    setImageResource(R.drawable.baseline_speaker_notes_off_black_24)
                } else {
                    setImageResource(R.drawable.baseline_speaker_notes_black_24)
                }
            }
        }
    }

    override fun cycleChatMode() {
        // Check if floating chat is enabled in settings
        val floatingChatAllowed = prefs.getBoolean(C.FLOATING_CHAT_ENABLED, true)

        if (!isChatOpen && !isFloatingChatEnabled) {
            // Hidden -> Show sidebar chat
            showChat()
        } else if (isChatOpen && !isFloatingChatEnabled) {
            // Sidebar -> Enable floating chat (if allowed) or hide chat
            if (floatingChatAllowed) {
                toggleFloatingChat()
            } else {
                // Skip floating mode, go directly to hidden
                hideChat()
            }
        } else {
            // Floating -> Hide chat completely
            isFloatingChatEnabled = false
            binding.floatingChatRoot.visibility = View.GONE
            // Move chat view back to sidebar container (reparent, don't recreate)
            reparentChatView(toFloating = false)
            isChatOpen = false
            hideChatLayout()
            prefs.edit { putBoolean(C.KEY_CHAT_OPENED, false) }
        }
    }

    private fun toggleFloatingChat() {
        if (isPortrait) return

        isFloatingChatEnabled = !isFloatingChatEnabled

        // Reparent the chat view instead of recreating the fragment
        reparentChatView(toFloating = isFloatingChatEnabled)

        if (isFloatingChatEnabled) {
            // Hide sidebar chat and reset player margin
            binding.chatLayout.visibility = View.GONE
            binding.playerLayout.updateLayoutParams<FrameLayout.LayoutParams> {
                marginEnd = 0
            }
            // Show with fade-in animation
            binding.floatingChatRoot.alpha = 0f
            binding.floatingChatRoot.visibility = View.VISIBLE
            binding.floatingChatRoot.animate().alpha(1f).setDuration(200).start()
            // Start with drag handle hidden (synced with player controls)
            binding.dragHandleZone.alpha = if (binding.playerControls.root.isVisible) 1f else 0f
            restoreFloatingChatPosition()
            // Floating chat overlays video, so keep the surface dark in every app theme.
            val transparency = prefs.getInt(C.FLOATING_CHAT_TRANSPARENCY, 0)
            val alpha = (transparency * 255 / 100).coerceIn(0, 255)
            binding.floatingChatContainer.setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, alpha))
        } else {
            // Hide with fade-out animation
            binding.floatingChatRoot.animate().alpha(0f).setDuration(200)
                .withEndAction { binding.floatingChatRoot.visibility = View.GONE }.start()
            // Restore sidebar chat with proper layout
            isChatOpen = true
            showChatLayout()
        }
        updateChatButtonIcon()
    }

    /**
     * Reparent the chat fragment's view between sidebar and floating containers.
     * This preserves the chat connection and message history (no reconnection).
     */
    private fun reparentChatView(toFloating: Boolean) {
        val chatView = chatFragment?.view ?: return
        val currentParent = chatView.parent as? ViewGroup ?: return
        
        val targetContainer = if (toFloating) {
            binding.floatingChatContainer
        } else {
            binding.chatFragmentContainer
        }
        
        // Only reparent if not already in target container
        if (currentParent != targetContainer) {
            currentParent.removeView(chatView)
            targetContainer.addView(chatView)
        }
        // Update high visibility mode - should only apply to floating chat
        chatFragment?.updateHighVisibility(toFloating)
    }

    private fun saveFloatingChatPosition() {
        val channelId = requireArguments().getString(KEY_CHANNEL_ID) ?: return
        with(binding.floatingChatRoot) {
            prefs.edit {
                putFloat("floating_chat_x_$channelId", x)
                putFloat("floating_chat_y_$channelId", y)
                putInt("floating_chat_w_$channelId", width)
                putInt("floating_chat_h_$channelId", height)
            }
        }
    }

    private fun restoreFloatingChatPosition() {
        val channelId = requireArguments().getString(KEY_CHANNEL_ID) ?: return
        val x = prefs.getFloat("floating_chat_x_$channelId", -1f)
        val y = prefs.getFloat("floating_chat_y_$channelId", -1f)
        val w = prefs.getInt("floating_chat_w_$channelId", -1)
        val h = prefs.getInt("floating_chat_h_$channelId", -1)

        // DPI-aware default sizing - larger for tablets
        val density = resources.displayMetrics.density
        val screenWidthDp = resources.configuration.screenWidthDp
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        val defaultWidthDp = if (isTablet) 300 else 200
        val defaultHeightDp = if (isTablet) 400 else 280
        val defaultWidthPx = (defaultWidthDp * density).toInt()
        val defaultHeightPx = (defaultHeightDp * density).toInt()

        // Apply saved size or use defaults
        binding.floatingChatRoot.updateLayoutParams {
            width = if (w != -1) w else defaultWidthPx
            height = if (h != -1) h else defaultHeightPx
        }

        binding.floatingChatRoot.doOnLayout {
            val parent = binding.floatingChatRoot.parent as? View ?: return@doOnLayout
            val parentWidth = parent.width
            val parentHeight = parent.height
            val chatWidth = binding.floatingChatRoot.width
            val chatHeight = binding.floatingChatRoot.height

            if (x != -1f && y != -1f) {
                // Clamp position to keep chat within screen bounds
                val clampedX = x.coerceIn(0f, (parentWidth - chatWidth).coerceAtLeast(0).toFloat())
                val clampedY = y.coerceIn(0f, (parentHeight - chatHeight).coerceAtLeast(0).toFloat())
                binding.floatingChatRoot.x = clampedX
                binding.floatingChatRoot.y = clampedY
            } else {
                // Default position: top-right corner with some margin
                val marginPx = (16 * density).toInt()
                binding.floatingChatRoot.x = (parentWidth - chatWidth - marginPx).coerceAtLeast(0).toFloat()
                binding.floatingChatRoot.y = marginPx.toFloat()
            }
        }
    }

    private fun toggleHighVisibility() {
        val current = prefs.getBoolean(C.FLOATING_CHAT_HIGH_VISIBILITY, true)
        prefs.edit { putBoolean(C.FLOATING_CHAT_HIGH_VISIBILITY, !current) }

        // NOTE: High visibility mode requires fragment recreation because it affects
        // the chat adapter's rendering. This is an intentional trade-off - the user
        // explicitly toggled this setting, so a brief reconnection is acceptable.
        // For normal floating chat toggle (sidebar <-> floating), we use view reparenting
        // to preserve the chat connection.
        if (isFloatingChatEnabled && chatFragment != null) {
            childFragmentManager.beginTransaction().remove(chatFragment!!).commitNow()
            val fragment = createChatFragment(true)
            if (fragment != null) {
                childFragmentManager.beginTransaction().replace(R.id.floating_chat_container, fragment).commitNow()
                chatFragment = fragment
            }
        }
    }

    // PlayerGestureCallback implementation
    override val isControlsVisible get() = binding.playerControls.root.isVisible
    override val playerWidth get() = binding.playerLayout.width
    override val playerHeight get() = binding.playerLayout.height
    override val playerGestureInsets get() = gestureInsets
    override val windowAttributes: android.view.WindowManager.LayoutParams
        get() = android.view.WindowManager.LayoutParams().apply {
            copyFrom(requireActivity().window.attributes)
        }
    override fun setWindowAttributes(params: android.view.WindowManager.LayoutParams) { 
        if (params.screenBrightness != requireActivity().window.attributes.screenBrightness) {
            brightnessState.captureOriginal(requireActivity().window.attributes.screenBrightness)
        }
        requireActivity().window.attributes = params 
    }
    override fun getGestureFeedbackView() = binding.playerLayout.findViewById<View>(R.id.gestureFeedback)
    override fun getHideGestureRunnable() = hideGestureRunnable
    override fun isControllerHideOnTouch() = controllerHideOnTouch
    override fun showController() = showController(false)
    override fun hideController() = hideController(false)
    
    override fun onSwipeGestureStarted() {
        isSwipeGestureInProgress = true
    }

    override fun onSwipeGestureEnded() {
        isSwipeGestureInProgress = false
    }

    override fun claimSingleFingerGesture(owner: PlayerGestureArbiter.Owner): Boolean {
        return gestureArbiter.tryClaimSingleFinger(owner)
    }

    override fun claimDoubleTapChat(): Boolean {
        return gestureArbiter.onDoubleTapClaimed()
    }

    private fun twoFingerSpan(event: MotionEvent, pointerId1: Int, pointerId2: Int): Float {
        val index1 = event.findPointerIndex(pointerId1)
        val index2 = event.findPointerIndex(pointerId2)
        if (index1 == -1 || index2 == -1) {
            return -1f
        }
        val dx = event.getX(index1) - event.getX(index2)
        val dy = event.getY(index1) - event.getY(index2)
        return sqrt(dx * dx + dy * dy)
    }

    private fun resetPinchTracking() {
        pinchPointerId1 = -1
        pinchPointerId2 = -1
        pinchAnchorSpan = 0f
    }

    /**
     * Display mode used to begin a pinch: the canonical persisted mode.
     */
    internal open fun effectivePinchDisplayMode(): PlayerDisplayMode {
        return displayMode
    }

    private fun beginPinch(supersededDoubleTap: Boolean, event: MotionEvent) {
        if (supersededDoubleTap) {
            // The pinch's first-finger down was consumed as the second tap of a
            // double tap and already toggled chat; revert so an intentional
            // pinch does not toggle chat.
            cycleChatMode()
        }
        finalizePinchSurface()
        pinchController.begin(effectivePinchDisplayMode())
        pinchLastArmedTarget = null
        isSwipeGestureInProgress = true
        val cancelEvent = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        if (controlsVisibleAtGestureStart) {
            binding.playerControls.root.dispatchTouchEvent(cancelEvent)
        }
        controllerTapDetector?.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun updatePinch(event: MotionEvent) {
        if (pinchPointerId1 == -1 || pinchPointerId2 == -1 || pinchAnchorSpan <= 0f) {
            return
        }
        val span = twoFingerSpan(event, pinchPointerId1, pinchPointerId2)
        if (span <= 0f) {
            return
        }
        pinchController.update(span / pinchAnchorSpan).forEach(::applyPinchEvent)
    }

    private fun applyPinchEvent(pinchEvent: PinchDisplayModeController.Event) {
        when (pinchEvent) {
            is PinchDisplayModeController.Event.Preview -> {
                showPinchFeedback(pinchEvent.toward, pinchEvent.progress)
                applyPinchPreview(pinchEvent.from, pinchEvent.toward, pinchEvent.progress)
            }
            is PinchDisplayModeController.Event.NoPreview -> {
                showPinchFeedback(pinchEvent.from, 0f)
                // NoPreview arrives only before the gesture establishes a
                // direction, so the surface is still canonical and finalizing
                // here is harmless.
                finalizePinchSurface()
            }
            is PinchDisplayModeController.Event.Elastic -> {
                showPinchFeedback(pinchEvent.from, pinchEvent.deformation)
                applyPinchElastic(pinchEvent.from, pinchEvent.deformation)
            }
            is PinchDisplayModeController.Event.Armed -> {
                if (pinchEvent.target != pinchController.committedMode && pinchEvent.target != pinchLastArmedTarget) {
                    pinchLastArmedTarget = pinchEvent.target
                    if (prefs.getBoolean(C.PLAYER_GESTURES_HAPTIC, false)) {
                        try {
                            binding.playerLayout.findViewById<View>(R.id.gestureFeedback)
                                ?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        } catch (e: Exception) {
                            // Haptic failure must not block gesture completion.
                        }
                    }
                }
            }
            is PinchDisplayModeController.Event.Disarmed -> Unit
            is PinchDisplayModeController.Event.Commit -> {
                commitPinchDisplayMode(pinchEvent.mode)
                onSuccessfulPinch()
                // Show the completed bar briefly before the linger hide.
                showPinchFeedback(pinchEvent.mode, 1f)
                hidePinchFeedback()
            }
            is PinchDisplayModeController.Event.Restore -> {
                settlePinchPreview(pinchEvent.mode)
                hidePinchFeedback()
            }
            is PinchDisplayModeController.Event.Cancelled -> {
                settlePinchPreview(pinchEvent.mode)
                hidePinchFeedback()
            }
        }
    }

    /**
     * Continuous preview toward the target geometry. On API 24+ the committed
     * renderer remains unchanged while a uniform view scale interpolates to
     * the other geometry (Fit → ratio, Fill → inverse ratio). This avoids an
     * asynchronous resize-mode relayout while fingers are down. Stretch and
     * older devices step to the target renderer only once armed.
     */
    private fun applyPinchPreview(from: PlayerDisplayMode, toward: PlayerDisplayMode, progress: Float) {
        if (isPortrait || !isMaximized) {
            return
        }
        cancelPinchSettle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && from != PlayerDisplayMode.STRETCH) {
            val ratio = PlayerDisplayModePreviewer.fillToFitRatio(videoAspectRatio, binding.playerLayout.width, binding.playerLayout.height)
            val scale = PlayerDisplayModePreviewer.previewScale(from, toward, progress, ratio)
            binding.aspectRatioFrameLayout.resizeMode = from.resizeMode
            binding.aspectRatioFrameLayout.scaleX = scale
            binding.aspectRatioFrameLayout.scaleY = scale
        } else if (progress >= 1f) {
            binding.aspectRatioFrameLayout.resizeMode = toward.resizeMode
        }
    }

    /**
     * Elastic endpoint deformation for dead-direction pinches: the renderer
     * stays in the committed renderer and the view scales a restrained few
     * percent past its unit anchor, releasing through [settlePinchPreview]. Portrait,
     * non-maximized, and pre-N surfaces keep pill-only feedback (SurfaceView
     * transforms are unreliable before API 24).
     */
    private fun applyPinchElastic(from: PlayerDisplayMode, deformation: Float) {
        if (isPortrait || !isMaximized) {
            return
        }
        cancelPinchSettle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val scale = PlayerDisplayModePreviewer.elasticScale(from, deformation)
            binding.aspectRatioFrameLayout.resizeMode = from.resizeMode
            binding.aspectRatioFrameLayout.scaleX = scale
            binding.aspectRatioFrameLayout.scaleY = scale
        }
    }

    private fun cancelPinchSettle() {
        pinchSettleAnimator?.cancel()
        pinchSettleAnimator = null
    }

    /**
     * Single owner of canonical surface geometry: cancels any running settle
     * and applies the committed display mode's canonical resize mode and
     * unit scale. Every path that interrupts pinch state (new pinch, mode
     * selection, minimize/restore, orientation or PiP mode changes, view
     * destruction) must run through here so a partially transformed surface
     * can never survive; a cancelled settle finalizes through the animator's
     * end action, which simply re-enters this function.
     */
    private fun finalizePinchSurface() {
        pinchCommitGeneration++
        cancelPinchSettle()
        _binding?.aspectRatioFrameLayout?.let { frame ->
            frame.scaleX = 1f
            frame.scaleY = 1f
            if (!isPortrait && isMaximized) {
                frame.resizeMode = displayMode.resizeMode
            }
        }
    }

    /**
     * Neutral release: animate back toward the committed geometry instead of
     * snapping. Fit and Fill previews remain in their committed renderer, so
     * both settle to unit scale without a resize-mode handoff. Surfaces already
     * at unit scale finalize immediately. Stretch is not on the uniform
     * Fit/Fill continuum and pre-N previews step rather than scale, so both
     * fall back to an immediate canonical finalize.
     */
    private fun settlePinchPreview(committed: PlayerDisplayMode) {
        val frame = _binding?.aspectRatioFrameLayout ?: return
        if (isPortrait || !isMaximized ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
            committed == PlayerDisplayMode.STRETCH
        ) {
            finalizePinchSurface()
            return
        }
        val targetScale = 1f
        if (frame.scaleX <= 0f || abs(frame.scaleX - targetScale) < PINCH_SETTLE_EPSILON) {
            finalizePinchSurface()
            return
        }
        cancelPinchSettle()
        frame.resizeMode = committed.resizeMode
        pinchSettleAnimator = frame.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(PINCH_SETTLE_MS)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .withEndAction {
                pinchSettleAnimator = null
                finalizePinchSurface()
            }
            .also { it.start() }
    }

    private fun showPinchFeedback(target: PlayerDisplayMode, progress: Float) {
        val feedback = binding.playerLayout.findViewById<View>(R.id.gestureFeedback) ?: return
        val targetLabel = getString(
            when (target) {
                PlayerDisplayMode.FIT -> R.string.display_mode_fit
                PlayerDisplayMode.FILL -> R.string.display_mode_fill
                PlayerDisplayMode.STRETCH -> R.string.display_mode_stretch
            }
        )
        PlayerSurfacePolicy.presentFeedback(
            context = requireContext(),
            feedbackRoot = feedback,
            kind = PlayerGestureFeedbackKind.PINCH,
            surfaceWidthPx = binding.playerLayout.width,
            surfaceHeightPx = binding.playerLayout.height,
            insets = gestureInsets,
            presentation = PlayerGestureFeedbackState.pinchPresentation(
                surfaceClass = PlayerSurfacePolicy.classify(binding.playerLayout.width, resources.displayMetrics.density),
                progress = progress,
                toward = target,
                targetLabel = targetLabel,
            ),
            iconRes = R.drawable.baseline_aspect_ratio_black_24,
            a11yText = getString(R.string.gesture_feedback_pinch) + " \u00B7 " + targetLabel,
            hideRunnable = hideGestureRunnable,
        )
    }

    private fun hidePinchFeedback() {
        binding.playerLayout.findViewById<View>(R.id.gestureFeedback)?.let { feedback ->
            feedback.removeCallbacks(hideGestureRunnable)
            feedback.postDelayed(hideGestureRunnable, PINCH_FEEDBACK_LINGER_MS)
        }
    }

    /**
     * One-time gesture guide on the first eligible non-portrait maximized
     * playback; a versioned preference records dismissal.
     */
    fun maybeShowGestureGuide() {
        if (isPortrait || !isMaximized || gestureGuideShownThisSession) return
        if (activePlayerPopup != null) return
        if (childFragmentManager.findFragmentByTag("closeOnPip") != null) return
        if (PlayerGestureEducationState.shouldShowGuide(prefs.getInt(C.PLAYER_GESTURE_GUIDE_VERSION, 0))) {
            showGestureGuide()
        }
    }

    fun showGestureGuide(contextOverride: PlayerGestureGuideContext? = null) {
        val guideContext = contextOverride ?: if (videoType == STREAM) {
            PlayerGestureGuideContext.LIVE
        } else {
            PlayerGestureGuideContext.SEEKABLE
        }
        gestureGuideShownThisSession = true
        PlayerGestureGuideDialog.newInstance(guideContext).show(childFragmentManager, "closeOnPip")
    }

    fun onGestureGuideDismissed() {
        // The pinch hint becomes eligible in a later playback session; state
        // lives in preferences, nothing else to do here.
    }

    /**
     * Contextual pinch hint: shown at most once, only after the guide was
     * dismissed in an earlier session, and suppressed forever once a pinch
     * successfully changed the display mode. Never appears above a modal
     * player surface.
     */
    private fun maybeShowPinchHint() {
        if (isPortrait || !isMaximized) return
        if (activePlayerPopup != null) return
        if (childFragmentManager.findFragmentByTag("closeOnPip") != null) return
        if (!PlayerGestureEducationState.shouldShowPinchHint(
                guideStoredVersion = prefs.getInt(C.PLAYER_GESTURE_GUIDE_VERSION, 0),
                pinchHintShown = prefs.getBoolean(C.PLAYER_PINCH_HINT_SHOWN, false),
                pinchUsed = prefs.getBoolean(C.PLAYER_PINCH_USED, false),
                guideShownThisSession = gestureGuideShownThisSession,
            )
        ) {
            return
        }
        prefs.edit { putBoolean(C.PLAYER_PINCH_HINT_SHOWN, true) }
        val feedback = binding.playerLayout.findViewById<View>(R.id.gestureFeedback) ?: return
        PlayerSurfacePolicy.presentFeedback(
            context = requireContext(),
            feedbackRoot = feedback,
            kind = PlayerGestureFeedbackKind.PINCH,
            surfaceWidthPx = binding.playerLayout.width,
            surfaceHeightPx = binding.playerLayout.height,
            insets = gestureInsets,
            presentation = PlayerGestureFeedbackState.presentation(
                kind = PlayerGestureFeedbackKind.PINCH,
                surfaceClass = PlayerSurfacePolicy.classify(binding.playerLayout.width, resources.displayMetrics.density),
                text = getString(R.string.pinch_hint),
            ),
            iconRes = R.drawable.baseline_aspect_ratio_black_24,
            a11yText = getString(R.string.pinch_hint),
            hideRunnable = hideGestureRunnable,
            holdMs = PINCH_HINT_LINGER_MS,
        )
    }

    private fun onSuccessfulPinch() {
        if (!prefs.getBoolean(C.PLAYER_PINCH_USED, false)) {
            prefs.edit { putBoolean(C.PLAYER_PINCH_USED, true) }
        }
    }

    private fun finishPinch(cancelled: Boolean) {
        val terminal = if (cancelled) pinchController.cancel() else pinchController.release()
        applyPinchEvent(terminal)
        pinchLastArmedTarget = null
        isSwipeGestureInProgress = false
    }
    
    private fun restoreBrightness() {
        brightnessState.consumeRestoreBrightness()?.let { originalBrightness ->
            val lp = requireActivity().window.attributes
            lp.screenBrightness = originalBrightness
            requireActivity().window.attributes = lp
        }
    }
}

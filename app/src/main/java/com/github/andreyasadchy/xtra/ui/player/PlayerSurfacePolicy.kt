package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.github.andreyasadchy.xtra.R
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Classifies the active player surface and derives gesture-feedback placement
 * and presentation.
 *
 * All sizing is based on the measured player surface in density-independent
 * pixels, not on the display or the physical device, so the policy stays
 * correct in split-screen and resizable windows.
 */
enum class PlayerSurfaceClass {
    COMPACT,
    LARGE,
}

enum class PlayerGestureFeedbackKind {
    BRIGHTNESS,
    DEVICE_VOLUME,
    SEEK,
    PLAYBACK_SPEED,
    PINCH,
}

data class PlayerFeedbackPlacement(
    val gravity: Int,
    val maxWidthPx: Int,
    val marginStartPx: Int,
    val marginEndPx: Int,
    val topPaddingPx: Int,
    val fixedContainerWidthPx: Int?,
    val fixedContainerHeightPx: Int?,
    val verticalPill: Boolean,
)

object PlayerSurfacePolicy {

    const val LARGE_SURFACE_MIN_WIDTH_DP = 600
    const val CENTER_FEEDBACK_MAX_WIDTH_DP = 360
    const val EDGE_PILL_WIDTH_DP = 48
    const val EDGE_PILL_HEIGHT_DP = 144
    const val FEEDBACK_HOLD_MS = 800L
    const val FEEDBACK_FADE_MS = 150L
    private const val FEEDBACK_MARGIN_DP = 16
    private const val COMPACT_TOP_PADDING_DP = 24
    private const val EDGE_PILL_MAX_HEIGHT_FRACTION = 0.45f
    private const val HORIZONTAL_PILL_PADDING_H_DP = 16
    private const val HORIZONTAL_PILL_PADDING_V_DP = 8
    private const val EDGE_PILL_PADDING_H_DP = 6
    private const val EDGE_PILL_PADDING_V_DP = 10
    private const val ICON_SPACING_DP = 12

    fun classify(surfaceWidthPx: Int, density: Float): PlayerSurfaceClass {
        if (density <= 0f || surfaceWidthPx <= 0) {
            return PlayerSurfaceClass.COMPACT
        }
        return if (surfaceWidthPx / density >= LARGE_SURFACE_MIN_WIDTH_DP) {
            PlayerSurfaceClass.LARGE
        } else {
            PlayerSurfaceClass.COMPACT
        }
    }

    /**
     * Large brightness and device-volume feedback uses a compact vertical edge
     * pill; every other combination keeps the top-centered horizontal pill on
     * both compact and large surfaces.
     */
    fun placementFor(
        kind: PlayerGestureFeedbackKind,
        surfaceClass: PlayerSurfaceClass,
        density: Float,
        insetStartPx: Int = 0,
        insetEndPx: Int = 0,
        surfaceHeightPx: Int = 0,
    ): PlayerFeedbackPlacement {
        val margin = (FEEDBACK_MARGIN_DP * density).toInt()
        val verticalPill = surfaceClass == PlayerSurfaceClass.LARGE &&
            (kind == PlayerGestureFeedbackKind.BRIGHTNESS || kind == PlayerGestureFeedbackKind.DEVICE_VOLUME)
        if (!verticalPill) {
            return PlayerFeedbackPlacement(
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                maxWidthPx = (CENTER_FEEDBACK_MAX_WIDTH_DP * density).toInt(),
                marginStartPx = margin,
                marginEndPx = margin,
                topPaddingPx = (COMPACT_TOP_PADDING_DP * density).toInt(),
                fixedContainerWidthPx = null,
                fixedContainerHeightPx = null,
                verticalPill = false,
            )
        }
        val pillWidth = (EDGE_PILL_WIDTH_DP * density).toInt()
        val pillHeight = edgePillHeightPx(density, surfaceHeightPx)
        val atStart = kind == PlayerGestureFeedbackKind.BRIGHTNESS
        return PlayerFeedbackPlacement(
            gravity = Gravity.CENTER_VERTICAL or if (atStart) Gravity.START else Gravity.END,
            maxWidthPx = pillWidth,
            marginStartPx = if (atStart) margin + insetStartPx.coerceAtLeast(0) else margin,
            marginEndPx = if (atStart) margin else margin + insetEndPx.coerceAtLeast(0),
            topPaddingPx = 0,
            fixedContainerWidthPx = pillWidth,
            fixedContainerHeightPx = pillHeight,
            verticalPill = true,
        )
    }

    /**
     * The edge pill never covers more than [EDGE_PILL_MAX_HEIGHT_FRACTION] of
     * the player surface so short split-screen surfaces cannot clip it.
     */
    fun edgePillHeightPx(density: Float, surfaceHeightPx: Int): Int {
        val desired = (EDGE_PILL_HEIGHT_DP * density).toInt()
        if (surfaceHeightPx <= 0) {
            return desired
        }
        return desired.coerceAtMost((surfaceHeightPx * EDGE_PILL_MAX_HEIGHT_FRACTION).toInt())
    }

    fun applyPlacement(feedbackRoot: View, container: LinearLayout, placement: PlayerFeedbackPlacement) {
        if (feedbackRoot.parent is FrameLayout) {
            feedbackRoot.layoutParams = (feedbackRoot.layoutParams as? FrameLayout.LayoutParams)?.apply {
                gravity = placement.gravity
                // The included root is match-parent width. Horizontal margins
                // belong on the pill container; retaining them here only shifts
                // the coordinate space and leaves the pill centered.
                leftMargin = 0
                rightMargin = 0
            } ?: feedbackRoot.layoutParams
        }
        feedbackRoot.setPadding(
            feedbackRoot.paddingLeft,
            placement.topPaddingPx,
            feedbackRoot.paddingRight,
            feedbackRoot.paddingBottom,
        )
        container.layoutParams = (container.layoutParams as? FrameLayout.LayoutParams)?.apply {
            gravity = placement.gravity
            marginStart = placement.marginStartPx
            marginEnd = placement.marginEndPx
            width = placement.fixedContainerWidthPx ?: FrameLayout.LayoutParams.WRAP_CONTENT
            height = placement.fixedContainerHeightPx ?: FrameLayout.LayoutParams.WRAP_CONTENT
        } ?: container.layoutParams
        // Geometry is part of the presentation contract: orientation, child
        // gravity, minimum width, and padding are reset on every placement so
        // a prior gesture kind cannot contaminate the next one.
        container.orientation = if (placement.verticalPill) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        container.gravity = if (placement.verticalPill) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        container.minimumWidth = 0
        val density = container.resources.displayMetrics.density
        if (placement.verticalPill) {
            container.setPadding(
                (EDGE_PILL_PADDING_H_DP * density).toInt(),
                (EDGE_PILL_PADDING_V_DP * density).toInt(),
                (EDGE_PILL_PADDING_H_DP * density).toInt(),
                (EDGE_PILL_PADDING_V_DP * density).toInt(),
            )
        } else {
            container.setPadding(
                (HORIZONTAL_PILL_PADDING_H_DP * density).toInt(),
                (HORIZONTAL_PILL_PADDING_V_DP * density).toInt(),
                (HORIZONTAL_PILL_PADDING_H_DP * density).toInt(),
                (HORIZONTAL_PILL_PADDING_V_DP * density).toInt(),
            )
        }
    }

    /**
     * A visible pill visibly jumps when its geometry changes, so a placement
     * change must be applied while hidden; an already-hidden pill can
     * reposition freely, and an unchanged placement just refreshes content.
     */
    fun requiresCleanReposition(
        lastPlacement: PlayerFeedbackPlacement?,
        nextPlacement: PlayerFeedbackPlacement,
        isVisible: Boolean,
    ): Boolean {
        return isVisible && lastPlacement != nextPlacement
    }

    /**
     * Places, styles, and shows a gesture-feedback pill from a complete
     * presentation. Every visual property is written on every call, so no
     * visibility, orientation, width, progress, or text state can leak between
     * gesture kinds, then the shared idle-hold hide is scheduled. The last
     * applied placement is stored on the view itself so multiple player
     * instances never share state.
     */
    fun presentFeedback(
        context: Context,
        feedbackRoot: View,
        kind: PlayerGestureFeedbackKind,
        surfaceWidthPx: Int,
        surfaceHeightPx: Int,
        insets: androidx.core.graphics.Insets?,
        presentation: PlayerGestureFeedbackPresentation,
        iconRes: Int,
        a11yText: String?,
        hideRunnable: Runnable,
        holdMs: Long = FEEDBACK_HOLD_MS,
    ) {
        val container = feedbackRoot.findViewById<LinearLayout>(R.id.feedbackContainer) ?: return
        val icon = feedbackRoot.findViewById<ImageView>(R.id.feedbackIcon)
        val horizontalProgress = feedbackRoot.findViewById<LinearProgressIndicator>(R.id.feedbackProgress)
        val verticalLevel = feedbackRoot.findViewById<ProgressBar>(R.id.feedbackVerticalLevel)
        val text = feedbackRoot.findViewById<TextView>(R.id.feedbackText)
        val density = context.resources.displayMetrics.density
        val vertical = presentation.layout == PlayerGestureFeedbackLayout.EDGE_VERTICAL

        feedbackRoot.animate().cancel()
        val placement = placementFor(
            kind = kind,
            surfaceClass = classify(surfaceWidthPx, density),
            density = density,
            insetStartPx = insets?.left ?: 0,
            insetEndPx = insets?.right ?: 0,
            surfaceHeightPx = surfaceHeightPx,
        )
        val lastPlacement = feedbackRoot.getTag(R.id.feedbackPlacement) as? PlayerFeedbackPlacement
        if (requiresCleanReposition(lastPlacement, placement, feedbackRoot.isVisible)) {
            // Swap geometry while invisible so the pill cannot be seen
            // teleporting from one gesture's position to another's.
            feedbackRoot.removeCallbacks(hideRunnable)
            feedbackRoot.visibility = View.GONE
            feedbackRoot.alpha = 1f
        }
        applyPlacement(
            feedbackRoot = feedbackRoot,
            container = container,
            placement = placement,
        )
        feedbackRoot.setTag(R.id.feedbackPlacement, placement)

        icon?.setImageResource(iconRes)
        icon?.visibility = View.VISIBLE
        (icon?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            val spacing = (ICON_SPACING_DP * density).toInt()
            val trailing = if (vertical) 0 else spacing
            val bottom = if (vertical) spacing else 0
            // Relative margins must be written too: setMargins() alone does
            // not clear marginStart/marginEnd, and any stale relative margin
            // re-resolves over the absolute ones and shifts the icon.
            params.setMargins(0, 0, trailing, bottom)
            params.marginStart = 0
            params.marginEnd = trailing
            icon.layoutParams = params
        }
        horizontalProgress?.visibility = if (presentation.levelVisible && !vertical) View.VISIBLE else View.GONE
        horizontalProgress?.progress = presentation.level
        verticalLevel?.visibility = if (presentation.levelVisible && vertical) View.VISIBLE else View.GONE
        verticalLevel?.progress = presentation.level
        text?.visibility = if (presentation.text != null && !vertical) View.VISIBLE else View.GONE
        text?.text = presentation.text.orEmpty()
        text?.maxLines = 1

        feedbackRoot.contentDescription = a11yText
        feedbackRoot.alpha = 1f
        feedbackRoot.visibility = View.VISIBLE
        feedbackRoot.removeCallbacks(hideRunnable)
        feedbackRoot.postDelayed(hideRunnable, holdMs)
    }

    /**
     * Clears progress, text, and the content description after the overlay
     * hides so nothing persists into the next gesture even if a future
     * presentation forgets to set a field.
     */
    fun resetFeedback(feedbackRoot: View) {
        feedbackRoot.findViewById<LinearProgressIndicator>(R.id.feedbackProgress)?.progress = 0
        feedbackRoot.findViewById<ProgressBar>(R.id.feedbackVerticalLevel)?.progress = 0
        feedbackRoot.findViewById<TextView>(R.id.feedbackText)?.text = ""
        feedbackRoot.contentDescription = null
    }
}

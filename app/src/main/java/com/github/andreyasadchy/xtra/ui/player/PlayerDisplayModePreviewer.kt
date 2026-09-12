package com.github.andreyasadchy.xtra.ui.player

import kotlin.math.max
import kotlin.math.min
import kotlin.math.exp
import kotlin.math.ln

/**
 * Pure geometry for pinch display-mode previews.
 *
 * A gesture stays in its committed renderer's coordinate space. From Fit, a
 * uniform scale of [fillToFitRatio] reproduces Fill. From Fill, the inverse
 * scale reproduces Fit. Avoiding a resize-mode change while fingers are down
 * matters because AspectRatioFrameLayout applies that change through an
 * asynchronous layout pass, while view scale is immediate.
 *
 * Stretch is not on this uniform Fit/Fill continuum and is stepped only when
 * armed by the Fragment. When source and viewport aspects match, the ratio is
 * 1 and mode previews are label-only. Elastic endpoint feedback remains
 * visible because it is relative to the committed renderer's unit scale.
 */
object PlayerDisplayModePreviewer {

    const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f
    const val ELASTIC_MAX_SCALE_DELTA = 0.05f

    fun fillToFitRatio(videoAspectRatio: Float, viewportWidthPx: Int, viewportHeightPx: Int): Float {
        val aspect = if (videoAspectRatio > 0f) videoAspectRatio else DEFAULT_VIDEO_ASPECT_RATIO
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) {
            return 1f
        }
        val fittedWidth = min(viewportWidthPx.toFloat(), viewportHeightPx * aspect)
        val filledWidth = max(viewportWidthPx.toFloat(), viewportHeightPx * aspect)
        if (fittedWidth <= 0f) {
            return 1f
        }
        return (filledWidth / fittedWidth).coerceAtLeast(1f)
    }

    fun previewScale(from: PlayerDisplayMode, toward: PlayerDisplayMode, progress: Float, fillToFitRatio: Float): Float {
        val ratio = fillToFitRatio.coerceAtLeast(1f)
        val target = when {
            from == PlayerDisplayMode.FIT && toward == PlayerDisplayMode.FILL -> ratio
            from == PlayerDisplayMode.FILL && toward == PlayerDisplayMode.FIT -> 1f / ratio
            else -> 1f
        }
        return exp(ln(target) * progress.coerceIn(0f, 1f))
    }

    /**
     * Elastic endpoint deformation for a dead-direction pinch: a restrained
     * [ELASTIC_MAX_SCALE_DELTA] fraction away from the committed renderer's
     * unit scale, driven by the controller's normalized deformation.
     * Unreachable for Stretch, which always has a live target.
     */
    fun elasticScale(from: PlayerDisplayMode, deformation: Float): Float {
        val delta = ELASTIC_MAX_SCALE_DELTA * deformation.coerceIn(0f, 1f)
        return when (from) {
            PlayerDisplayMode.FIT -> 1f - delta
            PlayerDisplayMode.FILL -> 1f + delta
            PlayerDisplayMode.STRETCH -> 1f
        }
    }
}

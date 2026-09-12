package com.github.andreyasadchy.xtra.ui.player

/**
 * Pure presentation rules for the shared gesture-feedback overlay.
 *
 * A presentation fully describes the overlay for one gesture update. Applying
 * it resets icon, both level indicators, text, and container geometry, so a
 * prior gesture cannot leak visibility, orientation, progress, or placement
 * into the next one.
 */
enum class PlayerGestureFeedbackLayout {

    /** Compact top-centered pill: icon, horizontal level, value text. */
    COMPACT_HORIZONTAL,

    /** Wide top-centered pill for seek, speed, and pinch: icon and text; pinch adds a bar. */
    CENTERED_HORIZONTAL,

    /** Wide edge pill: icon above a vertical level, no visible text. */
    EDGE_VERTICAL,
}

data class PlayerGestureFeedbackPresentation(
    val layout: PlayerGestureFeedbackLayout,
    val levelVisible: Boolean,
    val level: Int,
    val text: String?,
)

object PlayerGestureFeedbackState {

    /** Neutral center of the directional pinch bar. */
    const val PINCH_LEVEL_NEUTRAL = 50

    fun clampLevel(level: Int): Int = level.coerceIn(0, 100)

    /**
     * Only large brightness and device-volume feedback uses the vertical edge
     * pill; every other combination stays horizontal so compact surfaces keep
     * the top-centered treatment.
     */
    fun layoutFor(kind: PlayerGestureFeedbackKind, surfaceClass: PlayerSurfaceClass): PlayerGestureFeedbackLayout {
        return when {
            surfaceClass == PlayerSurfaceClass.COMPACT -> PlayerGestureFeedbackLayout.COMPACT_HORIZONTAL
            kind == PlayerGestureFeedbackKind.BRIGHTNESS || kind == PlayerGestureFeedbackKind.DEVICE_VOLUME -> PlayerGestureFeedbackLayout.EDGE_VERTICAL
            else -> PlayerGestureFeedbackLayout.CENTERED_HORIZONTAL
        }
    }

    /**
     * Builds a complete presentation. [level] carries the 0-100 indicator value
     * and is only honored for gestures that show one; [text] is forced off on
     * the vertical edge pill, which communicates through icon and level only.
     */
    fun presentation(
        kind: PlayerGestureFeedbackKind,
        surfaceClass: PlayerSurfaceClass,
        level: Int? = null,
        text: String? = null,
    ): PlayerGestureFeedbackPresentation {
        val layout = layoutFor(kind, surfaceClass)
        val levelVisible = when (kind) {
            PlayerGestureFeedbackKind.SEEK, PlayerGestureFeedbackKind.PLAYBACK_SPEED -> false
            else -> level != null
        }
        return PlayerGestureFeedbackPresentation(
            layout = layout,
            levelVisible = levelVisible,
            level = if (levelVisible) clampLevel(level ?: 0) else 0,
            text = if (layout == PlayerGestureFeedbackLayout.EDGE_VERTICAL) null else text,
        )
    }

    /**
     * Directional pinch bar: half-full at neutral, growing to full as the
     * pinch arms Fill and emptying as it arms Fit, so the bar alone expresses
     * which way the pinch points. Stretch is never a pinch target; a neutral
     * preview from Stretch parks at the center.
     */
    fun pinchLevel(progress: Float, toward: PlayerDisplayMode): Int {
        val clamped = progress.coerceIn(0f, 1f)
        return when (toward) {
            PlayerDisplayMode.FILL -> clampLevel((PINCH_LEVEL_NEUTRAL + PINCH_LEVEL_NEUTRAL * clamped).toInt())
            PlayerDisplayMode.FIT -> clampLevel((PINCH_LEVEL_NEUTRAL - PINCH_LEVEL_NEUTRAL * clamped).toInt())
            PlayerDisplayMode.STRETCH -> PINCH_LEVEL_NEUTRAL
        }
    }

    /**
     * Pinch feedback always shows its determinate bar, including zero-progress
     * and no-preview states, so the Fit/Fill transition stays visible for the
     * entire active pinch.
     */
    fun pinchPresentation(
        surfaceClass: PlayerSurfaceClass,
        progress: Float,
        toward: PlayerDisplayMode,
        targetLabel: String,
    ): PlayerGestureFeedbackPresentation {
        return presentation(
            kind = PlayerGestureFeedbackKind.PINCH,
            surfaceClass = surfaceClass,
            level = pinchLevel(progress, toward),
            text = targetLabel,
        )
    }
}

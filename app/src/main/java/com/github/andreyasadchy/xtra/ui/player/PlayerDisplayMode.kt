package com.github.andreyasadchy.xtra.ui.player

import androidx.media3.ui.AspectRatioFrameLayout

/**
 * Canonical user-visible display modes for the non-portrait maximized player.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
enum class PlayerDisplayMode(val resizeMode: Int) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH(AspectRatioFrameLayout.RESIZE_MODE_FILL);

    companion object {
        const val FIT_NAME = "fit"
        const val FILL_NAME = "fill"
        const val STRETCH_NAME = "stretch"

        fun fromName(name: String?): PlayerDisplayMode? = when (name) {
            FIT_NAME -> FIT
            FILL_NAME -> FILL
            STRETCH_NAME -> STRETCH
            else -> null
        }
    }
}

package com.github.andreyasadchy.xtra.util.update

import kotlin.math.roundToInt

internal data class UpdateWindowBounds(val width: Int, val bodyMaxHeight: Int, val centered: Boolean)

/** Uses the host window, not physical display dimensions or the already-sized dialog. */
internal fun updateWindowBounds(width: Int, height: Int, density: Float): UpdateWindowBounds {
    val scale = density.takeIf { it.isFinite() && it > 0 } ?: 1f
    val availableWidth = width.coerceAtLeast(1)
    val availableHeight = height.coerceAtLeast(1)
    val gutter = (32 * scale).roundToInt().coerceAtLeast(0)
    val maxWidth = (560 * scale).roundToInt().coerceAtLeast(1)
    return UpdateWindowBounds(
        width = (availableWidth - gutter).coerceAtLeast(1).coerceAtMost(maxWidth).coerceAtMost(availableWidth),
        bodyMaxHeight = (availableHeight - gutter).coerceAtLeast(1).coerceAtMost(availableHeight),
        centered = availableWidth / scale >= 600f,
    )
}

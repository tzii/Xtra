package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * Shared palette for the player's control panels (Quality, Speed, Stream
 * volume, and More). One resolver keeps the four surfaces visually identical in both
 * themes; every panel applies these colors in code instead of XML defaults.
 */
data class PlayerPanelColors(
    val panel: Int,
    val onPanel: Int,
    val secondaryText: Int,
    val panelStroke: Int,
    val handle: Int,
    val primary: Int,
    val controlFill: Int,
    val selectedFill: Int,
    val onSelected: Int,
    val sliderActive: Int,
    val sliderInactive: Int,
)

object PlayerPanelTheme {

    fun resolve(context: Context): PlayerPanelColors {
        val fallbackSurface = if (isLightTheme(context)) Color.WHITE else Color.rgb(18, 18, 18)
        val panel = themeColor(context, com.google.android.material.R.attr.colorSurfaceContainer, fallbackSurface)
        val onPanel = themeColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
            if (ColorUtils.calculateLuminance(panel) > 0.5) Color.rgb(28, 28, 28) else Color.WHITE,
        )
        val primary = themeColor(context, androidx.appcompat.R.attr.colorPrimary, Color.rgb(0, 125, 202))
        val lightPanel = ColorUtils.calculateLuminance(panel) > 0.5
        val controlBlend = if (lightPanel) 0.08f else 0.16f
        val strokeAlpha = if (lightPanel) 0.16f else 0.22f
        // Selected options render as solid accent pills; their content color is
        // chosen by actual contrast, not a luminance threshold that can put
        // pale text on a mid-light accent (including the default lavender).
        val onPrimary = if (ColorUtils.calculateContrast(Color.BLACK, primary) >=
            ColorUtils.calculateContrast(Color.WHITE, primary)) Color.BLACK else Color.WHITE
        return PlayerPanelColors(
            panel = panel,
            onPanel = onPanel,
            // Secondary text sits on panels that overlay bright video; keep it
            // clearly readable instead of atmosphere-subtle, especially on the
            // short full-surface portrait sheets.
            secondaryText = ColorUtils.setAlphaComponent(onPanel, if (lightPanel) 176 else 210),
            panelStroke = ColorUtils.blendARGB(panel, onPanel, strokeAlpha),
            handle = ColorUtils.setAlphaComponent(onPanel, if (lightPanel) 96 else 128),
            primary = primary,
            controlFill = ColorUtils.blendARGB(panel, onPanel, controlBlend),
            selectedFill = primary,
            onSelected = onPrimary,
            sliderActive = primary,
            sliderInactive = ColorUtils.setAlphaComponent(onPanel, if (lightPanel) 52 else 64),
        )
    }

    private fun isLightTheme(context: Context): Boolean {
        val value = TypedValue()
        return context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, value, true) && value.data != 0
    }

    private fun themeColor(context: Context, attr: Int, fallback: Int): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(attr, value, true)) {
            return fallback
        }
        return if (value.resourceId != 0) {
            ContextCompat.getColor(context, value.resourceId)
        } else {
            value.data
        }
    }
}

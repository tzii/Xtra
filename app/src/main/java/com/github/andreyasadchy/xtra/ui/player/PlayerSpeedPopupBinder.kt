package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.text.TextPaint
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.children
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerSpeedPopupBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlin.math.abs

/** Binds Playback Speed content inside the shared player popup host. */
internal class PlayerSpeedPopupBinder(
    private val context: Context,
    private val binding: LayoutPlayerSpeedPopupBinding,
    initialSpeed: Float,
    private val panelWidthPx: Int,
    private val onSpeedChanged: (Float) -> Unit,
    private val onDismissRequested: () -> Unit,
) {

    companion object {
        private const val MIN_SPEED = 0.25f
        private const val MAX_SPEED = 8.0f
        private const val MAX_PRESET_SPEED = 4.0f
        private const val SPEED_STEP = 0.05f
        private const val DEFAULT_PRESETS = "0.25\n0.5\n0.75\n1.0\n1.25\n1.5\n1.75\n2.0\n3.0\n4.0\n8.0"
    }

    private val density = context.resources.displayMetrics.density
    private val colors = PlayerPanelTheme.resolve(context)
    private var selectedSpeed = initialSpeed.coerceIn(MIN_SPEED, MAX_SPEED)

    fun bind() {
        applyTheme()
        binding.speedPopupClose.setOnClickListener { onDismissRequested() }
        binding.speedSlider.value = selectedSpeed
        buildPresetRows(loadSpeedPresets())
        updateSpeedDisplay(selectedSpeed)

        binding.speedSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) applySpeed(value, save = false)
        }
        binding.speedSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit

            override fun onStopTrackingTouch(slider: Slider) {
                saveSpeed(slider.value)
            }
        })
        binding.btnDecreaseSpeed.setOnClickListener {
            applySpeed((selectedSpeed - SPEED_STEP).coerceAtLeast(MIN_SPEED))
        }
        binding.btnIncreaseSpeed.setOnClickListener {
            applySpeed((selectedSpeed + SPEED_STEP).coerceAtMost(MAX_SPEED))
        }
    }

    fun dispose() {
        binding.speedPopupClose.setOnClickListener(null)
        binding.speedSlider.clearOnChangeListeners()
        binding.speedSlider.clearOnSliderTouchListeners()
        binding.btnDecreaseSpeed.setOnClickListener(null)
        binding.btnIncreaseSpeed.setOnClickListener(null)
        binding.speedPresetRows.removeAllViews()
    }

    private fun applyTheme() {
        with(binding) {
            speedPopupCard.setCardBackgroundColor(colors.panel)
            speedPopupCard.setStrokeColor(colors.panelStroke)
            speedPopupTitle.setTextColor(colors.onPanel)
            currentSpeedText.setTextColor(colors.onPanel)
            speedPopupClose.imageTintList = ColorStateList.valueOf(colors.secondaryText)
            btnDecreaseSpeed.background = controlRipple(ovalDrawable(colors.controlFill), ovalDrawable(Color.WHITE))
            btnIncreaseSpeed.background = controlRipple(ovalDrawable(colors.controlFill), ovalDrawable(Color.WHITE))
            btnDecreaseSpeed.imageTintList = ColorStateList.valueOf(colors.onPanel)
            btnIncreaseSpeed.imageTintList = ColorStateList.valueOf(colors.onPanel)
            speedSlider.thumbTintList = ColorStateList.valueOf(colors.sliderActive)
            speedSlider.trackActiveTintList = ColorStateList.valueOf(colors.sliderActive)
            speedSlider.trackInactiveTintList = ColorStateList.valueOf(colors.sliderInactive)
            speedSlider.haloTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
    }

    private fun loadSpeedPresets(): List<Float> {
        val saved = context.prefs()
            .getString(C.PLAYER_SPEED_LIST, DEFAULT_PRESETS)
            ?.split("\n")
            ?.mapNotNull { it.toFloatOrNull() }
            .orEmpty()
        val defaults = DEFAULT_PRESETS.split("\n").mapNotNull { it.toFloatOrNull() }
        return (saved + defaults)
            .distinct()
            .sorted()
            .filter { it in MIN_SPEED..MAX_PRESET_SPEED }
    }

    private fun buildPresetRows(speeds: List<Float>) {
        var presetsPerRow = when {
            panelWidthPx >= dp(288f) -> 5
            panelWidthPx >= dp(232f) -> 4
            else -> 3
        }
        val horizontalGap = dp(6f)
        val availableWidth = panelWidthPx - dp(24f)
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * context.resources.displayMetrics.scaledDensity
        }
        val minimumWidth = maxOf(dp(48f).toFloat(), speeds.maxOf { labelPaint.measureText(formatPreset(it)) } + dp(12f))
        while (presetsPerRow > 1 && (availableWidth - horizontalGap * (presetsPerRow - 1)) / presetsPerRow < minimumWidth) {
            presetsPerRow--
        }
        val presetWidth = (availableWidth - horizontalGap * (presetsPerRow - 1)) / presetsPerRow
        val presetHeight = maxOf(dp(48f), labelPaint.fontMetricsInt.run { descent - ascent } + dp(12f))
        binding.speedPresetRows.removeAllViews()

        speeds.chunked(presetsPerRow).forEachIndexed { rowIndex, rowSpeeds ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            binding.speedPresetRows.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (rowIndex > 0) topMargin = dp(6f)
                },
            )

            rowSpeeds.forEachIndexed { chipIndex, speed ->
                val preset = TextView(context).apply {
                    tag = speed
                    text = formatPreset(speed)
                    background = presetBackground(colors.controlFill, colors.selectedFill)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    isClickable = true
                    isFocusable = true
                    minHeight = dp(48f)
                    setTextColor(
                        ColorStateList(
                            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                            intArrayOf(colors.onSelected, colors.onPanel),
                        ),
                    )
                    textSize = 14f
                    setOnClickListener {
                        applySpeed(speed)
                        onDismissRequested()
                    }
                }
                row.addView(
                    preset,
                    LinearLayout.LayoutParams(presetWidth, presetHeight).apply {
                        if (chipIndex > 0) marginStart = horizontalGap
                    },
                )
            }
        }
    }

    private fun applySpeed(speed: Float, save: Boolean = true) {
        selectedSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        updateSpeedDisplay(selectedSpeed)
        onSpeedChanged(selectedSpeed)
        if (abs(binding.speedSlider.value - selectedSpeed) >= 0.001f) {
            binding.speedSlider.value = selectedSpeed
        }
        if (save) saveSpeed(selectedSpeed)
    }

    private fun updateSpeedDisplay(speed: Float) {
        binding.currentSpeedText.text = String.format(Locale.US, "%.2fx", speed)
        binding.speedPresetRows.children.forEach { row ->
            (row as? ViewGroup)?.children?.forEach { child ->
                val preset = child as? TextView ?: return@forEach
                val presetSpeed = preset.tag as? Float
                preset.isSelected = presetSpeed != null && abs(presetSpeed - speed) < 0.01f
            }
        }
    }

    private fun saveSpeed(speed: Float) {
        context.prefs().edit { putFloat(C.PLAYER_SPEED, speed) }
    }

    private fun formatPreset(speed: Float): String {
        return if (speed % 1f == 0f) "${speed.toInt()}.0" else speed.toString()
    }

    private fun ovalDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun presetBackground(normalColor: Int, selectedColor: Int) = controlRipple(
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), roundedDrawable(selectedColor, 20f))
            addState(intArrayOf(), roundedDrawable(normalColor, 20f))
        },
        roundedDrawable(Color.WHITE, 20f),
    )

    private fun controlRipple(content: android.graphics.drawable.Drawable, mask: android.graphics.drawable.Drawable) =
        android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(colors.onPanel, 48)),
            content,
            mask,
        )

    private fun roundedDrawable(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Float): Int = (value * density).toInt()
}

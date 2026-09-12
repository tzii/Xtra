package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerVolumeOverlayBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.slider.Slider

/** Owns the stream-volume controls while they are attached to the player popup host. */
internal class PlayerVolumePopupBinder(
    private val context: Context,
    private val binding: LayoutPlayerVolumeOverlayBinding,
    private val state: PlayerVolumeOverlayState,
    initialValue: Float,
    private val dismissDelayMs: Long,
    private val onVolumeChanged: (Float) -> Unit,
    private val onDismissRequested: () -> Unit,
) {
    private var currentValue = initialValue.coerceIn(0f, 1f)
    private val dismissRunnable = Runnable(onDismissRequested)

    private val changeListener = Slider.OnChangeListener { _, value, fromUser ->
        if (fromUser) {
            state.remember(value.toInt())
            currentValue = (value / 100f).coerceIn(0f, 1f)
            onVolumeChanged(currentValue)
            render(scheduleDismiss = true)
        }
    }

    private val touchListener = object : Slider.OnSliderTouchListener {
        override fun onStartTrackingTouch(slider: Slider) {
            binding.root.removeCallbacks(dismissRunnable)
        }

        override fun onStopTrackingTouch(slider: Slider) {
            val percent = slider.value.toInt().coerceIn(0, 100)
            context.prefs().edit { putInt(C.PLAYER_VOLUME, percent) }
            currentValue = percent / 100f
            render(scheduleDismiss = true)
        }
    }

    fun bind() {
        state.remember((currentValue * 100).toInt())
        applyTheme()
        binding.volumeOverlaySlider.addOnChangeListener(changeListener)
        binding.volumeOverlaySlider.addOnSliderTouchListener(touchListener)
        binding.volumeOverlayMute.setOnClickListener {
            val target = state.targetAfterToggle(binding.volumeOverlaySlider.value.toInt()).coerceIn(0, 100)
            currentValue = target / 100f
            onVolumeChanged(currentValue)
            context.prefs().edit { putInt(C.PLAYER_VOLUME, target) }
            render(scheduleDismiss = true)
        }
        render(scheduleDismiss = true)
    }

    fun dispose() {
        binding.root.removeCallbacks(dismissRunnable)
        binding.volumeOverlaySlider.removeOnChangeListener(changeListener)
        binding.volumeOverlaySlider.removeOnSliderTouchListener(touchListener)
        binding.volumeOverlayMute.setOnClickListener(null)
    }

    private fun render(scheduleDismiss: Boolean) {
        binding.volumeOverlaySlider.value = currentValue * 100f
        binding.volumeOverlayPercent.text = "${(currentValue * 100).toInt()}%"
        binding.volumeOverlayMute.setImageResource(
            if (currentValue <= 0f) R.drawable.baseline_volume_off_black_24
            else R.drawable.baseline_volume_up_black_24,
        )
        binding.root.removeCallbacks(dismissRunnable)
        if (scheduleDismiss) {
            binding.root.postDelayed(dismissRunnable, dismissDelayMs)
        }
    }

    private fun applyTheme() {
        val colors = PlayerPanelTheme.resolve(context)
        val density = context.resources.displayMetrics.density
        binding.volumeOverlayCard.apply {
            setCardBackgroundColor(colors.panel)
            strokeWidth = density.toInt().coerceAtLeast(1)
            setStrokeColor(colors.panelStroke)
        }
        binding.volumeOverlayTitle.setTextColor(colors.onPanel)
        binding.volumeOverlayPercent.setTextColor(colors.onPanel)
        binding.volumeOverlayMute.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.controlFill)
        }
        binding.volumeOverlayMute.imageTintList = ColorStateList.valueOf(colors.onPanel)
        binding.volumeOverlaySlider.thumbTintList = ColorStateList.valueOf(colors.sliderActive)
        binding.volumeOverlaySlider.trackActiveTintList = ColorStateList.valueOf(colors.sliderActive)
        binding.volumeOverlaySlider.trackInactiveTintList = ColorStateList.valueOf(colors.sliderInactive)
        binding.volumeOverlaySlider.haloTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    }
}

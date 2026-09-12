package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerQualityPopupBinding
import com.github.andreyasadchy.xtra.model.VideoQuality
import java.util.Locale

/** Binds Quality content inside the shared player popup host. */
internal class PlayerQualityPopupBinder(
    private val context: Context,
    private val binding: LayoutPlayerQualityPopupBinding,
    qualities: List<VideoQuality>,
    private val selectedTag: String?,
    private val panelWidthPx: Int,
    private val onQualitySelected: (String) -> Unit,
    private val onDismissRequested: () -> Unit,
) {

    companion object {
        private const val PRIMARY_LABEL_TEXT_SIZE_SP = 14f
    }

    private val density = context.resources.displayMetrics.density
    private val colors = PlayerPanelTheme.resolve(context)
    private val options = PlayerQualityPopupModel.build(
        qualities = qualities,
        labels = PlayerQualityPopupLabels(
            auto = context.getString(R.string.auto),
            source = context.getString(R.string.source),
            audioOnly = context.getString(R.string.audio_only),
            chatOnly = context.getString(R.string.chat_only),
        ),
    )

    fun bind() {
        applyTheme()
        binding.qualityPopupClose.setOnClickListener { onDismissRequested() }
        val video = options.filter { it.kind == PlayerQualityPopupOption.Kind.VIDEO }
        val utilities = options.filter { it.kind != PlayerQualityPopupOption.Kind.VIDEO }
        val maxVideoColumns = if (panelWidthPx >= dp(320f)) 4 else 3
        buildGrid(
            binding.qualityVideoRows,
            video,
            maxColumns = maxVideoColumns,
            stableCodecHeight = video.any { it.codecLabel != null },
        )
        if (utilities.isNotEmpty()) {
            binding.qualityUtilitySection.visibility = View.VISIBLE
            buildGrid(
                binding.qualityUtilityRows,
                utilities,
                maxColumns = 2,
                stableCodecHeight = false,
            )
        } else {
            binding.qualityUtilitySection.visibility = View.GONE
        }
    }

    fun dispose() {
        binding.qualityPopupClose.setOnClickListener(null)
        binding.qualityVideoRows.removeAllViews()
        binding.qualityUtilityRows.removeAllViews()
    }

    private fun applyTheme() {
        binding.qualityPopupCard.setCardBackgroundColor(colors.panel)
        binding.qualityPopupCard.setStrokeColor(colors.panelStroke)
        binding.qualityPopupTitle.setTextColor(colors.onPanel)
        binding.videoQualitySectionTitle.setTextColor(colors.secondaryText)
        binding.audioChatSectionTitle.setTextColor(colors.secondaryText)
        binding.qualityPopupClose.imageTintList = ColorStateList.valueOf(colors.secondaryText)
    }

    private fun buildGrid(
        target: LinearLayout,
        entries: List<PlayerQualityPopupOption>,
        maxColumns: Int,
        stableCodecHeight: Boolean,
    ) {
        target.removeAllViews()
        if (entries.isEmpty()) return
        val horizontalGap = dp(6f)
        val availableWidth = panelWidthPx - dp(24f)
        val columns = columnsForLabels(
            labels = entries.map { it.primaryLabel },
            availableWidthPx = availableWidth,
            maxColumns = maxColumns,
            gapPx = horizontalGap,
        )
        val chipWidth = (availableWidth - horizontalGap * (columns - 1)) / columns
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fun lineHeight(sizeSp: Float): Int {
            labelPaint.textSize = sizeSp * context.resources.displayMetrics.scaledDensity
            return labelPaint.fontMetricsInt.run { descent - ascent }
        }
        val chipHeight = maxOf(
            dp(if (stableCodecHeight) 56f else 48f),
            lineHeight(PRIMARY_LABEL_TEXT_SIZE_SP) + (if (stableCodecHeight) lineHeight(12f) else 0) + dp(12f),
        )
        entries.chunked(columns).forEachIndexed { rowIndex, rowEntries ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            target.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (rowIndex > 0) topMargin = dp(6f)
                },
            )
            rowEntries.forEachIndexed { chipIndex, option ->
                row.addView(
                    qualityChip(option, stableCodecHeight),
                    LinearLayout.LayoutParams(chipWidth, chipHeight).apply {
                        if (chipIndex > 0) marginStart = horizontalGap
                    },
                )
            }
        }
    }

    /**
     * Picks the widest column count whose chips still fit the longest label so
     * primary labels like "1080p60" are never truncated by construction. Bold
     * is assumed because the selected chip renders bold.
     */
    private fun columnsForLabels(
        labels: List<String>,
        availableWidthPx: Int,
        maxColumns: Int,
        gapPx: Int,
    ): Int {
        if (availableWidthPx <= 0 || labels.isEmpty()) return 1
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = PRIMARY_LABEL_TEXT_SIZE_SP * context.resources.displayMetrics.scaledDensity
            typeface = Typeface.DEFAULT_BOLD
        }
        val longestLabel = labels.maxOf { paint.measureText(it) }
        val requiredChipWidth = longestLabel + dp(12f)
        var columns = maxColumns.coerceAtLeast(1)
        while (columns > 1) {
            val chipWidth = (availableWidthPx - gapPx * (columns - 1)) / columns
            if (chipWidth >= requiredChipWidth) break
            columns--
        }
        return columns
    }

    private fun qualityChip(option: PlayerQualityPopupOption, stableCodecHeight: Boolean): View {
        val selected = isSelected(option)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = chipBackground(colors.controlFill, colors.selectedFill)
            isSelected = selected
            isClickable = true
            isFocusable = true
            contentDescription = listOfNotNull(option.primaryLabel, option.codecLabel).joinToString(", ")
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))

            addView(TextView(context).apply {
                isDuplicateParentStateEnabled = true
                text = option.primaryLabel
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                // This selection is immutable until dismissal. Bind its color
                // immediately, without waiting for inherited state on attachment.
                setTextColor(if (selected) colors.onSelected else colors.onPanel)
                textSize = PRIMARY_LABEL_TEXT_SIZE_SP
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            })
            if (stableCodecHeight && option.codecLabel != null) {
                addView(TextView(context).apply {
                    isDuplicateParentStateEnabled = true
                    text = option.codecLabel
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(if (selected) colors.onSelected else ColorUtils.setAlphaComponent(colors.onPanel, 212))
                    textSize = 12f
                })
            }
            setOnClickListener {
                onQualitySelected(option.tag)
                onDismissRequested()
            }
        }
    }

    private fun isSelected(option: PlayerQualityPopupOption): Boolean {
        val selected = selectedTag.normalizedValue()
        return option.tag.normalizedValue() == selected || when (option.kind) {
            PlayerQualityPopupOption.Kind.AUDIO_ONLY -> selected == "audio_only"
            PlayerQualityPopupOption.Kind.CHAT_ONLY -> selected == "chat_only"
            PlayerQualityPopupOption.Kind.VIDEO -> false
        }
    }

    private fun chipBackground(normalColor: Int, selectedColor: Int) = android.graphics.drawable.RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.onPanel, 48)),
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), roundedDrawable(selectedColor, 20f))
            addState(intArrayOf(), roundedDrawable(normalColor, 20f))
        },
        roundedDrawable(android.graphics.Color.WHITE, 20f),
    )

    private fun roundedDrawable(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun String?.normalizedValue(): String = this
        ?.trim()
        ?.lowercase(Locale.US)
        ?.replace(' ', '_')
        ?.replace('-', '_')
        .orEmpty()

    private fun dp(value: Float): Int = (value * density).toInt()
}

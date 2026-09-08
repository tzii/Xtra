package com.github.andreyasadchy.xtra.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.stats.StatsChartScale
import java.util.Locale

/**
 * Custom bar chart view for displaying daily screen time.
 * Shows 7 days of data with animated bars, grid lines, and day labels.
 * Automatically adapts to light/dark theme.
 */
class DailyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DayData(
        val label: String,      // e.g., "Mon", "Tue", "Today"
        val seconds: Long       // Total seconds watched
    )

    private var data: List<DayData> = emptyList()
    private var animationProgress = 1f
    private var dataAnimator: ValueAnimator? = null
    private var startRatios: List<Float> = emptyList()
    private var maxSeconds: Long = 60L
    private val density = resources.displayMetrics.density

    // Theme colors
    private val primaryColor: Int
    private val onSurfaceColor: Int
    private val outlineColor: Int

    // Paints
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
    }

    private val gridLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        textAlign = Paint.Align.RIGHT
    }

    private val barRect = RectF()
    private val barCornerRadius = 3f * density

    // Margins and spacing
    private val rightMargin = 8f * density
    private val topMargin get() = gridLabelPaint.textSize + 4f * density
    private val bottomMargin get() = labelPaint.textSize + 12f * density
    private val barSpacing = 0.25f   // Spacing between bars as fraction of bar width

    init {
        // Get theme colors
        val typedValue = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        primaryColor = typedValue.data

        context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        val textColorSecondary = if (typedValue.resourceId != 0) {
            context.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }
        onSurfaceColor = textColorSecondary

        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)
        outlineColor = if (typedValue.resourceId != 0) {
            context.getColor(typedValue.resourceId)
        } else {
            typedValue.data
        }

        // Apply theme colors (can be overridden by XML attrs)
        barPaint.color = primaryColor
        gridLinePaint.color = outlineColor
        labelPaint.color = onSurfaceColor
        gridLabelPaint.color = onSurfaceColor

        // Apply XML attributes if provided
        context.theme.obtainStyledAttributes(attrs, R.styleable.DailyBarChartView, 0, 0).apply {
            try {
                barPaint.color = getColor(R.styleable.DailyBarChartView_barColor, primaryColor)
                gridLinePaint.color = getColor(R.styleable.DailyBarChartView_gridLineColor, outlineColor)
                labelPaint.color = getColor(R.styleable.DailyBarChartView_labelColor, onSurfaceColor)
            } finally {
                recycle()
            }
        }
    }

    fun setData(dayData: List<DayData>, animate: Boolean = true) {
        if (data == dayData) return
        // Preserve the currently drawn heights when a refresh interrupts motion.
        val previousRatios = data.indices.map { index -> displayedRatio(index) }
        val sameSlots = data.size == dayData.size &&
            data.indices.all { data[it].label == dayData[it].label }
        dataAnimator?.cancel()
        dataAnimator = null
        startRatios = if (sameSlots) previousRatios else List(dayData.size) { 0f }
        data = dayData.toList()
        maxSeconds = StatsChartScale.ceilingSeconds(data.maxOfOrNull { it.seconds } ?: 0L)

        if (animate && isAttachedToWindow) {
            animationProgress = 0f
            dataAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 280
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animationProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animationProgress = 1f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        dataAnimator?.cancel()
        dataAnimator = null
        animationProgress = 1f
        super.onDetachedFromWindow()
    }

    private fun displayedRatio(index: Int): Float {
        val target = data[index].seconds.toFloat() / maxSeconds
        val start = startRatios.getOrElse(index) { 0f }
        return start + (target - start) * animationProgress
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (144 * density).toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (data.isEmpty()) {
            canvas.drawText(context.getString(R.string.no_data), width / 2f, height / 2f, labelPaint)
            return
        }

        val gridLines = listOf(maxSeconds / 2, maxSeconds).map { it to scaleLabel(it) }
        val leftMargin = gridLines.maxOf { gridLabelPaint.measureText(it.second) } + 8f * density
        val chartWidth = width - leftMargin - rightMargin
        val chartHeight = height - topMargin - bottomMargin
        val chartBottom = height - bottomMargin
        if (chartWidth <= 0 || chartHeight <= 0) return

        for ((seconds, label) in gridLines) {
            if (seconds <= maxSeconds) {
                val y = chartBottom - (seconds.toFloat() / maxSeconds * chartHeight)
                canvas.drawLine(leftMargin, y, width - rightMargin, y, gridLinePaint)
                canvas.drawText(label, leftMargin - 6f * density, y + gridLabelPaint.textSize / 3f, gridLabelPaint)
            }
        }

        // Calculate bar dimensions
        val numBars = data.size
        val totalBarWidth = chartWidth / numBars
        val barWidth = totalBarWidth * (1 - barSpacing)
        val gap = totalBarWidth * barSpacing / 2

        // Draw bars and labels
        var lastLabelRight = Float.NEGATIVE_INFINITY
        data.forEachIndexed { index, dayData ->
            val barLeft = leftMargin + index * totalBarWidth + gap
            val barRight = barLeft + barWidth

            // Animated bar height
            val animatedHeight = chartHeight * displayedRatio(index)
            val barTop = chartBottom - animatedHeight

            // Draw bar with rounded top corners
            barRect.set(barLeft, barTop, barRight, chartBottom)
            canvas.drawRoundRect(barRect, barCornerRadius, barCornerRadius, barPaint)

            if (dayData.label.isNotBlank()) {
                val halfLabelWidth = labelPaint.measureText(dayData.label) / 2f
                if (halfLabelWidth * 2 > chartWidth) return@forEachIndexed
                val labelX = (barLeft + barWidth / 2).coerceIn(leftMargin + halfLabelWidth, width - rightMargin - halfLabelWidth)
                if (labelX - halfLabelWidth >= lastLabelRight + 4f * density) {
                    canvas.drawText(dayData.label, labelX, height - 4f * density, labelPaint)
                    lastLabelRight = labelX + halfLabelWidth
                }
            }
        }
    }

    private fun scaleLabel(seconds: Long): String = when {
        seconds < 60 -> String.format(Locale.getDefault(), "%ds", seconds)
        seconds < 3600 && seconds % 60 == 0L -> String.format(Locale.getDefault(), "%dm", seconds / 60)
        seconds < 3600 -> String.format(Locale.getDefault(), "%.1fm", seconds / 60f)
        seconds % 3600 == 0L -> String.format(Locale.getDefault(), "%dh", seconds / 3600)
        else -> String.format(Locale.getDefault(), "%.1fh", seconds / 3600f)
    }
}

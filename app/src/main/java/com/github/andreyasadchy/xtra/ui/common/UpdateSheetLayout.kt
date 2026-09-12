package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.github.andreyasadchy.xtra.R

/** One scrollable body yields space to the actions in short windows and large text. */
class UpdateSheetLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    var maxAvailableHeight: Int = Int.MAX_VALUE
        set(value) {
            val height = value.coerceAtLeast(1)
            if (field != height) { field = height; requestLayout() }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val stacked = resources.configuration.fontScale > 1.3f &&
            View.MeasureSpec.getSize(widthMeasureSpec) / resources.displayMetrics.density < 400
        findViewById<LinearLayout>(R.id.secondaryActions)?.let { actions ->
            actions.orientation = if (stacked) VERTICAL else HORIZONTAL
            for (index in 0 until actions.childCount) {
                val child = actions.getChildAt(index)
                val params = child.layoutParams as LayoutParams
                val width = if (stacked) LayoutParams.MATCH_PARENT else 0
                val weight = if (stacked) 0f else 1f
                if (params.width != width || params.weight != weight) {
                    params.width = width
                    params.weight = weight
                    child.layoutParams = params
                }
            }
        }
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val available = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) {
            resources.displayMetrics.heightPixels
        } else View.MeasureSpec.getSize(heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(measuredHeight.coerceAtMost(available).coerceAtMost(maxAvailableHeight), View.MeasureSpec.EXACTLY))
    }
}

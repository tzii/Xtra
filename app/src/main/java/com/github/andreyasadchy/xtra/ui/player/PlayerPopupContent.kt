package com.github.andreyasadchy.xtra.ui.player

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import com.github.andreyasadchy.xtra.R
import com.google.android.material.card.MaterialCardView

/** The card and first row stay fixed; only the options below the header scroll. */
internal object PlayerPopupContent {
    fun prepare(card: MaterialCardView) {
        val column = card.getChildAt(0) as LinearLayout
        val header = column.getChildAt(0)
        val body = LinearLayout(card.context).apply { orientation = LinearLayout.VERTICAL }
        while (column.childCount > 1) {
            val child = column.getChildAt(1)
            column.removeView(child)
            // More already has a scroll view. Unwrap it so touch/focus scrolling
            // has exactly one owner and cannot scroll the header offscreen.
            if (child is NestedScrollView) {
                val options = child.getChildAt(0)
                child.removeView(options)
                body.addView(options)
            } else {
                body.addView(child)
            }
        }
        val viewport = NestedScrollView(card.context).apply {
            id = R.id.playerPopupViewport
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(body)
        }
        column.addView(viewport, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        column.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        header.minimumHeight = (48 * card.resources.displayMetrics.density).toInt()
        card.clipToOutline = true
    }
}

package com.github.andreyasadchy.xtra.ui.stats

import android.app.Application
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import com.github.andreyasadchy.xtra.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import com.github.andreyasadchy.xtra.ui.UiTestRender
import com.github.andreyasadchy.xtra.ui.view.DailyBarChartView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [28], qualifiers = "w320dp-h800dp-mdpi")
class StatsLayoutTest {
    @Test fun `compact metrics and range buttons remain readable with large text`() {
        for (scale in listOf(1f, 2f)) {
            val app = RuntimeEnvironment.getApplication()
            val context = ContextThemeWrapper(app.createConfigurationContext(
                Configuration(app.resources.configuration).apply { fontScale = scale },
            ), R.style.BaseDarkTheme)
            val inflater = LayoutInflater.from(context)
            for (resource in listOf(R.layout.item_stats_screen_time, R.layout.item_stats_streak)) {
                val card = inflater.inflate(resource, null)
                if (resource == R.layout.item_stats_screen_time) {
                    StatsDashboardAdapter.ScreenTimeViewHolder(com.github.andreyasadchy.xtra.databinding.ItemStatsScreenTimeBinding.bind(card))
                }
                mapOf(R.id.dailyAverageText to "3h 37m", R.id.todayTimeText to "18m",
                    R.id.weekTotalText to "125h", R.id.currentStreakText to "123", R.id.longestStreakText to "365",
                    R.id.weekChangeText to "Up 25% vs last week").forEach { (id, text) ->
                    card.findViewById<TextView>(id)?.text = text
                }
                measure(card, 296, 0, View.MeasureSpec.UNSPECIFIED)
                checkText(card)
                if (resource == R.layout.item_stats_screen_time) {
                    for (id in listOf(R.id.dailyAverageText, R.id.todayTimeText, R.id.weekTotalText)) {
                        val value = card.findViewById<TextView>(id)
                        for (line in 0 until value.lineCount) {
                            val part = value.text.subSequence(value.layout.getLineStart(line), value.layout.getLineEnd(line)).toString().trim()
                            assertFalse("unit must stay with its number", part == "m" || part == "h")
                        }
                    }
                }
                if (scale == 1f) assertTrue("compact card should not waste height", card.height < 360)
            }
            val page = inflater.inflate(R.layout.fragment_stats, null)
            page.findViewById<RecyclerView>(R.id.statsRecyclerView).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = StatsDashboardAdapter().apply {
                    submitList(listOf(
                        StatsDashboardItem.ScreenTime(
                            chartData = listOf(12, 25, 18, 7, 40, 30, 36).mapIndexed { index, minutes ->
                                DailyBarChartView.DayData(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Today")[index], minutes * 60L)
                            },
                            dailyAverageText = "24m", weekChangeText = "Up 25% vs last week",
                            todayTimeText = "36m", rangeTotalLabelText = "Last 7 days", weekTotalText = "2h 48m",
                        ),
                        StatsDashboardItem.Streak("4", "12"),
                    ))
                }
            }
            measure(page, 320, 800, View.MeasureSpec.EXACTLY)
            val range = page.findViewById<ViewGroup>(R.id.rangeButtonGroup)
            checkText(range)
            for (i in 0 until range.childCount) assertTrue(range.getChildAt(i).height >= 48)
            val scroll = range.parent as HorizontalScrollView
            scroll.scrollTo(10000, 0)
            assertTrue("last range remains reachable", scroll.scrollX + scroll.width - scroll.paddingRight >= range.right)
            scroll.scrollTo(0, 0)
            UiTestRender.save(page, "stats-font-$scale")
        }
    }

    private fun checkText(view: View) {
        if (view is TextView && view.text.isNotEmpty()) {
            assertTrue("text fits vertically: ${view.text}", view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
            for (line in 0 until view.lineCount) assertEquals("text is not truncated: ${view.text}", 0, view.layout.getEllipsisCount(line))
        }
        if (view is ViewGroup) (0 until view.childCount).forEach { checkText(view.getChildAt(it)) }
    }

    private fun measure(view: View, width: Int, height: Int, mode: Int) {
        view.layoutDirection = view.resources.configuration.layoutDirection
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, mode))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}

package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerQualityPopupBinding
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerSpeedPopupBinding
import com.github.andreyasadchy.xtra.model.VideoQuality
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import com.github.andreyasadchy.xtra.ui.UiTestRender

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [28], qualifiers = "w360dp-h800dp-mdpi")
class PlayerPopupContentTest {
    private val context: Context get() = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)

    @Test fun `short viewport scrolls options without moving header or card`() {
        for ((layout, close) in listOf(
            R.layout.layout_player_quality_popup to R.id.qualityPopupClose,
            R.layout.layout_player_speed_popup to R.id.speedPopupClose,
            R.layout.layout_player_more_popup to R.id.morePopupClose,
        )) {
            val card = LayoutInflater.from(context).inflate(layout, null) as MaterialCardView
            val column = card.getChildAt(0) as LinearLayout
            repeat(12) {
                column.addView(TextView(context).apply { text = "Option $it" }, LinearLayout.LayoutParams(-1, 48))
            }
            PlayerPopupContent.prepare(card)
            measure(card, 328, 0, View.MeasureSpec.UNSPECIFIED)
            assertTrue("natural content must not be lost", card.measuredHeight > 576)
            measure(card, 328, 220, View.MeasureSpec.EXACTLY)
            val viewport = card.findViewById<NestedScrollView>(R.id.playerPopupViewport)
            val header = column.getChildAt(0)
            val headerTop = header.top
            assertTrue(viewport.height > 48)
            assertTrue(card.findViewById<View>(close).height >= 48)
            viewport.scrollTo(0, 10000)
            assertTrue("body must scroll", viewport.scrollY > 0)
            assertEquals(headerTop, header.top)
            assertEquals(0, card.scrollY)
            assertEquals(1, countScrollViews(card))
            assertTrue("last option can reach viewport", viewport.scrollY + viewport.height >= viewport.getChildAt(0).height)
        }
    }

    @Test fun `portrait popup host is outside forced video bounds`() {
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_player, null) as ViewGroup
        val host = root.findViewById<View>(R.id.playerPopupHost)
        assertSame(root, host.parent)
    }

    @Test fun `More keeps its rounded card and close button when scrolled`() {
        val card = LayoutInflater.from(context).inflate(R.layout.layout_player_more_popup, null) as MaterialCardView
        val colors = PlayerPanelTheme.resolve(context)
        card.setCardBackgroundColor(colors.panel)
        card.setStrokeColor(colors.panelStroke)
        card.findViewById<android.widget.ImageButton>(R.id.morePopupClose).imageTintList = android.content.res.ColorStateList.valueOf(colors.secondaryText)
        listOf(R.id.streamGroupHeader, R.id.menuDownload, R.id.menuBookmark, R.id.menuTimer,
            R.id.chatGroupHeader, R.id.menuReloadEmotes, R.id.playbackGroupHeader,
            R.id.menuDisplayMode, R.id.helpGroupHeader, R.id.menuGestureGuide).forEach {
            card.findViewById<View>(it).visibility = View.VISIBLE
        }
        card.findViewById<TextView>(R.id.menuBookmark).text = "Add bookmark"
        card.findViewById<TextView>(R.id.displayModeValue).apply { visibility = View.VISIBLE; text = "Fit" }
        listOf(R.id.menuDownload, R.id.menuBookmark, R.id.menuTimer, R.id.menuReloadEmotes,
            R.id.menuDisplayMode, R.id.menuGestureGuide).forEach { card.findViewById<View>(it).minimumHeight = 48 }
        PlayerPopupContent.prepare(card)
        measure(card, 328, 280, View.MeasureSpec.EXACTLY)
        val close = card.findViewById<View>(R.id.morePopupClose)
        val closeTop = close.top
        UiTestRender.save(card, "more-scroll-top")
        val viewport = card.findViewById<NestedScrollView>(R.id.playerPopupViewport)
        viewport.scrollTo(0, 10000)
        assertTrue(viewport.scrollY > 0)
        assertEquals(closeTop, close.top)
        assertTrue(viewport.scrollY + viewport.height >= viewport.getChildAt(0).height)
        UiTestRender.save(card, "more-scroll-bottom")
    }

    @Test fun `mixed codec chips keep Auto centered and all labels readable at large font`() {
        for (fontScale in listOf(1f, 2f)) {
            val scaled = ContextThemeWrapper(context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { this.fontScale = fontScale },
            ), R.style.BaseDarkTheme)
            val binding = LayoutPlayerQualityPopupBinding.inflate(LayoutInflater.from(scaled))
            val colors = PlayerPanelTheme.resolve(scaled)
            assertTrue("selected text has readable contrast", androidx.core.graphics.ColorUtils.calculateContrast(colors.onSelected, colors.selectedFill) >= 4.5)
            val binder = PlayerQualityPopupBinder(scaled, binding, listOf(
                VideoQuality("auto"), VideoQuality("1440p60", "hvc1.2.4.L153"),
                VideoQuality("1080p60", "avc1.64002a"), VideoQuality("720p60", "avc1.64002a"),
                VideoQuality("480p"), VideoQuality("360p"), VideoQuality("160p"),
                VideoQuality("audio_only"), VideoQuality("chat_only"),
            ), "1080p60", 288, {}, {})
            binder.bind()
            PlayerPopupContent.prepare(binding.root)
            measure(binding.root, 288, 0, View.MeasureSpec.UNSPECIFIED)
            val naturalHeight = binding.root.height
            measure(binding.root, 288, minOf(naturalHeight, 600), View.MeasureSpec.EXACTLY)
            val firstRow = binding.qualityVideoRows.getChildAt(0) as LinearLayout
            val auto = firstRow.getChildAt(0) as LinearLayout
            assertEquals("Auto must not reserve an empty codec line", 1, auto.childCount)
            val label = auto.getChildAt(0)
            assertEquals(auto.height / 2f, (label.top + label.bottom) / 2f, 1f)
            fun checkLabels(view: View) {
                if (view is TextView && view.text.isNotEmpty()) {
                    assertTrue("label fits chip vertically: ${view.text}", view.bottom <= (view.parent as View).height)
                    assertEquals("label is not truncated: ${view.text}", 0, view.layout.getEllipsisCount(0))
                    if ((view.parent as View).isSelected) {
                        assertEquals("selected color is applied before attachment", colors.onSelected, view.currentTextColor)
                        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(view.currentTextColor, colors.selectedFill) >= 4.5)
                    }
                }
                if (view is ViewGroup) (0 until view.childCount).forEach { checkLabels(view.getChildAt(it)) }
            }
            checkLabels(binding.qualityVideoRows)
            checkLabels(binding.qualityUtilityRows)
            UiTestRender.save(binding.root, "quality-font-$fontScale")
            binder.dispose()
        }
    }

    private fun countScrollViews(view: View): Int = (if (view is NestedScrollView) 1 else 0) +
        if (view is ViewGroup) (0 until view.childCount).sumOf { countScrollViews(view.getChildAt(it)) } else 0

    @Test fun `speed presets reflow at large font instead of truncating`() {
        val scaled = ContextThemeWrapper(context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { fontScale = 2f },
        ), R.style.BaseDarkTheme)
        val binding = LayoutPlayerSpeedPopupBinding.inflate(LayoutInflater.from(scaled))
        val binder = PlayerSpeedPopupBinder(scaled, binding, 1.35f, 288, {}, {})
        binder.bind()
        PlayerPopupContent.prepare(binding.root)
        measure(binding.root, 288, 0, View.MeasureSpec.UNSPECIFIED)
        val height = binding.root.height
        measure(binding.root, 288, height, View.MeasureSpec.EXACTLY)
        assertEquals("large title stays on one line", 1, binding.speedPopupTitle.lineCount)
        for (i in 0 until binding.speedPresetRows.childCount) {
            val row = binding.speedPresetRows.getChildAt(i) as ViewGroup
            for (j in 0 until row.childCount) {
                val preset = row.getChildAt(j) as TextView
                assertTrue(preset.height >= 48)
                assertTrue(preset.width >= 48)
                assertEquals("speed remains readable: ${preset.text}", 0, preset.layout.getEllipsisCount(0))
            }
        }
        UiTestRender.save(binding.root, "speed-large-text")
        binder.dispose()
    }

    private fun measure(view: View, width: Int, height: Int, mode: Int) {
        view.layoutDirection = view.resources.configuration.layoutDirection
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, mode))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}

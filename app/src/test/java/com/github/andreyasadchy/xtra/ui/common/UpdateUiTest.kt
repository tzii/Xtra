package com.github.andreyasadchy.xtra.ui.common

import android.app.Application
import android.app.Activity
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogUpdateAvailableBinding
import com.github.andreyasadchy.xtra.databinding.DialogUpdateDownloadBinding
import com.github.andreyasadchy.xtra.model.ui.UpdateInfo
import com.github.andreyasadchy.xtra.ui.UiTestRender
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [28], qualifiers = "w360dp-h800dp-mdpi")
class UpdateUiTest {
    private val release = UpdateInfo("1.3.0", "v1.3.0", "ThystTV 1.3.0", "2026-09-11T12:00:00Z",
        "# ThystTV 1.3.0\n\n### A smoother player\n\n- Menus stay within reach in portrait.\n- Faster controls and cleaner local stats.\n\n### Fixes\n\nImproved chat replay and saved video resume.\n\n[Full release notes](https://github.com/tzii/ThystTV/releases)",
        "https://github.com/tzii/ThystTV/releases", "https://example.com/sample.apk")

    private fun context(scale: Float, light: Boolean = false): ContextThemeWrapper {
        val app = RuntimeEnvironment.getApplication()
        return ContextThemeWrapper(app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = scale }),
            if (light) R.style.BaseLightTheme else R.style.BaseDarkTheme)
    }

    private fun measure(view: View, width: Int, height: Int) {
        view.layoutDirection = View.LAYOUT_DIRECTION_LTR
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun `prompt fits compact wide and short windows with accessible fixed actions`() {
        for (scale in listOf(1f, 2f)) for ((width, height) in listOf(288 to 640, 528 to 280)) {
            val ctx = context(scale)
            val binding = DialogUpdateAvailableBinding.inflate(LayoutInflater.from(ctx))
            UpdateAvailableDialog.bindRelease(ctx, binding, release.copy(releaseNotes = release.releaseNotes!!.repeat(6)))
            measure(binding.root, width, height)
            assertTrue(binding.root.height <= height)
            for (button in listOf(binding.downloadButton, binding.laterButton, binding.githubButton)) {
                assertTrue("48dp touch target", button.height >= 48)
                val rect = android.graphics.Rect()
                button.getDrawingRect(rect)
                binding.root.offsetDescendantRectToMyCoords(button, rect)
                assertTrue("action below bottom: $rect", rect.bottom <= binding.root.height)
                assertTrue("action outside width: $rect", rect.left >= 0 && rect.right <= width)
                assertFalse("action must not be within scroll", isInside(button, binding.releaseNotesScroll))
                val layout = button.layout
                assertTrue("button text clipped", layout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
            }
            assertTrue(binding.releaseNotesScroll.height > 0)
            assertTrue(binding.releaseNotesScroll.getChildAt(0).height > binding.releaseNotesScroll.height)
        }
    }

    @Test fun `native prompt and download previews`() {
        for (light in listOf(false, true)) for (scale in listOf(1f, 2f)) {
            val ctx = context(scale, light)
            val binding = DialogUpdateAvailableBinding.inflate(LayoutInflater.from(ctx))
            UpdateAvailableDialog.bindRelease(ctx, binding, release)
            measure(binding.root, 328, 720)
            UiTestRender.save(binding.root, "updater-${if (light) "light" else "dark"}-$scale")
            val download = DialogUpdateDownloadBinding.inflate(LayoutInflater.from(ctx))
            val activity = Robolectric.buildActivity(Activity::class.java).setup()
            activity.get().setContentView(download.root)
            download.root.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(download.root, com.google.android.material.R.attr.colorSurfaceContainer))
            UpdateDownloadUi.bind(download, 24_000_000, 40_000_000)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            // Capture the settled endpoint: Robolectric does not reliably advance
            // Material's spring animation. Device QA covers actual motion.
            download.progressBar.setProgressCompat(60, false)
            measure(download.root, 328, 720)
            UiTestRender.save(download.root, "update-download-${if (light) "light" else "dark"}-$scale")
            activity.pause().stop().destroy()
        }
    }

    @Test fun `download body scrolls to the final byte count in a short large-font window`() {
        val ctx = context(2f)
        val binding = DialogUpdateDownloadBinding.inflate(LayoutInflater.from(ctx))
        // A parent supplies the scroll transform when capturing View.draw directly.
        val preview = android.widget.FrameLayout(ctx).apply {
            setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceContainer))
            addView(binding.root, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        UpdateDownloadUi.bind(binding, 24_000_000, 40_000_000)
        measure(preview, 288, 144)
        assertTrue(binding.root.height <= 144)
        assertTrue(binding.root.canScrollVertically(1))
        binding.root.scrollTo(0, binding.root.getChildAt(0).height)
        val rect = android.graphics.Rect()
        binding.textView.getDrawingRect(rect)
        binding.root.offsetDescendantRectToMyCoords(binding.textView, rect)
        // Descendant bounds are in the scroll owner's content coordinates.
        val viewportTop = binding.root.scrollY
        assertTrue("body has scrolled", viewportTop > 0)
        assertFalse("reached the end", binding.root.canScrollVertically(1))
        assertTrue("final byte count reachable: $rect, viewport top $viewportTop",
            rect.top >= viewportTop && rect.bottom <= viewportTop + binding.root.height)
        UiTestRender.save(preview, "update-download-short-scrolled")
    }

    @Test fun `missing date and release link are hidden while notes have a fallback`() {
        val ctx = context(1f)
        val binding = DialogUpdateAvailableBinding.inflate(LayoutInflater.from(ctx))
        UpdateAvailableDialog.bindRelease(ctx, binding, release.copy(publishedAt = "bad", releaseUrl = null, releaseNotes = null))
        assertEquals(View.GONE, binding.updateDate.visibility)
        assertEquals(View.GONE, binding.githubButton.visibility)
        assertEquals(ctx.getString(R.string.no_release_notes), binding.releaseNotesText.text.toString())
    }

    @Test fun `known progress shows percentage and unknown size shows received bytes`() {
        val binding = DialogUpdateDownloadBinding.inflate(LayoutInflater.from(context(1f)))
        UpdateDownloadUi.bind(binding, 25, 100)
        assertTrue(binding.progressPercent.text.contains("25"))
        assertEquals(25, binding.progressBar.progress)
        UpdateDownloadUi.bind(binding, 25, null)
        assertEquals("—", binding.progressPercent.text.toString())
        assertTrue(binding.progressBar.isIndeterminate)
        assertFalse(binding.textView.text.toString().contains("Preparing"))
        UpdateDownloadUi.bind(binding, 0, null)
        assertEquals(binding.root.context.getString(R.string.update_preparing), binding.textView.text.toString())
    }

    @Test fun `release metadata removes only duplicate leading headings`() {
        assertFalse(UpdateAvailableDialog.releaseNotesMarkdown(release).startsWith("# ThystTV"))
        assertEquals("## Fixes\nKeep this", UpdateAvailableDialog.releaseNotesMarkdown(release.copy(releaseNotes = "## Fixes\nKeep this")))
        assertNull(UpdateAvailableDialog.releaseDate("2026-02-30T00:00:00Z"))
        assertNull(UpdateAvailableDialog.releaseDate("nonsense"))
        assertNotNull(UpdateAvailableDialog.releaseDate(release.publishedAt))
    }

    @Test fun `prompt actions dismiss once and dispatch the selected action`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val ctx = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        try {
            for ((id, expected) in listOf(R.id.downloadButton to "download", R.id.laterButton to "later", R.id.githubButton to release.releaseUrl!!)) {
                val actions = mutableListOf<String>()
                val dialog = UpdateAvailableDialog.show(ctx, LayoutInflater.from(ctx), release,
                    onDownload = { actions.add("download") }, onLater = { actions.add("later") }, onGithub = { actions.add(it) })
                dialog.findViewById<View>(id)!!.performClick()
                assertFalse(dialog.isShowing)
                assertEquals(listOf(expected), actions)
            }
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun `failure actions retry open browser or cancel without extra work`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val ctx = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        try {
            for ((button, expected) in listOf(-1 to listOf("retry"), -3 to listOf("browser"), -2 to emptyList())) {
                val actions = mutableListOf<String>()
                val dialog = UpdateDownloadUi.showFailure(ctx, { actions.add("retry") }, { actions.add("browser") })
                dialog.getButton(button).performClick()
                shadowOf(android.os.Looper.getMainLooper()).idle()
                assertFalse(dialog.isShowing)
                assertEquals(expected, actions)
            }
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun `browser failure keeps retry and cancel visible`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val ctx = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        var retries = 0
        try {
            val dialog = UpdateDownloadUi.showFailure(ctx, { retries++ }, { false })
            dialog.getButton(-3).performClick()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(dialog.isShowing)
            dialog.getButton(-1).performClick()
            assertEquals(1, retries)
            assertFalse(dialog.isShowing)
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun `same prompt root remeasures when available height changes`() {
        val ctx = context(2f)
        val binding = DialogUpdateAvailableBinding.inflate(LayoutInflater.from(ctx))
        UpdateAvailableDialog.bindRelease(ctx, binding, release.copy(releaseNotes = release.releaseNotes!!.repeat(8)))
        for (height in listOf(700, 300, 600)) {
            binding.updateSheetContent.maxAvailableHeight = height
            measure(binding.root, 328, 800)
            assertTrue("root exceeds resized host", binding.root.height <= height)
            val rect = android.graphics.Rect()
            binding.downloadButton.getDrawingRect(rect)
            binding.root.offsetDescendantRectToMyCoords(binding.downloadButton, rect)
            assertTrue("download remains reachable after resize", rect.bottom <= binding.root.height)
        }
    }

    @Test fun `same live dialog follows host shrink and expansion and detaches listeners`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val ctx = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx).setMessage("Update test").create()
        try {
            val host = activity.get().window.decorView
            host.layout(0, 0, 320, 700)
            dialog.show()
            val binding = UpdateDialogWindow.attach(dialog)
            val first = dialog.window!!.attributes.width
            host.layout(0, 0, 200, 300)
            binding.resize()
            val compact = dialog.window!!.attributes.width
            assertTrue("dialog must shrink with its host", compact < first)
            host.layout(0, 0, 320, 700)
            binding.resize()
            assertEquals(first, dialog.window!!.attributes.width)
            binding.dispose()
            binding.dispose()
            assertTrue(binding.disposed)
            host.layout(0, 0, 160, 300)
            binding.resize()
            assertEquals("disposed binding must not update the dialog", first, dialog.window!!.attributes.width)
        } finally { dialog.dismiss(); activity.pause().stop().destroy() }
    }

    private fun isInside(view: View, parent: ViewGroup): Boolean {
        var current = view.parent
        while (current != null) { if (current === parent) return true; current = current.parent }
        return false
    }
}

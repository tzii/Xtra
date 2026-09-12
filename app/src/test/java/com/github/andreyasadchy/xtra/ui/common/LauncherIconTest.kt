package com.github.andreyasadchy.xtra.ui.common

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.LinearLayout
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.UiTestRender
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [28], qualifiers = "mdpi")
class LauncherIconTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun render(drawable: Drawable): Bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also {
        drawable.setBounds(0, 0, 256, 256)
        drawable.draw(Canvas(it))
    }

    @Test fun `adaptive and round launcher use the supplied gem layers`() {
        val foreground = render(context.getDrawable(R.drawable.ic_launcher_foreground)!!)
        val background = render(context.getDrawable(R.drawable.ic_launcher_background_p4)!!)
        assertEquals(Color.rgb(0, 229, 239), foreground.getPixel(128, 128))
        assertEquals(0, Color.alpha(foreground.getPixel(0, 0)))
        assertEquals(255, Color.alpha(background.getPixel(0, 0)))
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val adaptive = context.getDrawable(id) as AdaptiveIconDrawable
            val actualForeground = render(adaptive.foreground)
            val actualBackground = render(adaptive.background)
            assertTrue(foreground.sameAs(actualForeground))
            assertTrue(background.sameAs(actualBackground))
            actualForeground.recycle()
            actualBackground.recycle()
        }
        foreground.recycle()
        background.recycle()
    }

    @Test fun `color and themed silhouettes stay in the adaptive safe circle`() {
        val color = render(context.getDrawable(R.drawable.ic_launcher_foreground)!!)
        val mono = render(context.getDrawable(R.drawable.ic_launcher_monochrome)!!)
        assertEquals(0, Color.alpha(mono.getPixel(128, 128))) // Transparent play counter.
        assertEquals(255, Color.alpha(mono.getPixel(90, 110)))
        for (bitmap in listOf(color, mono)) {
            for (y in 0 until 256) for (x in 0 until 256) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 16) {
                    val dx = x + 0.5f - 128
                    val dy = y + 0.5f - 128
                    // 66dp diameter on the supplied 108dp canvas, plus one antialias pixel.
                    val radius = 256f * 33 / 108 + 1
                    assertTrue("out of safe circle at $x,$y", dx * dx + dy * dy <= radius * radius)
                }
            }
            bitmap.recycle()
        }
    }

    @Test fun `render packaged adaptive and light dark themed icon previews`() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(38, 38, 43))
        }
        val normal = context.getDrawable(R.mipmap.ic_launcher)!!
        val light = AdaptiveIconDrawable(
            android.graphics.drawable.ColorDrawable(Color.rgb(230, 227, 245)),
            context.getDrawable(R.drawable.ic_launcher_monochrome)!!.mutate().apply { setTint(Color.rgb(61, 39, 92)) },
        )
        val dark = AdaptiveIconDrawable(
            android.graphics.drawable.ColorDrawable(Color.rgb(40, 31, 57)),
            context.getDrawable(R.drawable.ic_launcher_monochrome)!!.mutate().apply { setTint(Color.rgb(213, 190, 248)) },
        )
        for (drawable in listOf(normal, light, dark)) {
            row.addView(ImageView(context).apply {
                setPadding(24, 24, 24, 24)
                setImageDrawable(drawable)
            }, LinearLayout.LayoutParams(256, 256))
        }
        row.measure(android.view.View.MeasureSpec.makeMeasureSpec(768, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(256, android.view.View.MeasureSpec.EXACTLY))
        row.layout(0, 0, 768, 256)
        UiTestRender.save(row, "launcher-gem-native")
    }
}

package com.github.andreyasadchy.xtra.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File

/** Native Android rendering evidence, generated under ignored build outputs. */
internal object UiTestRender {
    fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/ui-previews").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

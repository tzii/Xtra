package com.github.andreyasadchy.xtra.ui.common

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.andreyasadchy.xtra.util.getActivity
import com.github.andreyasadchy.xtra.util.update.UpdateWindowBounds
import com.github.andreyasadchy.xtra.util.update.updateWindowBounds

/** Re-sizes the same dialog on host layout/inset changes, and detaches every listener. */
internal class UpdateDialogWindow private constructor(
    private val dialog: AlertDialog,
    private val host: View,
    private val lifecycleOwner: LifecycleOwner?,
    private val onMaxHeight: ((Int) -> Unit)?,
) : View.OnLayoutChangeListener, View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener,
    DefaultLifecycleObserver {
    private var lastBounds: UpdateWindowBounds? = null
    internal var disposed = false
        private set

    init {
        host.addOnLayoutChangeListener(this)
        host.viewTreeObserver.addOnGlobalLayoutListener(this)
        dialog.window?.decorView?.addOnAttachStateChangeListener(this)
        lifecycleOwner?.lifecycle?.addObserver(this)
        resize()
    }

    internal fun resize() {
        if (disposed) return
        val window = dialog.window ?: return
        val metrics = host.resources.displayMetrics
        val frame = Rect().also(host::getWindowVisibleDisplayFrame)
        val width = minOf(host.width.takeIf { it > 0 } ?: metrics.widthPixels,
            frame.width().takeIf { it > 0 } ?: metrics.widthPixels)
        val height = minOf(host.height.takeIf { it > 0 } ?: metrics.heightPixels,
            frame.height().takeIf { it > 0 } ?: metrics.heightPixels)
        val bounds = updateWindowBounds(width, height, metrics.density)
        if (bounds == lastBounds) return
        lastBounds = bounds
        onMaxHeight?.invoke(bounds.bodyMaxHeight)
        window.setGravity(if (bounds.centered) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.setLayout(bounds.width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) = resize()
    override fun onGlobalLayout() = resize()
    override fun onViewAttachedToWindow(view: View) = resize()
    override fun onViewDetachedFromWindow(view: View) = dispose()
    override fun onDestroy(owner: LifecycleOwner) {
        dispose()
        dialog.dismiss()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        host.removeOnLayoutChangeListener(this)
        if (host.viewTreeObserver.isAlive) host.viewTreeObserver.removeOnGlobalLayoutListener(this)
        dialog.window?.decorView?.removeOnAttachStateChangeListener(this)
        lifecycleOwner?.lifecycle?.removeObserver(this)
    }

    companion object {
        fun attach(dialog: AlertDialog, onMaxHeight: ((Int) -> Unit)? = null): UpdateDialogWindow {
            val activity = dialog.context.getActivity()
            return UpdateDialogWindow(dialog, activity?.window?.decorView ?: checkNotNull(dialog.window).decorView,
                activity as? LifecycleOwner, onMaxHeight)
        }

    }
}

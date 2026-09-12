package com.github.andreyasadchy.xtra.ui.player

import android.view.View
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.github.andreyasadchy.xtra.R

/** View-lifetime ownership of the activity decor's single immersive-mode callback. */
@MainThread
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class PlayerSystemUiListener {
    private var registration: Registration? = null

    fun attach(decor: View, owner: LifecycleOwner, onVisibilityChanged: () -> Unit) {
        detach()
        val lifecycle = owner.lifecycle
        if (lifecycle.currentState == Lifecycle.State.DESTROYED) return
        (decor.getTag(R.id.playerSystemUiListener) as? Registration)?.detach()
        registration = Registration(decor, lifecycle, onVisibilityChanged).also {
            decor.setTag(R.id.playerSystemUiListener, it)
            decor.setOnSystemUiVisibilityChangeListener(it)
            lifecycle.addObserver(it)
        }
    }

    fun detach() {
        registration?.detach()
        registration = null
    }

    private class Registration(
        private var decor: View?,
        private var lifecycle: Lifecycle?,
        private var callback: (() -> Unit)?,
    ) : View.OnSystemUiVisibilityChangeListener, DefaultLifecycleObserver {
        override fun onSystemUiVisibilityChange(visibility: Int) {
            callback?.invoke()
        }

        override fun onDestroy(owner: LifecycleOwner) = detach()

        fun detach() {
            // Invalidate even a previously queued/captured listener before releasing its view.
            callback = null
            lifecycle?.removeObserver(this)
            lifecycle = null
            decor?.let {
                // A retiring player must never clear a replacement player's listener.
                if (it.getTag(R.id.playerSystemUiListener) === this) {
                    it.setOnSystemUiVisibilityChangeListener(null)
                    it.setTag(R.id.playerSystemUiListener, null)
                }
            }
            decor = null
        }
    }
}

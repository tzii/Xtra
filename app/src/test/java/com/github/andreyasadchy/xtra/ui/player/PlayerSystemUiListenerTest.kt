package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.github.andreyasadchy.xtra.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class PlayerSystemUiListenerTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply {
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        fun destroy() = lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private class Decor : View(RuntimeEnvironment.getApplication()) {
        var installed: OnSystemUiVisibilityChangeListener? = null
            private set
        override fun setOnSystemUiVisibilityChangeListener(listener: OnSystemUiVisibilityChangeListener?) {
            super.setOnSystemUiVisibilityChangeListener(listener)
            installed = listener
        }
        fun dispatch() = dispatchSystemUiVisibilityChanged(SYSTEM_UI_FLAG_FULLSCREEN)
    }

    @Test fun `view destruction removes listener and invalidates captured callback`() {
        val decor = Decor()
        val owner = Owner()
        val listener = PlayerSystemUiListener()
        var calls = 0
        listener.attach(decor, owner) { calls++ }
        val captured = decor.installed!!
        decor.dispatch()
        assertEquals(1, calls)

        owner.destroy()
        assertNull(decor.installed)
        assertNull(decor.getTag(R.id.playerSystemUiListener))
        assertEquals(0, owner.lifecycle.observerCount)
        decor.dispatch()
        captured.onSystemUiVisibilityChange(0)
        assertEquals(1, calls)
        listener.detach() // Explicit onDestroyView cleanup is safe after the lifecycle event.
    }

    @Test fun `portrait landscape cycles detach and install only one fresh listener`() {
        val decor = Decor()
        val owner = Owner()
        val listener = PlayerSystemUiListener()
        var calls = 0
        repeat(10) {
            listener.attach(decor, owner) { calls++ }
            val captured = decor.installed!!
            decor.dispatch()
            assertEquals(1, owner.lifecycle.observerCount)
            listener.detach()
            listener.detach()
            assertNull(decor.installed)
            assertNull(decor.getTag(R.id.playerSystemUiListener))
            assertEquals(0, owner.lifecycle.observerCount)
            captured.onSystemUiVisibilityChange(0)
        }
        assertEquals(10, calls)
    }

    @Test fun `old player teardown cannot remove replacement player listener`() {
        val decor = Decor()
        val oldOwner = Owner()
        val newOwner = Owner()
        val old = PlayerSystemUiListener()
        val replacement = PlayerSystemUiListener()
        var oldCalls = 0
        var newCalls = 0
        old.attach(decor, oldOwner) { oldCalls++ }
        val captured = decor.installed!!
        replacement.attach(decor, newOwner) { newCalls++ }
        val current = decor.installed
        assertEquals(0, oldOwner.lifecycle.observerCount)
        oldOwner.destroy()
        old.detach()
        assertSame(current, decor.installed)
        captured.onSystemUiVisibilityChange(0)
        decor.dispatch()
        assertEquals(0, oldCalls)
        assertEquals(1, newCalls)
        newOwner.destroy()
        assertNull(decor.installed)
    }

    @Test fun `reattach releases prior decor and view lifecycle`() {
        val first = Decor()
        val second = Decor()
        val oldOwner = Owner()
        val newOwner = Owner()
        val listener = PlayerSystemUiListener()
        var calls = 0
        listener.attach(first, oldOwner) { fail("stale callback") }
        val captured = first.installed!!
        listener.attach(second, newOwner) { calls++ }
        assertNull(first.installed)
        assertNull(first.getTag(R.id.playerSystemUiListener))
        assertEquals(0, oldOwner.lifecycle.observerCount)
        oldOwner.destroy()
        captured.onSystemUiVisibilityChange(0)
        second.dispatch()
        assertEquals(1, calls)
        newOwner.destroy()
        assertNull(second.installed)
    }

    @Test fun `repeated landscape layout replaces rather than stacks callbacks`() {
        val decor = Decor()
        val owner = Owner()
        val listener = PlayerSystemUiListener()
        listener.attach(decor, owner) { fail("previous layout callback") }
        val captured = decor.installed!!
        var calls = 0
        listener.attach(decor, owner) { calls++ }
        captured.onSystemUiVisibilityChange(0)
        decor.dispatch()
        assertEquals(1, calls)
        assertEquals(1, owner.lifecycle.observerCount)
        owner.destroy()
        assertNull(decor.installed)
    }

    @Test fun `destroyed view cannot take ownership from active player`() {
        val decor = Decor()
        val active = PlayerSystemUiListener()
        var calls = 0
        active.attach(decor, Owner()) { calls++ }
        val installed = decor.installed
        val destroyed = Owner().apply { destroy() }
        PlayerSystemUiListener().attach(decor, destroyed) { fail("destroyed owner") }
        assertSame(installed, decor.installed)
        decor.dispatch()
        assertEquals(1, calls)
        assertEquals(0, destroyed.lifecycle.observerCount)
        active.detach()
    }
}

package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28], qualifiers = "land")
class PlayerPinchChatTest {
    private enum class Mode { HIDDEN, SIDEBAR, FLOATING }

    @Test fun `pinch restores hidden chat with floating enabled`() = checkRollback(Mode.HIDDEN, true)

    @Test fun `pinch restores sidebar chat with floating enabled`() = checkRollback(Mode.SIDEBAR, true)

    @Test fun `pinch restores floating chat with floating enabled`() = checkRollback(Mode.FLOATING, true)

    @Test fun `pinch restores hidden chat with floating disabled`() = checkRollback(Mode.HIDDEN, false)

    @Test fun `pinch restores sidebar chat with floating disabled`() = checkRollback(Mode.SIDEBAR, false)

    @Test fun `rollback preserves an unset saved chat preference`() {
        val harness = Harness(Mode.SIDEBAR, true)
        harness.prefs.edit { remove(C.KEY_CHAT_OPENED) }
        harness.doubleTap()
        harness.claimPinch()
        harness.assertMode(Mode.SIDEBAR, savedOpen = null)
    }

    @Test fun `double tap keeps its normal cycle when pinch does not take over`() {
        for ((from, to, floatingAllowed) in listOf(
            Triple(Mode.HIDDEN, Mode.SIDEBAR, true),
            Triple(Mode.SIDEBAR, Mode.FLOATING, true),
            Triple(Mode.FLOATING, Mode.HIDDEN, true),
            Triple(Mode.HIDDEN, Mode.SIDEBAR, false),
            Triple(Mode.SIDEBAR, Mode.HIDDEN, false),
        )) {
            val harness = Harness(from, floatingAllowed)
            harness.doubleTap()
            assertTrue(harness.arbiter.onPointerAdded(2))
            assertFalse(harness.arbiter.onScaleUpdate(1.01f))
            harness.assertMode(to)
        }
    }

    @Test fun `ordinary pinch leaves each chat mode unchanged`() {
        for (mode in Mode.entries) {
            val harness = Harness(mode, true)
            harness.claimPinch()
            harness.assertMode(mode)
        }
        for (mode in listOf(Mode.HIDDEN, Mode.SIDEBAR)) {
            val harness = Harness(mode, false)
            harness.claimPinch()
            harness.assertMode(mode)
        }
    }

    @Test fun `rejected second double tap claim does not replace the rollback state`() {
        val harness = Harness(Mode.HIDDEN, true)
        harness.doubleTap()
        harness.doubleTap() // The arbiter already belongs to the first callback.
        harness.claimPinch()
        harness.assertMode(Mode.HIDDEN)
    }

    private fun checkRollback(mode: Mode, floatingAllowed: Boolean) {
        val harness = Harness(mode, floatingAllowed)
        harness.doubleTap()
        harness.claimPinch()
        harness.assertMode(mode)
    }

    /**
     * Exercise the real listener, fragment chat transitions, pinch entry and Android
     * views/preferences. Only fragment attachment and the network chat fragment are
     * mocked; no chat-state or rollback methods are stubbed.
     */
    private class Harness(initialMode: Mode, floatingAllowed: Boolean) {
        private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
        val prefs = context.prefs()
        private val binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        private val chatView = View(context)
        private val fragment = Mockito.mock(PlayerFragment::class.java,
            Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))
        val arbiter = PlayerGestureArbiter(0.02f)
        private val listener = PlayerGestureListener(context, fragment, doubleTapEnabled = true)

        init {
            prefs.edit { clear(); putBoolean(C.FLOATING_CHAT_ENABLED, floatingAllowed) }
            Mockito.doReturn(context).`when`(fragment).context
            Mockito.doReturn(binding.root).`when`(fragment).view
            Mockito.doReturn(android.os.Bundle()).`when`(fragment).arguments
            ReflectionHelpers.setField(fragment, "_binding", binding)
            ReflectionHelpers.setField(fragment, "gestureArbiter", arbiter)
            ReflectionHelpers.setField(fragment, "chatWidthLandscape", 240)
            val chat = Mockito.mock(ChatFragment::class.java)
            Mockito.doReturn(chatView).`when`(chat).view
            ReflectionHelpers.setField(fragment, "chatFragment", chat)
            binding.chatFragmentContainer.addView(chatView)
            fragment.hideChat()
            if (initialMode != Mode.HIDDEN) fragment.showChat()
            if (initialMode == Mode.FLOATING) fragment.cycleChatMode()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            arbiter.onSequenceStarted()
        }

        fun doubleTap() {
            val event = MotionEvent.obtain(0, 100, MotionEvent.ACTION_DOWN, 300f, 200f, 0)
            try {
                assertTrue(listener.onDoubleTap(event))
            } finally {
                event.recycle()
            }
        }

        fun claimPinch() {
            val supersededDoubleTap = arbiter.owner == PlayerGestureArbiter.Owner.DOUBLE_TAP_CHAT
            assertTrue(arbiter.onPointerAdded(2))
            assertTrue(arbiter.onScaleUpdate(1.1f))
            val event = MotionEvent.obtain(0, 120, MotionEvent.ACTION_MOVE, 300f, 200f, 0)
            try {
                PlayerFragment::class.java.getDeclaredMethod(
                    "beginPinch", Boolean::class.javaPrimitiveType, MotionEvent::class.java,
                ).apply { isAccessible = true }.invoke(fragment, supersededDoubleTap, event)
            } finally {
                event.recycle()
            }
        }

        fun assertMode(mode: Mode, savedOpen: Boolean? = mode != Mode.HIDDEN) {
            assertEquals(mode != Mode.HIDDEN, ReflectionHelpers.getField<Boolean>(fragment, "isChatOpen"))
            assertEquals(mode == Mode.FLOATING, ReflectionHelpers.getField<Boolean>(fragment, "isFloatingChatEnabled"))
            assertEquals(savedOpen != null, prefs.contains(C.KEY_CHAT_OPENED))
            if (savedOpen != null) assertEquals(savedOpen, prefs.getBoolean(C.KEY_CHAT_OPENED, !savedOpen))
            assertSame(if (mode == Mode.FLOATING) binding.floatingChatContainer else binding.chatFragmentContainer, chatView.parent)
            assertEquals(if (mode == Mode.SIDEBAR) View.VISIBLE else View.GONE, binding.chatLayout.visibility)
            assertEquals(if (mode == Mode.SIDEBAR) 240 else 0, (binding.playerLayout.layoutParams as FrameLayout.LayoutParams).marginEnd)
            assertEquals(if (mode == Mode.FLOATING) View.VISIBLE else View.GONE, binding.floatingChatRoot.visibility)
            // Allow old animation callbacks to run too: they must not undo the restored state.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            assertEquals(if (mode == Mode.FLOATING) View.VISIBLE else View.GONE, binding.floatingChatRoot.visibility)
            if (mode == Mode.FLOATING) assertEquals(1f, binding.floatingChatRoot.alpha, 0.001f)
        }
    }
}

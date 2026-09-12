package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class MediaSeekFallbackTest {
    private val player: Player = mock()

    private fun intent(key: Int, action: Int = KeyEvent.ACTION_DOWN) =
        Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, key))

    @Test fun `unhandled next seeks forward once`() {
        whenever(player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD)).thenReturn(true)
        assertTrue(handleMediaSeekFallback(intent(KeyEvent.KEYCODE_MEDIA_NEXT), false, player))
        verify(player).isCommandAvailable(Player.COMMAND_SEEK_FORWARD)
        verify(player).seekForward()
        verifyNoMoreInteractions(player)
    }

    @Test fun `unhandled previous seeks back once`() {
        whenever(player.isCommandAvailable(Player.COMMAND_SEEK_BACK)).thenReturn(true)
        assertTrue(handleMediaSeekFallback(intent(KeyEvent.KEYCODE_MEDIA_PREVIOUS), false, player))
        verify(player).isCommandAvailable(Player.COMMAND_SEEK_BACK)
        verify(player).seekBack()
        verifyNoMoreInteractions(player)
    }

    @Test fun `handled keys never seek twice`() {
        assertTrue(handleMediaSeekFallback(intent(KeyEvent.KEYCODE_MEDIA_NEXT), true, player))
        assertTrue(handleMediaSeekFallback(intent(KeyEvent.KEYCODE_MEDIA_PREVIOUS), true, player))
        verifyNoInteractions(player)
    }

    @Test fun `key up and play pause remain untouched`() {
        assertFalse(handleMediaSeekFallback(intent(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP), false, player))
        for (key in listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_VOLUME_UP)) {
            assertFalse(handleMediaSeekFallback(intent(key), false, player))
        }
        verifyNoInteractions(player)
    }

    @Test fun `missing wrong type and unrelated intents are ignored`() {
        for (input in listOf(Intent(), Intent(Intent.ACTION_MEDIA_BUTTON),
            Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, Bundle()),
            intent(KeyEvent.KEYCODE_MEDIA_NEXT).setAction(Intent.ACTION_VIEW))) {
            assertFalse(handleMediaSeekFallback(input, false, player))
        }
        verifyNoInteractions(player)
    }

    @Test fun `non seekable players do not consume keys`() {
        assertFalse(handleMediaSeekFallback(intent(KeyEvent.KEYCODE_MEDIA_NEXT), false, player))
        assertFalse(handleMediaSeekFallback(intent(KeyEvent.KEYCODE_MEDIA_PREVIOUS), false, player))
        verify(player).isCommandAvailable(Player.COMMAND_SEEK_FORWARD)
        verify(player).isCommandAvailable(Player.COMMAND_SEEK_BACK)
        verifyNoMoreInteractions(player)
    }

    @Test fun `canceled key down is ignored`() {
        val event = KeyEvent.changeFlags(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT), KeyEvent.FLAG_CANCELED)
        assertFalse(handleMediaSeekFallback(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, event), false, player))
        verifyNoInteractions(player)
    }
}

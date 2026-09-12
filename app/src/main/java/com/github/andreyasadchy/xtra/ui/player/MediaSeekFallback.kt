package com.github.andreyasadchy.xtra.ui.player

import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.Player

/** Only supplement media-session dispatch; never seek twice for a handled event. */
internal fun handleMediaSeekFallback(intent: Intent, alreadyHandled: Boolean, player: Player): Boolean {
    if (alreadyHandled) return true
    if (intent.action != Intent.ACTION_MEDIA_BUTTON) return false
    val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        ?: return false
    if (event.action != KeyEvent.ACTION_DOWN || event.isCanceled) return false
    return when (event.keyCode) {
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
            if (!player.isCommandAvailable(Player.COMMAND_SEEK_BACK)) return false
            player.seekBack()
            true
        }
        KeyEvent.KEYCODE_MEDIA_NEXT -> {
            if (!player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD)) return false
            player.seekForward()
            true
        }
        else -> false
    }
}

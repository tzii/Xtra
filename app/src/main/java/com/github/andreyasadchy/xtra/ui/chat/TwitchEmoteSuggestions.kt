package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote

internal fun twitchEmoteSuggestions(emotes: List<TwitchEmote>, channelId: String?): List<Emote> =
    emotes.sortedByDescending { it.ownerId == channelId }.map { emote ->
        Emote(
            name = emote.name,
            url1x = emote.url1x,
            url2x = emote.url2x,
            url3x = emote.url3x,
            url4x = emote.url4x,
            format = emote.format,
        )
    }

/** Replace only Twitch entries; preserve chatters and third-party emote precedence. */
internal fun replaceTwitchSuggestions(target: MutableList<Any?>, emotes: List<Emote>) {
    synchronized(target) {
        target.removeAll { it is Emote && !it.thirdParty }
        for (emote in emotes) {
            if (!emote.name.isNullOrBlank() && emote !in target) {
                target.add(emote)
            }
        }
    }
}

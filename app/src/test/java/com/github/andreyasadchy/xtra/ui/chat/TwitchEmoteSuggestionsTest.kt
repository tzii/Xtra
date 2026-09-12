package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import org.junit.Assert.*
import org.junit.Test

class TwitchEmoteSuggestionsTest {
    @Test fun `Twitch entries populate suggestions without any third-party emotes`() {
        val target = mutableListOf<Any?>()
        replaceTwitchSuggestions(target, twitchEmoteSuggestions(listOf(TwitchEmote("25", "Kappa")), "channel"))
        val emote = target.single() as Emote
        assertEquals("Kappa", emote.name)
        assertEquals(":Kappa", emote.toString())
        assertFalse(emote.thirdParty)
    }

    @Test fun `conversion preserves all image variants and prioritizes current channel`() {
        val suggestions = twitchEmoteSuggestions(listOf(
            TwitchEmote("1", "Other", ownerId = "other"),
            TwitchEmote("2", "Channel", url1x = "one", url2x = "two", url3x = "three",
                url4x = "four", format = "png", ownerId = "channel"),
        ), "channel")
        assertEquals(listOf("Channel", "Other"), suggestions.map { it.name })
        with(suggestions.first()) {
            assertEquals("one", url1x)
            assertEquals("two", url2x)
            assertEquals("three", url3x)
            assertEquals("four", url4x)
            assertEquals("png", format)
        }
    }

    @Test fun `duplicates and blank names do not enter autocomplete`() {
        val target = mutableListOf<Any?>()
        val suggestions = twitchEmoteSuggestions(listOf(
            TwitchEmote("1", "Kappa"), TwitchEmote("2", "Kappa"),
            TwitchEmote("3", null), TwitchEmote("4", "  "),
        ), null)
        repeat(3) { replaceTwitchSuggestions(target, suggestions) }
        assertEquals(listOf("Kappa"), target.filterIsInstance<Emote>().map { it.name })
    }

    @Test fun `third-party precedence and chatter entries survive refresh`() {
        val thirdParty = Emote(name = "Shared", source = Emote.CHANNEL_STV)
        val chatter = Any()
        val target = mutableListOf<Any?>(thirdParty, chatter)
        replaceTwitchSuggestions(target, twitchEmoteSuggestions(listOf(
            TwitchEmote("1", "Shared"), TwitchEmote("2", "Kappa"),
        ), "channel"))
        assertSame(thirdParty, target.first())
        assertTrue(target.contains(chatter))
        assertEquals(listOf("Shared", "Kappa"), target.filterIsInstance<Emote>().map { it.name })
    }

    @Test fun `new Twitch snapshot replaces stale suggestions and refreshes image metadata`() {
        val target = mutableListOf<Any?>()
        replaceTwitchSuggestions(target, twitchEmoteSuggestions(listOf(
            TwitchEmote("1", "OldSub"), TwitchEmote("2", "Kappa", url1x = "cached"),
        ), "old-channel"))
        replaceTwitchSuggestions(target, twitchEmoteSuggestions(listOf(
            TwitchEmote("2", "Kappa", url1x = "fresh"), TwitchEmote("3", "NewSub"),
        ), "new-channel"))
        val emotes = target.filterIsInstance<Emote>()
        assertEquals(listOf("Kappa", "NewSub"), emotes.map { it.name })
        assertEquals("fresh", emotes.first().url1x)
    }

    @Test fun `empty snapshot removes only Twitch entries`() {
        val thirdParty = Emote(name = "Party", source = Emote.GLOBAL_BTTV)
        val target = mutableListOf<Any?>(thirdParty, "chatter", Emote(name = "OldSub"))
        replaceTwitchSuggestions(target, emptyList())
        assertEquals(listOf(thirdParty, "chatter"), target)
    }
}

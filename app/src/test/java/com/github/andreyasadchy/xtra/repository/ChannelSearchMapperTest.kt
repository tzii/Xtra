package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelsResponse
import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearch
import com.github.andreyasadchy.xtra.model.ui.ChannelSearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSearchMapperTest {

    @Test
    fun `gql query null node maps to null`() {
        assertNull(ChannelSearchMapper.fromGqlQuery(null))
    }

    @Test
    fun `gql persisted live stream carries viewer count`() {
        val item = ChannelSearchMapper.fromGqlPersisted(
            SearchChannelsResponse.User(
                id = "42",
                login = "gorgc",
                displayName = "Gorgc",
                followers = SearchChannelsResponse.Followers(totalCount = 1000),
                stream = SearchChannelsResponse.Stream(viewersCount = 1234),
            )
        )
        assertTrue(item.isLive == true)
        assertEquals(1234, item.viewerCount)
        assertEquals(1000, item.followerCount)
        assertNull(item.streamTitle)
        assertNull(item.gameName)
    }

    @Test
    fun `gql persisted offline channel keeps followers without stream metadata`() {
        val item = ChannelSearchMapper.fromGqlPersisted(
            SearchChannelsResponse.User(id = "42", login = "gorgc", displayName = "Gorgc")
        )
        assertTrue(item.isLive != true)
        assertNull(item.viewerCount)
    }

    @Test
    fun `helix maps title game and start time`() {
        val item = ChannelSearchMapper.fromHelix(
            ChannelSearch(
                id = "42",
                login = "gorgc",
                displayName = "Gorgc",
                isLive = true,
                gameName = "Dota 2",
                title = "Ranked grind",
                startedAt = "2026-08-16T10:00:00Z",
            )
        )
        assertTrue(item.isLive == true)
        assertEquals("Ranked grind", item.streamTitle)
        assertEquals("Dota 2", item.gameName)
        assertEquals("2026-08-16T10:00:00Z", item.startedAt)
        assertNull(item.viewerCount)
    }

    @Test
    fun `helix blank metadata degrades cleanly`() {
        val item = ChannelSearchMapper.fromHelix(
            ChannelSearch(id = "42", login = "gorgc", displayName = "Gorgc", isLive = true, title = "  ", gameName = "")
        )
        assertTrue(item.isLive == true)
        assertNull(item.streamTitle)
        assertNull(item.gameName)
    }

    @Test
    fun `helix offline channel maps identity and rows gate stream display`() {
        val item = ChannelSearchMapper.fromHelix(
            ChannelSearch(id = "42", login = "gorgc", displayName = "Gorgc", isLive = false, title = "old title")
        )
        assertTrue(item.isLive != true)
        // Helix returns the last broadcast title; the row only shows stream
        // metadata while live, so offline rows keep follower presentation.
        assertEquals("old title", item.streamTitle)
        assertNull(item.viewerCount)
    }

    @Test
    fun `content equality detects live-state and metadata changes`() {
        val offline = ChannelSearchItem(id = "42", login = "gorgc", followerCount = 10)
        val live = offline.copy(isLive = true, streamTitle = "Ranked grind", viewerCount = 500)
        assertFalse(offline == live)
        assertEquals(offline, offline.copy(profileImageURL = offline.profileImageURL))
    }
}

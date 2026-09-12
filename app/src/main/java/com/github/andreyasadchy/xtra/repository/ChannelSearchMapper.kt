package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.graphql.SearchChannelsQuery
import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelsResponse
import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearch
import com.github.andreyasadchy.xtra.model.ui.ChannelSearchItem

/**
 * Mapping for channel search results into [ChannelSearchItem]. Optional live
 * metadata is carried when available and never blocks rendering; viewer count
 * stays absent for Helix because channel search does not provide it, and no
 * per-row enrichment request is introduced.
 */
object ChannelSearchMapper {

    fun fromGqlQuery(node: SearchChannelsQuery.Node?): ChannelSearchItem? {
        if (node == null) return null
        val stream = node.stream
        return ChannelSearchItem(
            id = node.id,
            login = node.login,
            name = node.displayName,
            profileImageURL = node.profileImageURL,
            followerCount = node.followers?.totalCount,
            isLive = stream?.viewersCount != null,
            streamId = stream?.id,
            streamTitle = stream?.title,
            gameName = stream?.game?.displayName,
            viewerCount = stream?.viewersCount,
            startedAt = stream?.createdAt?.toString(),
            thumbnailUrl = stream?.previewImageURL,
        )
    }

    fun fromGqlPersisted(user: SearchChannelsResponse.User): ChannelSearchItem {
        return ChannelSearchItem(
            id = user.id,
            login = user.login,
            name = user.displayName,
            profileImageURL = user.profileImageURL,
            followerCount = user.followers?.totalCount,
            isLive = user.stream?.viewersCount != null,
            viewerCount = user.stream?.viewersCount,
        )
    }

    fun fromHelix(channel: ChannelSearch): ChannelSearchItem {
        return ChannelSearchItem(
            id = channel.id,
            login = channel.login,
            name = channel.displayName,
            profileImageURL = channel.profileImageURL,
            isLive = channel.isLive,
            streamTitle = channel.title?.takeIf { it.isNotBlank() },
            gameName = channel.gameName?.takeIf { it.isNotBlank() },
            startedAt = channel.startedAt,
        )
    }
}

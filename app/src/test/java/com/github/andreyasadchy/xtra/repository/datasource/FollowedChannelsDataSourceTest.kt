package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import com.apollographql.apollo.api.ApolloResponse
import com.github.andreyasadchy.xtra.graphql.UsersLastBroadcastQuery
import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.PageInfo
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedChannelsResponse
import com.github.andreyasadchy.xtra.model.ui.LocalFollowChannel
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.util.UUID

class FollowedChannelsDataSourceTest {
    private val graphQL: GraphQLRepository = mock()
    private val localFollows: LocalFollowChannelRepository = mock()
    private val headers = mapOf(C.HEADER_TOKEN to "test-only-token")

    private suspend fun source(local: List<LocalFollowChannel> = emptyList()): FollowedChannelsDataSource {
        whenever(localFollows.loadFollows()).thenReturn(local)
        whenever(graphQL.loadQueryUsersLastBroadcast(eq("OkHttp"), eq(headers), any(), isNull()))
            .thenReturn(ApolloResponse.Builder(UsersLastBroadcastQuery(), UUID.randomUUID())
                .data(UsersLastBroadcastQuery.Data(emptyList())).build())
        return FollowedChannelsDataSource(
            sort = "login", order = "asc", userId = "viewer", localFollowsChannel = localFollows,
            offlineRepository = mock(), bookmarksRepository = mock(), gqlHeaders = headers,
            graphQLRepository = graphQL, helixHeaders = emptyMap(), helixRepository = mock(),
            enableIntegrity = true, apiPref = listOf(C.GQL_PERSISTED_QUERY), networkLibrary = "OkHttp",
        )
    }

    private fun page(id: String, cursor: String?, hasNext: Boolean) = FollowedChannelsResponse(
        data = FollowedChannelsResponse.Data(FollowedChannelsResponse.FollowedData(
            FollowedChannelsResponse.Users(listOf(FollowedChannelsResponse.Item(
                FollowedChannelsResponse.User(id, "user$id", "User$id", "https://example.com/$id.png"), cursor,
            )), PageInfo(hasNext)),
        )),
    )

    private suspend fun firstPage(source: FollowedChannelsDataSource): PagingSource.LoadResult.Page<Int, User> {
        whenever(graphQL.loadFollowedChannels("OkHttp", headers, 100, null))
            .thenReturn(page("1", "page-2", true))
        return source.load(PagingSource.LoadParams.Refresh(null, 100, false)) as PagingSource.LoadResult.Page
    }

    @Test fun `appended account channels are returned and final page ends pagination`() = runTest {
        val source = source()
        val first = firstPage(source)
        assertEquals(2, first.nextKey)
        whenever(graphQL.loadFollowedChannels("OkHttp", headers, 100, "page-2"))
            .thenReturn(page("2", "end", false))
        val second = source.load(PagingSource.LoadParams.Append(2, 100, false)) as PagingSource.LoadResult.Page
        assertEquals(listOf("2"), second.data.map { it.id })
        assertTrue(second.data.single().accountFollow)
        assertFalse(second.data.single().localFollow)
        assertNull(second.nextKey)
        verify(graphQL).loadQueryUsersLastBroadcast("OkHttp", headers, listOf("2"), null)
    }

    @Test fun `first page still merges overlapping local and account follows`() = runTest {
        val source = source(listOf(LocalFollowChannel("1", "user1", "User1")))
        val first = firstPage(source)
        assertEquals(1, first.data.size)
        assertTrue(first.data.single().accountFollow)
        assertTrue(first.data.single().localFollow)
        assertEquals(2, first.nextKey)
    }

    @Test fun `integrity failures remain errors rather than empty appended pages`() = runTest {
        val source = source()
        firstPage(source)
        whenever(graphQL.loadFollowedChannels("OkHttp", headers, 100, "page-2"))
            .thenReturn(FollowedChannelsResponse(errors = listOf(Error("failed integrity check"))))
        val result = source.load(PagingSource.LoadParams.Append(2, 100, false))
        assertTrue(result is PagingSource.LoadResult.Error)
        assertEquals("failed integrity check", (result as PagingSource.LoadResult.Error).throwable.message)
    }
}

package com.github.andreyasadchy.xtra.model.ui

import android.os.Parcelable
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.parcelize.Parcelize

/**
 * Channel search result: channel identity/profile data plus optional live
 * stream metadata. Keeps search-only playback fields out of the shared
 * [User] model.
 */
@Parcelize
data class ChannelSearchItem(
    val id: String? = null,
    val login: String? = null,
    val name: String? = null,
    val profileImageURL: String? = null,
    val followerCount: Int? = null,
    val isLive: Boolean? = false,
    val streamId: String? = null,
    val streamTitle: String? = null,
    val gameName: String? = null,
    val viewerCount: Int? = null,
    val startedAt: String? = null,
    val thumbnailUrl: String? = null,
) : Parcelable {
    val profileImage: String?
        get() = TwitchApiHelper.getProfileImage(profileImageURL)
}

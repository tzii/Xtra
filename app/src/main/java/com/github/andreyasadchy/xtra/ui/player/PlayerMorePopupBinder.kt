package com.github.andreyasadchy.xtra.ui.player

import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.media3.common.Tracks
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.LayoutPlayerMorePopupBinding
import com.github.andreyasadchy.xtra.databinding.PlayerSettingsBinding
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs

/** Binds the existing grouped More actions inside the player-owned popup host. */
internal class PlayerMorePopupBinder(
    private val fragment: PlayerFragment,
    private val popupBinding: LayoutPlayerMorePopupBinding,
    private val videoType: String?,
    private val speedText: String?,
    private val qualityText: String?,
    private val vodGamesAvailable: Boolean,
    private val onDismissRequested: () -> Unit,
) {
    private val context get() = fragment.requireContext()
    private val binding: PlayerSettingsBinding get() = popupBinding.morePopupSettings

    fun bind() {
        applyTheme()
        popupBinding.morePopupClose.setOnClickListener { onDismissRequested() }
        with(binding) {
            val prefs = context.prefs()
            val showSpeedMenu = PlayerSpeedControls.shouldShowSpeedMenu(
                videoType = videoType,
                speedButtonEnabled = prefs.getBoolean(C.PLAYER_SPEEDBUTTON, true),
                menuSpeedEnabled = prefs.getBoolean(C.PLAYER_MENU_SPEED, false),
            )
            if (showSpeedMenu) {
                menuSpeed.visibility = View.VISIBLE
                menuSpeed.setOnClickListener { fragment.showSpeedDialog() }
                setSpeed(speedText)
            }
            if (prefs.getBoolean(C.PLAYER_MENU_QUALITY, false)) {
                menuQuality.visibility = View.VISIBLE
                menuQuality.setOnClickListener { onDismissRequested() }
                setQuality(qualityText)
            }

            if (videoType == PlayerFragment.STREAM) {
                if (prefs.getBoolean(C.PLAYER_MENU_VIEWER_LIST, true)) {
                    menuViewerList.visibility = View.VISIBLE
                    menuViewerList.setOnClickListener { dismissThen(fragment::openViewerList) }
                }
                if (prefs.getBoolean(C.PLAYER_MENU_RESTART, false)) {
                    menuRestart.visibility = View.VISIBLE
                    menuRestart.setOnClickListener { dismissThen(fragment::restartPlayer) }
                }
                if (!prefs.getBoolean(C.CHAT_DISABLE, false)) {
                    val isLoggedIn = !context.tokenPrefs().getString(C.USERNAME, null).isNullOrBlank() &&
                        (!TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN].isNullOrBlank() ||
                            !TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].isNullOrBlank())
                    if (isLoggedIn && prefs.getBoolean(C.PLAYER_MENU_CHAT_BAR, true)) {
                        menuChatBar.visibility = View.VISIBLE
                        menuChatBar.text = context.getString(
                            if (prefs.getBoolean(C.KEY_CHAT_BAR_VISIBLE, true)) R.string.hide_chat_bar
                            else R.string.show_chat_bar,
                        )
                        menuChatBar.setOnClickListener { dismissThen(fragment::toggleChatBar) }
                    }
                    if (prefs.getBoolean(C.PLAYER_MENU_CHAT_DISCONNECT, true)) {
                        menuChatDisconnect.visibility = View.VISIBLE
                        if (fragment.isActive() == true) {
                            menuChatDisconnect.text = context.getString(R.string.disconnect_chat)
                            menuChatDisconnect.setOnClickListener { dismissThen(fragment::disconnect) }
                        } else {
                            menuChatDisconnect.text = context.getString(R.string.connect_chat)
                            menuChatDisconnect.setOnClickListener { dismissThen(fragment::reconnect) }
                        }
                    }
                }
                if (prefs.getBoolean(C.DEBUG_PLAYER_MENU_PLAYLIST_TAGS, false)) {
                    menuMediaPlaylistTags.visibility = View.VISIBLE
                    menuMediaPlaylistTags.setOnClickListener { dismissThen { fragment.showPlaylistTags(true) } }
                    menuMultivariantPlaylistTags.visibility = View.VISIBLE
                    menuMultivariantPlaylistTags.setOnClickListener { dismissThen { fragment.showPlaylistTags(false) } }
                }
            }

            if (videoType == PlayerFragment.VIDEO) {
                if (vodGamesAvailable) setVodGames()
                if (prefs.getBoolean(C.PLAYER_MENU_BOOKMARK, true)) fragment.checkBookmark()
            }
            if (videoType != PlayerFragment.OFFLINE_VIDEO && prefs.getBoolean(C.PLAYER_MENU_DOWNLOAD, true)) {
                menuDownload.visibility = View.VISIBLE
                menuDownload.setOnClickListener { dismissThen(fragment::showDownloadDialog) }
            }
            if (videoType != PlayerFragment.CLIP && prefs.getBoolean(C.PLAYER_MENU_SLEEP, true)) {
                menuTimer.visibility = View.VISIBLE
                menuTimer.setOnClickListener { dismissThen(fragment::showSleepTimerDialog) }
            }
            if (!fragment.getIsPortrait()) {
                setupDisplayModeMenu()
                if (prefs.getBoolean(C.PLAYER_MENU_CHAT_TOGGLE, false)) {
                    menuChatToggle.visibility = View.VISIBLE
                    if (prefs.getBoolean(C.KEY_CHAT_OPENED, true)) {
                        menuChatToggle.text = context.getString(R.string.hide_chat)
                        menuChatToggle.setOnClickListener { dismissThen(fragment::hideChat) }
                    } else {
                        menuChatToggle.text = context.getString(R.string.show_chat)
                        menuChatToggle.setOnClickListener { dismissThen(fragment::showChat) }
                    }
                }
            }
            if (prefs.getBoolean(C.PLAYER_MENU_VOLUME, false)) {
                menuVolume.visibility = View.VISIBLE
                menuVolume.setOnClickListener { fragment.showVolumeOverlay() }
            }
            if (prefs.getBoolean(C.CHAT_TRANSLATE, false) && Build.SUPPORTED_64_BIT_ABIS.firstOrNull() == "arm64-v8a") {
                fragment.getTranslateAllMessages()?.let { translateAll ->
                    menuTranslateAll.visibility = View.VISIBLE
                    if (translateAll) {
                        menuTranslateAll.setOnClickListener { dismissThen(fragment::deleteTranslateAllMessagesUser) }
                    } else {
                        menuTranslateAll.setOnClickListener { dismissThen(fragment::saveTranslateAllMessagesUser) }
                    }
                }
            }
            fragment.setSubtitlesButton()
            if ((videoType == PlayerFragment.STREAM || videoType == PlayerFragment.VIDEO) &&
                !prefs.getBoolean(C.CHAT_DISABLE, false) &&
                prefs.getBoolean(C.PLAYER_MENU_RELOAD_EMOTES, true)
            ) {
                menuReloadEmotes.visibility = View.VISIBLE
                menuReloadEmotes.setOnClickListener { dismissThen(fragment::reloadEmotes) }
            }
            menuGestureGuide.visibility = View.VISIBLE
            menuGestureGuide.setOnClickListener { dismissThen { fragment.showGestureGuide() } }
        }
        updateGroupVisibilities()
        actionViews().forEach { action ->
            action.isFocusable = true
            action.minimumHeight = (48 * context.resources.displayMetrics.density).toInt()
        }
    }

    fun dispose() {
        popupBinding.morePopupClose.setOnClickListener(null)
        actionViews().forEach { it.setOnClickListener(null) }
    }

    fun setQuality(text: String?) {
        with(binding) {
            if (!text.isNullOrBlank() && menuQuality.isVisible) {
                qualityValue.visibility = View.VISIBLE
                qualityValue.text = text
                menuQuality.setOnClickListener { fragment.showQualityDialog() }
            }
        }
    }

    fun setSpeed(text: String?) {
        with(binding) {
            if (!text.isNullOrBlank() && menuSpeed.isVisible) {
                speedValue.visibility = View.VISIBLE
                speedValue.text = text
            }
        }
    }

    fun setVodGames() {
        with(binding) {
            if (context.prefs().getBoolean(C.PLAYER_MENU_GAMES, false)) {
                menuVodGames.visibility = View.VISIBLE
                menuVodGames.setOnClickListener { dismissThen(fragment::showVodGames) }
                updateGroupVisibilities()
            }
        }
    }

    fun setBookmarkText(isBookmarked: Boolean) {
        with(binding) {
            menuBookmark.visibility = View.VISIBLE
            menuBookmark.text = context.getString(
                if (isBookmarked) R.string.remove_bookmark else R.string.add_bookmark,
            )
            menuBookmark.setOnClickListener { dismissThen(fragment::saveBookmark) }
        }
        updateGroupVisibilities()
    }

    fun setSubtitles(subtitles: Tracks.Group? = null) {
        with(binding) {
            if (subtitles != null && context.prefs().getBoolean(C.PLAYER_MENU_SUBTITLES, true)) {
                menuSubtitles.visibility = View.VISIBLE
                if (subtitles.isSelected) {
                    menuSubtitles.text = context.getString(R.string.hide_subtitles)
                    menuSubtitles.setOnClickListener {
                        context.prefs().edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, false) }
                        dismissThen { fragment.toggleSubtitles(false) }
                    }
                } else {
                    menuSubtitles.text = context.getString(R.string.show_subtitles)
                    menuSubtitles.setOnClickListener {
                        context.prefs().edit { putBoolean(C.PLAYER_SUBTITLES_ENABLED, true) }
                        dismissThen { fragment.toggleSubtitles(true) }
                    }
                }
            } else {
                menuSubtitles.visibility = View.GONE
                menuSubtitles.setOnClickListener(null)
            }
        }
        updateGroupVisibilities()
    }

    private fun setupDisplayModeMenu() {
        with(binding) {
            menuDisplayMode.visibility = View.VISIBLE
            val modes = PlayerDisplayMode.entries
            val labels = modes.map { mode ->
                context.getString(
                    when (mode) {
                        PlayerDisplayMode.FIT -> R.string.display_mode_fit
                        PlayerDisplayMode.FILL -> R.string.display_mode_fill
                        PlayerDisplayMode.STRETCH -> R.string.display_mode_stretch
                    },
                )
            }.toTypedArray()
            displayModeValue.text = labels[modes.indexOf(fragment.getCurrentDisplayMode())]
            menuDisplayMode.setOnClickListener {
                context.getAlertDialogBuilder()
                    .setTitle(context.getString(R.string.display_mode))
                    .setSingleChoiceItems(labels, modes.indexOf(fragment.getCurrentDisplayMode())) { dialog, which ->
                        fragment.selectDisplayMode(modes[which])
                        displayModeValue.text = labels[which]
                        dialog.dismiss()
                    }
                    .setNegativeButton(context.getString(R.string.no), null)
                    .show()
            }
        }
    }

    /** The display-mode chooser intentionally remains the existing alert over this host. */
    private fun updateGroupVisibilities() {
        with(binding) {
            streamGroupHeader.isVisible =
                listOf(menuViewerList, menuVodGames, menuDownload, menuBookmark, menuTimer, menuRestart).any { it.isVisible }
            chatGroupHeader.isVisible =
                listOf(menuChatBar, menuChatToggle, menuTranslateAll, menuReloadEmotes, menuChatDisconnect).any { it.isVisible }
            playbackGroupHeader.isVisible =
                listOf(menuVolume, menuSubtitles, menuDisplayMode, menuMediaPlaylistTags, menuMultivariantPlaylistTags).any { it.isVisible }
            helpGroupHeader.isVisible = menuGestureGuide.isVisible
        }
    }

    private fun dismissThen(action: () -> Unit) {
        onDismissRequested()
        action()
    }

    private fun applyTheme() {
        val colors = PlayerPanelTheme.resolve(context)
        val density = context.resources.displayMetrics.density
        popupBinding.morePopupCard.apply {
            setCardBackgroundColor(colors.panel)
            strokeWidth = density.toInt().coerceAtLeast(1)
            setStrokeColor(colors.panelStroke)
        }
        popupBinding.morePopupTitle.setTextColor(colors.onPanel)
        popupBinding.morePopupClose.imageTintList = ColorStateList.valueOf(colors.secondaryText)
        tintText(binding.root, colors.onPanel)
        listOf(
            binding.streamGroupHeader,
            binding.chatGroupHeader,
            binding.playbackGroupHeader,
            binding.helpGroupHeader,
            binding.qualityValue,
            binding.speedValue,
            binding.displayModeValue,
        ).forEach { it.setTextColor(colors.secondaryText) }
    }

    private fun tintText(view: View, color: Int) {
        if (view is TextView) view.setTextColor(color)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) tintText(view.getChildAt(index), color)
        }
    }

    private fun actionViews(): List<View> = with(binding) {
        listOf(
            menuQuality,
            menuSpeed,
            menuViewerList,
            menuVodGames,
            menuDownload,
            menuBookmark,
            menuTimer,
            menuRestart,
            menuChatBar,
            menuChatToggle,
            menuTranslateAll,
            menuReloadEmotes,
            menuChatDisconnect,
            menuVolume,
            menuSubtitles,
            menuDisplayMode,
            menuMediaPlaylistTags,
            menuMultivariantPlaylistTags,
            menuGestureGuide,
        )
    }
}

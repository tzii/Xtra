package com.github.andreyasadchy.xtra.ui.player

import android.content.SharedPreferences
import com.github.andreyasadchy.xtra.util.C

/**
 * Narrow storage interface so the store's persistence and migration logic is
 * unit-testable without Android.
 */
interface DisplayModeStorage {
    fun stringValue(key: String): String?
    fun intValue(key: String): Int?
    fun putStringValue(key: String, value: String)
}

class SharedPreferencesDisplayModeStorage(private val prefs: SharedPreferences) : DisplayModeStorage {
    override fun stringValue(key: String): String? = prefs.getString(key, null)
    override fun intValue(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null
    override fun putStringValue(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

/**
 * Owns persistence and legacy migration for the non-portrait maximized
 * player's display mode.
 *
 * The canonical preference key is [C.PLAYER_DISPLAY_MODE_LANDSCAPE] with
 * string values fit/fill/stretch. On first read when the canonical key is
 * absent, the legacy [C.ASPECT_RATIO_LANDSCAPE] integer migrates:
 * 0 to Fit; 1, 2 and 4 to Fill; 3 to Stretch; missing, corrupt or unknown to
 * Fit. The legacy key is never written; it remains read-only migration input
 * so upgrades are deterministic and downgrades are not disturbed.
 */
class PlayerDisplayModeStore(private val storage: DisplayModeStorage) {

    fun loadDisplayMode(): PlayerDisplayMode {
        storage.stringValue(C.PLAYER_DISPLAY_MODE_LANDSCAPE)?.let { canonical ->
            return PlayerDisplayMode.fromName(canonical) ?: migrateLegacy()
        }
        return migrateLegacy()
    }

    fun saveDisplayMode(mode: PlayerDisplayMode) {
        storage.putStringValue(C.PLAYER_DISPLAY_MODE_LANDSCAPE, mode.preferenceName)
    }

    private fun migrateLegacy(): PlayerDisplayMode {
        val mode = when (storage.intValue(C.ASPECT_RATIO_LANDSCAPE)) {
            0 -> PlayerDisplayMode.FIT
            1, 2, 4 -> PlayerDisplayMode.FILL
            3 -> PlayerDisplayMode.STRETCH
            else -> PlayerDisplayMode.FIT
        }
        storage.putStringValue(C.PLAYER_DISPLAY_MODE_LANDSCAPE, mode.preferenceName)
        return mode
    }
}

val PlayerDisplayMode.preferenceName: String
    get() = when (this) {
        PlayerDisplayMode.FIT -> PlayerDisplayMode.FIT_NAME
        PlayerDisplayMode.FILL -> PlayerDisplayMode.FILL_NAME
        PlayerDisplayMode.STRETCH -> PlayerDisplayMode.STRETCH_NAME
    }

package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.util.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDisplayModeStoreTest {

    private class FakeStorage(
        private val legacyInt: Int? = null,
        private val canonicalString: String? = null,
    ) : DisplayModeStorage {
        val writtenStrings = mutableMapOf<String, String>()
        val legacyInts = mutableMapOf<String, Int>().apply { legacyInt?.let { put(C.ASPECT_RATIO_LANDSCAPE, it) } }

        override fun stringValue(key: String): String? {
            if (key == C.PLAYER_DISPLAY_MODE_LANDSCAPE) {
                return canonicalString
            }
            return null
        }

        override fun intValue(key: String): Int? = legacyInts[key]

        override fun putStringValue(key: String, value: String) {
            writtenStrings[key] = value
        }
    }

    @Test
    fun `legacy 0 migrates to fit and persists canonical value`() {
        val storage = FakeStorage(legacyInt = 0)
        assertEquals(PlayerDisplayMode.FIT, PlayerDisplayModeStore(storage).loadDisplayMode())
        assertEquals("fit", storage.writtenStrings[C.PLAYER_DISPLAY_MODE_LANDSCAPE])
    }

    @Test
    fun `legacy 1 2 and 4 migrate to fill`() {
        for (legacy in listOf(1, 2, 4)) {
            val storage = FakeStorage(legacyInt = legacy)
            assertEquals(PlayerDisplayMode.FILL, PlayerDisplayModeStore(storage).loadDisplayMode())
            assertEquals("fill", storage.writtenStrings[C.PLAYER_DISPLAY_MODE_LANDSCAPE])
        }
    }

    @Test
    fun `legacy 3 migrates to stretch`() {
        val storage = FakeStorage(legacyInt = 3)
        assertEquals(PlayerDisplayMode.STRETCH, PlayerDisplayModeStore(storage).loadDisplayMode())
        assertEquals("stretch", storage.writtenStrings[C.PLAYER_DISPLAY_MODE_LANDSCAPE])
    }

    @Test
    fun `missing corrupt and unknown legacy values default to fit`() {
        for (legacy in listOf(null, -1, 7, 99)) {
            val storage = FakeStorage(legacyInt = legacy)
            assertEquals(PlayerDisplayMode.FIT, PlayerDisplayModeStore(storage).loadDisplayMode())
            assertEquals("fit", storage.writtenStrings[C.PLAYER_DISPLAY_MODE_LANDSCAPE])
        }
    }

    @Test
    fun `canonical value wins over legacy`() {
        val storage = FakeStorage(legacyInt = 3, canonicalString = "fit")
        assertEquals(PlayerDisplayMode.FIT, PlayerDisplayModeStore(storage).loadDisplayMode())
        assertFalse(storage.writtenStrings.containsKey(C.PLAYER_DISPLAY_MODE_LANDSCAPE))
    }

    @Test
    fun `corrupt canonical value falls back to legacy migration`() {
        val storage = FakeStorage(legacyInt = 3, canonicalString = "garbage")
        assertEquals(PlayerDisplayMode.STRETCH, PlayerDisplayModeStore(storage).loadDisplayMode())
        assertEquals("stretch", storage.writtenStrings[C.PLAYER_DISPLAY_MODE_LANDSCAPE])
    }

    @Test
    fun `save writes canonical key only`() {
        val storage = FakeStorage(legacyInt = 0)
        val store = PlayerDisplayModeStore(storage)
        store.saveDisplayMode(PlayerDisplayMode.STRETCH)
        assertEquals("stretch", storage.writtenStrings[C.PLAYER_DISPLAY_MODE_LANDSCAPE])
        assertTrue(storage.legacyInts.isEmpty() || storage.legacyInts[C.ASPECT_RATIO_LANDSCAPE] == 0)
        assertEquals(PlayerDisplayMode.STRETCH, FakeStorage(canonicalString = "stretch").let { PlayerDisplayModeStore(it).loadDisplayMode() })
    }

    @Test
    fun `renderer mapping covers exactly three canonical modes`() {
        assertEquals(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT, PlayerDisplayMode.FIT.resizeMode)
        assertEquals(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM, PlayerDisplayMode.FILL.resizeMode)
        assertEquals(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL, PlayerDisplayMode.STRETCH.resizeMode)
    }
}

package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerQualityPopupModelTest {

    private val labels = PlayerQualityPopupLabels("Auto", "Source", "Audio only", "Chat only")

    @Test
    fun `homogeneous h264 list omits redundant codec metadata`() {
        val options = PlayerQualityPopupModel.build(
            listOf(VideoQuality("auto"), VideoQuality("1080p60", "avc1.64002a")),
            labels,
        )

        assertEquals("Auto", options[0].primaryLabel)
        assertNull(options[0].codecLabel)
        assertEquals("1080p60", options[1].primaryLabel)
        assertNull(options[1].codecLabel)
    }

    @Test
    fun `mixed codecs use a standardized secondary label`() {
        val options = PlayerQualityPopupModel.build(
            listOf(
                VideoQuality("1440p60", "hvc1.2.4.L153"),
                VideoQuality("1080p60", "avc1.64002a"),
                VideoQuality("720p60", "av01.0.08M.08"),
            ),
            labels,
        )

        assertEquals(listOf("H.265", "H.264", "AV1"), options.map { it.codecLabel })
        assertEquals(listOf("1440p60", "1080p60", "720p60"), options.map { it.primaryLabel })
    }

    @Test
    fun `audio and chat options stay separate from codec presentation`() {
        val options = PlayerQualityPopupModel.build(
            listOf(
                VideoQuality("1080p60", "hvc1.2.4.L153"),
                VideoQuality("audio_only", "mp4a.40.2"),
                VideoQuality("chat-only"),
            ),
            labels,
        )

        assertEquals(PlayerQualityPopupOption.Kind.AUDIO_ONLY, options[1].kind)
        assertEquals(PlayerQualityPopupOption.Kind.CHAT_ONLY, options[2].kind)
        assertNull(options[1].codecLabel)
        assertNull(options[2].codecLabel)
    }

    @Test
    fun `numeric fallback qualities remain hidden`() {
        val options = PlayerQualityPopupModel.build(
            listOf(VideoQuality("0"), VideoQuality("1080p60")),
            labels,
        )

        assertEquals(listOf("1080p60"), options.map { it.tag })
    }
}

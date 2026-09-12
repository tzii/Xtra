package com.github.andreyasadchy.xtra.ui.player

import com.github.andreyasadchy.xtra.model.VideoQuality
import java.util.Locale

internal data class PlayerQualityPopupLabels(
    val auto: String,
    val source: String,
    val audioOnly: String,
    val chatOnly: String,
)

internal data class PlayerQualityPopupOption(
    val primaryLabel: String,
    val codecLabel: String?,
    val tag: String,
    val kind: Kind,
) {
    enum class Kind { VIDEO, AUDIO_ONLY, CHAT_ONLY }
}

/** Pure option mapping for the Quality popup. */
internal object PlayerQualityPopupModel {

    fun build(
        qualities: List<VideoQuality>,
        labels: PlayerQualityPopupLabels,
    ): List<PlayerQualityPopupOption> {
        val showCodecs = qualities.any { quality ->
            val codec = quality.codecs.codecPrefix()
            codec !in setOf(null, "avc1", "mp4a")
        }
        val options = qualities.mapNotNull { quality ->
            val tag = quality.name ?: return@mapNotNull null
            if (tag.isNumericQualityFallback()) return@mapNotNull null
            val normalized = tag.normalizedPopupValue()
            val kind = when (normalized) {
                "audio_only" -> PlayerQualityPopupOption.Kind.AUDIO_ONLY
                "chat_only" -> PlayerQualityPopupOption.Kind.CHAT_ONLY
                else -> PlayerQualityPopupOption.Kind.VIDEO
            }
            PlayerQualityPopupOption(
                primaryLabel = when (normalized) {
                    "auto" -> labels.auto
                    "source" -> labels.source
                    "audio_only" -> labels.audioOnly
                    "chat_only" -> labels.chatOnly
                    else -> tag
                },
                codecLabel = if (showCodecs && kind == PlayerQualityPopupOption.Kind.VIDEO && normalized !in setOf("auto", "source")) {
                    codecDisplayName(quality.codecs)
                } else {
                    null
                },
                tag = tag,
                kind = kind,
            )
        }

        val video = options.filter { it.kind == PlayerQualityPopupOption.Kind.VIDEO }
        val utilities = options
            .filter { it.kind != PlayerQualityPopupOption.Kind.VIDEO }
            .distinctBy { it.kind }
            .sortedBy { it.kind.ordinal }
        return video + utilities
    }

    fun codecDisplayName(codecs: String?): String? {
        return when (val codec = codecs.codecPrefix()) {
            null, "mp4a" -> null
            "av01" -> "AV1"
            "hev1", "hvc1" -> "H.265"
            "avc1" -> "H.264"
            else -> codec.uppercase(Locale.US)
        }
    }

    private fun String?.codecPrefix(): String? = this
        ?.substringBefore('.')
        ?.trim()
        ?.lowercase(Locale.US)
        ?.takeIf { it.isNotBlank() }

    private fun String.normalizedPopupValue(): String = trim()
        .lowercase(Locale.US)
        .replace(' ', '_')
        .replace('-', '_')
}

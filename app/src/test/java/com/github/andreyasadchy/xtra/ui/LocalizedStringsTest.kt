package com.github.andreyasadchy.xtra.ui

import android.app.Application
import android.content.res.Configuration
import com.github.andreyasadchy.xtra.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Exercise packaged localized resources, including multi-byte and Windows-1252 edge cases. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class LocalizedStringsTest {
    @Test fun `localized text remains readable after source edits and resource packaging`() {
        val context = RuntimeEnvironment.getApplication()
        val cases = listOf(
            Triple("ar", R.string.games, "الألعاب"),
            Triple("de", R.string.select_quality, "Qualität auswählen"),
            Triple("es", R.string.connection_error, "Error al conectarse al servidor. Inténtalo de nuevo"),
            Triple("fr", R.string.downloads, "Téléchargements"),
            Triple("gl", R.string.connection_error, "Erro ao conectar co servidor. Téntao de novo."),
            Triple("id", R.string.irc_notice_unavailable_command, "Maaf, “%s” tidak tersedia melalui klien ini."),
            Triple("it", R.string.select_quality, "Seleziona qualità"),
            Triple("ja", R.string.games, "ゲーム"),
            Triple("pt-BR", R.string.this_month, "Este mês"),
            Triple("ru", R.string.games, "Игры"),
            Triple("tr", R.string.popular, "Popüler"),
            Triple("zh-CN", R.string.games, "游戏"),
            Triple("zh-TW", R.string.games, "遊戲"),
        )
        for ((locale, resource, expected) in cases) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(locale))
            }
            val localized = context.createConfigurationContext(configuration)
            assertEquals(locale, expected, localized.getString(resource))
        }
    }
}

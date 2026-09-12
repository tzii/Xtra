package com.github.andreyasadchy.xtra.ui.channel.about

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class SocialLinkLabelTest {
    @Test fun `label includes destination host without common www prefix`() {
        assertEquals("Videos (example.com)", socialLinkLabel("Videos", "https://www.example.com/channel?ref=1"))
    }

    @Test fun `subdomain remains visible`() {
        assertEquals("Community (chat.example.com)", socialLinkLabel("Community", "https://chat.example.com/invite"))
    }

    @Test fun `userinfo and path cannot masquerade as the destination host`() {
        assertEquals("Trusted (actual.example)", socialLinkLabel("Trusted", "https://trusted.example@actual.example/trusted.example"))
    }

    @Test fun `missing relative or opaque URLs retain the original title`() {
        for (url in listOf(null, "", "/channel", "mailto:hello@example.com")) {
            assertEquals("Profile", socialLinkLabel("Profile", url))
        }
    }
}

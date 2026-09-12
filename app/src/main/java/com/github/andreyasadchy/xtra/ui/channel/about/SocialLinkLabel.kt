package com.github.andreyasadchy.xtra.ui.channel.about

import androidx.core.net.toUri

/** Show the parsed destination host, not user-info, path or a misleading link title. */
internal fun socialLinkLabel(title: String, url: String?): String {
    val host = url?.toUri()?.host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    return if (host != null) "$title ($host)" else title
}

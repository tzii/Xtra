package com.github.andreyasadchy.xtra.util.update

/** The nonce is fixed in the explicit PendingIntent's data, not in mutable extras. */
internal data class InstallCallbackKey(val sessionId: Int, val nonce: String)

internal object InstallCallbackPolicy {
    const val CONFIRM_INSTALL_ACTION = "android.content.pm.action.CONFIRM_INSTALL"

    // AOSP API 23-28 uses CONFIRM_PERMISSIONS; API 29+ uses CONFIRM_INSTALL.
    // Keep both exact formats for supported older systems and OEM backports.
    const val CONFIRM_PERMISSIONS_ACTION = "android.content.pm.action.CONFIRM_PERMISSIONS"

    fun matches(expected: InstallCallbackKey, sessionId: Int, nonce: String?, reportedSessionId: Int): Boolean =
        expected.sessionId >= 0 && expected.nonce.isNotBlank() &&
            expected.sessionId == sessionId && expected.nonce == nonce &&
            expected.sessionId == reportedSessionId

    fun allowsConfirmation(
        expectedSessionId: Int,
        action: String?,
        reportedSessionId: Int,
        exported: Boolean,
        enabled: Boolean,
        systemApplication: Boolean,
    ): Boolean = expectedSessionId >= 0 && (action == CONFIRM_INSTALL_ACTION || action == CONFIRM_PERMISSIONS_ACTION) &&
        reportedSessionId == expectedSessionId && exported && enabled && systemApplication
}

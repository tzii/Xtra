package com.github.andreyasadchy.xtra.util.update

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

internal object UpdateInstallIntents {
    private const val SCHEME = "thysttv-update"

    fun action(context: Context): String = "${context.packageName}.UPDATE_INSTALL_STATUS"

    fun callbackIntent(context: Context, key: InstallCallbackKey): Intent =
        Intent(context, UpdateInstallReceiver::class.java).apply {
            action = action(context)
            data = Uri.Builder().scheme(SCHEME).authority(context.packageName)
                .appendPath(key.sessionId.toString()).appendPath(key.nonce).build()
        }

    fun callback(context: Context, key: InstallCallbackKey): PendingIntent {
        // PackageInstaller fills status/session extras. API 35+ rejects an immutable
        // status receiver. The fixed explicit component and URI cannot be filled in.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, key.sessionId, callbackIntent(context, key), flags)
    }

    fun matches(context: Context, intent: Intent, expected: InstallCallbackKey): Boolean {
        val data = intent.data ?: return false
        if (intent.action != action(context) || data.scheme != SCHEME || data.authority != context.packageName ||
            data.query != null || data.fragment != null || data.pathSegments.size != 2) return false
        return InstallCallbackPolicy.matches(
            expected, data.pathSegments[0].toIntOrNull() ?: return false,
            data.pathSegments[1], intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1),
        )
    }

    /**
     * Never forward the received object. Keep only the system confirmation action,
     * resolved system component and correlated session ID. No URI grants, selectors,
     * ClipData, arbitrary data or nested extras cross into the launched Intent.
     */
    fun confirmation(context: Context, callback: Intent, sessionId: Int): Intent? {
        val candidate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            callback.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            callback.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        } ?: return null
        val target = candidate.resolveActivityInfo(context.packageManager, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return null
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        if (!InstallCallbackPolicy.allowsConfirmation(
                sessionId, candidate.action, candidate.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1),
                target.exported, target.enabled && target.applicationInfo.enabled,
                target.applicationInfo.flags and systemFlags != 0,
            )) return null
        return Intent(candidate.action).apply {
            component = ComponentName(target.packageName, target.name)
            putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
        }
    }
}

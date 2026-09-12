package com.github.andreyasadchy.xtra.util.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Only an explicit app-created status PendingIntent reaches this non-exported receiver. */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {
    @Inject lateinit var updater: UpdateManager

    override fun onReceive(context: Context, intent: Intent) {
        // Capture status while the UI is stopped. Never launch an Activity here.
        updater.onInstallStatus(intent)
    }
}

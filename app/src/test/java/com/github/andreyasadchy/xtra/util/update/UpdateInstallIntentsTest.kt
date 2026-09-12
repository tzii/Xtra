package com.github.andreyasadchy.xtra.util.update

import android.app.Application
import android.app.PendingIntent
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Adapter regressions. These do not substitute for a real signed PackageInstaller handoff. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28, 35])
class UpdateInstallIntentsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val confirmationAction get() = if (Build.VERSION.SDK_INT < 29)
        InstallCallbackPolicy.CONFIRM_PERMISSIONS_ACTION else InstallCallbackPolicy.CONFIRM_INSTALL_ACTION
    private val key = InstallCallbackKey(17, "test-attempt-token")

    @Test fun `callback receiver is private in the actual app manifest`() {
        val info = context.packageManager.getReceiverInfo(ComponentName(context, UpdateInstallReceiver::class.java), 0)
        assertFalse(info.exported)
    }

    @Test fun `callback is explicit unique per attempt and mutable where required`() {
        val first = UpdateInstallIntents.callback(context, key)
        val second = UpdateInstallIntents.callback(context, key.copy(nonce = "second-token"))
        try {
            val intent = shadowOf(first).savedIntent
            assertEquals(ComponentName(context, UpdateInstallReceiver::class.java), intent.component)
            assertEquals(UpdateInstallIntents.action(context), intent.action)
            assertEquals(listOf("17", key.nonce), intent.data!!.pathSegments)
            assertNotEquals(first, second)
            assertEquals(0, shadowOf(first).flags and PendingIntent.FLAG_IMMUTABLE)
            if (Build.VERSION.SDK_INT >= 31) assertNotEquals(0, shadowOf(first).flags and PendingIntent.FLAG_MUTABLE)
        } finally { first.cancel(); second.cancel() }
    }

    @Test fun `mutable fill in cannot replace fixed component action or token URI`() {
        val callback = UpdateInstallIntents.callbackIntent(context, key)
        callback.fillIn(Intent("untrusted.action", Uri.parse("content://untrusted/17"))
            .setComponent(ComponentName("untrusted.package", "FakeReceiver"))
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, 17), 0)
        assertEquals(ComponentName(context, UpdateInstallReceiver::class.java), callback.component)
        assertTrue(UpdateInstallIntents.matches(context, callback, key))
    }

    @Test fun `callback requires all fixed identity fields and the OS session extra`() {
        val callback = UpdateInstallIntents.callbackIntent(context, key)
        assertFalse(UpdateInstallIntents.matches(context, callback, key))
        callback.putExtra(PackageInstaller.EXTRA_SESSION_ID, 17)
        assertTrue(UpdateInstallIntents.matches(context, callback, key))
        assertFalse(UpdateInstallIntents.matches(context, Intent(callback).setAction("wrong"), key))
        assertFalse(UpdateInstallIntents.matches(context, Intent(callback).setData(callback.data!!.buildUpon().appendQueryParameter("x", "y").build()), key))
        assertFalse(UpdateInstallIntents.matches(context, Intent(callback).setData(callback.data!!.buildUpon().fragment("x").build()), key))
        assertFalse(UpdateInstallIntents.matches(context, Intent(callback).putExtra(PackageInstaller.EXTRA_SESSION_ID, 18), key))
        assertFalse(UpdateInstallIntents.matches(context, callback, key.copy(nonce = "old-attempt")))
    }

    private fun candidate(system: Boolean = true, exported: Boolean = true, enabled: Boolean = true): Intent {
        val component = ComponentName("test.system.installer", "test.system.installer.Confirm")
        val info = ActivityInfo().apply {
            packageName = component.packageName; name = component.className
            this.exported = exported; this.enabled = enabled
            applicationInfo = ApplicationInfo().apply {
                packageName = component.packageName; this.enabled = enabled
                flags = ApplicationInfo.FLAG_INSTALLED or if (system) ApplicationInfo.FLAG_SYSTEM else 0
            }
        }
        val pm = shadowOf(context.packageManager)
        pm.addOrUpdateActivity(info)
        pm.addIntentFilterForActivity(component, IntentFilter(confirmationAction).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        })
        return Intent(confirmationAction).setComponent(component)
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, 17)
    }
    private fun callback(candidate: Intent): Intent = Intent().putExtra(Intent.EXTRA_INTENT, candidate)

    @Test fun `confirmation rebuild discards grants data clip nested extras and flags`() {
        val input = candidate().apply {
            data = Uri.parse("content://untrusted/private")
            clipData = ClipData.newPlainText("private", "do not forward")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("unexpected", Intent(Intent.ACTION_VIEW))
        }
        val clean = UpdateInstallIntents.confirmation(context, callback(input), 17)!!
        assertNotSame(input, clean)
        assertEquals(confirmationAction, clean.action)
        assertEquals(input.component, clean.component)
        assertNull(clean.data); assertNull(clean.clipData); assertNull(clean.selector)
        assertEquals(0, clean.flags)
        assertEquals(setOf(PackageInstaller.EXTRA_SESSION_ID), clean.extras!!.keySet())
        assertEquals(17, clean.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1))
    }

    @Test fun `confirmation rejects third party target`() {
        assertNull(UpdateInstallIntents.confirmation(context, callback(candidate(system = false)), 17))
    }
    @Test fun `confirmation rejects nonexported target`() {
        assertNull(UpdateInstallIntents.confirmation(context, callback(candidate(exported = false)), 17))
    }
    @Test fun `confirmation rejects disabled target`() {
        assertNull(UpdateInstallIntents.confirmation(context, callback(candidate(enabled = false)), 17))
    }
    @Test fun `confirmation rejects missing nested intent and mismatched session`() {
        assertNull(UpdateInstallIntents.confirmation(context, Intent(), 17))
        assertNull(UpdateInstallIntents.confirmation(context, callback(candidate()), 18))
    }
    @Test fun `confirmation rejects wrong action even on system target`() {
        assertNull(UpdateInstallIntents.confirmation(context, callback(candidate().setAction(Intent.ACTION_VIEW)), 17))
    }
}

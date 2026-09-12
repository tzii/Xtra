package com.github.andreyasadchy.xtra.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogUpdateDownloadBinding
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.update.UpdateFailure
import com.github.andreyasadchy.xtra.util.update.UpdateHost
import com.github.andreyasadchy.xtra.util.update.UpdateManager
import com.github.andreyasadchy.xtra.util.update.UpdatePhase
import com.github.andreyasadchy.xtra.util.update.UpdateState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** One renderer per activity. Programmatic/lifecycle dismissal never cancels an attempt. */
internal class UpdateDialogController(
    private val activity: AppCompatActivity,
    private val updater: UpdateManager,
    private val host: UpdateHost,
) : DefaultLifecycleObserver {
    private var dialog: AlertDialog? = null
    private var windowBinding: UpdateDialogWindow? = null
    private var dialogKey: String? = null
    private var downloadBinding: DialogUpdateDownloadBinding? = null
    private var launchedInstallerThisResume = false

    init {
        activity.lifecycle.addObserver(this)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updater.state.collect { render(it) }
            }
        }
    }

    private fun render(state: UpdateState) {
        val attempt = state.attempt
        if (attempt == null || attempt.request.host != host) {
            clearDialog()
            return
        }
        val id = attempt.id
        when (state.phase) {
            UpdatePhase.DOWNLOADING, UpdatePhase.PREPARING, UpdatePhase.COMMITTING -> {
                val key = "$id/progress"
                if (dialogKey != key) {
                    clearDialog()
                    val binding = DialogUpdateDownloadBinding.inflate(activity.layoutInflater)
                    downloadBinding = binding
                    val progress = activity.getAlertDialogBuilder().setView(binding.root)
                        .setNegativeButton(android.R.string.cancel) { _, _ -> updater.cancel(id) }
                        .setOnCancelListener { updater.cancel(id) }.create()
                    progress.setCanceledOnTouchOutside(false)
                    progress.show()
                    track(progress, key)
                }
                downloadBinding?.let { binding ->
                    UpdateDownloadUi.bind(binding, state.bytesRead, state.totalBytes)
                    if (state.phase != UpdatePhase.DOWNLOADING) {
                        binding.downloadTitle.setText(if (state.phase == UpdatePhase.PREPARING)
                            R.string.update_preparing_install else R.string.update_waiting_installer)
                        binding.textView.setText(R.string.update_keep_open)
                        binding.progressBar.isIndeterminate = true
                        binding.progressPercent.text = ""
                    }
                }
            }
            UpdatePhase.READY -> {
                if (updater.canInstall()) {
                    clearDialog()
                    updater.commit(id)
                } else if (dialogKey != "$id/permission") {
                    clearDialog()
                    val permission = activity.getAlertDialogBuilder()
                        .setTitle(R.string.update_permission_title)
                        .setMessage(R.string.update_permission_message)
                        .setPositiveButton(R.string.update_open_settings, null)
                        .setNegativeButton(android.R.string.cancel) { _, _ -> updater.cancel(id) }
                        .setOnCancelListener { updater.cancel(id) }.create()
                    permission.setOnShowListener {
                        permission.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try {
                                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        "package:${activity.packageName}".toUri()))
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(activity, R.string.update_settings_unavailable, Toast.LENGTH_LONG).show()
                                } catch (_: SecurityException) {
                                    Toast.makeText(activity, R.string.update_settings_unavailable, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    permission.show()
                    track(permission, "$id/permission")
                }
            }
            UpdatePhase.CONFIRMATION -> {
                clearDialog()
                // Claim before starting an Activity: a second collector/resume cannot
                // launch the same callback again. The receiver itself never starts UI.
                val confirmation = updater.takeConfirmation(id)
                if (confirmation != null) launchInstaller(id, confirmation)
            }
            UpdatePhase.INSTALLING -> {
                if (!launchedInstallerThisResume && dialogKey != "$id/installing") {
                    clearDialog()
                    val waiting = activity.getAlertDialogBuilder()
                        .setTitle(R.string.update_finish_install_title)
                        .setMessage(R.string.update_finish_install_message)
                        .setPositiveButton(R.string.update_continue_install, null)
                        .setNegativeButton(android.R.string.cancel) { _, _ -> updater.cancel(id) }
                        .setOnCancelListener { updater.cancel(id) }.create()
                    waiting.setOnShowListener {
                        waiting.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            updater.confirmationForRetry(id)?.let { launchInstaller(id, it) }
                                ?: updater.confirmationLaunchFailed(id)
                        }
                    }
                    waiting.show()
                    track(waiting, "$id/installing")
                }
            }
            UpdatePhase.FAILED -> if (dialogKey != "$id/failure") {
                clearDialog()
                val message = when (state.failure) {
                    UpdateFailure.INVALID_CALLBACK -> R.string.update_invalid_callback
                    UpdateFailure.CALLBACK_TIMEOUT -> R.string.update_installer_timeout
                    else -> R.string.update_failed_message
                }
                val failure = UpdateDownloadUi.showFailure(activity,
                    onRetry = { updater.retry(id) },
                    onBrowser = { openBrowser(attempt.request.url).also { if (it) updater.dismiss(id) } },
                    onCancel = { updater.dismiss(id) }, messageRes = message)
                track(failure, "$id/failure")
            }
            UpdatePhase.CANCELLED, UpdatePhase.COMPLETE -> {
                clearDialog()
                updater.dismiss(id)
            }
            UpdatePhase.IDLE -> clearDialog()
        }
    }

    private fun launchInstaller(id: String, intent: Intent) {
        launchedInstallerThisResume = true
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            launchedInstallerThisResume = false
            updater.confirmationLaunchFailed(id)
        } catch (_: SecurityException) {
            launchedInstallerThisResume = false
            updater.confirmationLaunchFailed(id)
        }
    }

    private fun openBrowser(url: String): Boolean = try {
        val uri = url.toUri()
        if (uri.scheme != "https" && uri.scheme != "http") throw ActivityNotFoundException()
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(activity, R.string.no_browser_found, Toast.LENGTH_LONG).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(activity, R.string.no_browser_found, Toast.LENGTH_LONG).show()
        false
    }

    private fun track(value: AlertDialog, key: String) {
        dialog = value
        dialogKey = key
        val bounds = UpdateDialogWindow.attach(value)
        windowBinding = bounds
        value.setOnDismissListener {
            bounds.dispose()
            if (dialog === value) {
                dialog = null
                dialogKey = null
                windowBinding = null
                downloadBinding = null
            }
        }
    }

    private fun clearDialog() {
        val old = dialog
        dialog = null
        dialogKey = null
        downloadBinding = null
        windowBinding?.dispose()
        windowBinding = null
        old?.setOnDismissListener(null)
        old?.dismiss()
    }

    override fun onPause(owner: LifecycleOwner) {
        clearDialog()
        launchedInstallerThisResume = false
    }

    override fun onDestroy(owner: LifecycleOwner) {
        clearDialog()
        if (activity.isFinishing) updater.cancelHost(host)
        activity.lifecycle.removeObserver(this)
    }
}

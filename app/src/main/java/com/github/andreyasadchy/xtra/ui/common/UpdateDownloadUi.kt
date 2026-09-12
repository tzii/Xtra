package com.github.andreyasadchy.xtra.ui.common

import android.content.Context
import android.text.format.Formatter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogUpdateDownloadBinding
import com.github.andreyasadchy.xtra.util.UpdateUtils
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import java.text.NumberFormat

object UpdateDownloadUi {
    fun bind(binding: DialogUpdateDownloadBinding, bytesRead: Long, size: Long?) {
        ViewCompat.setAccessibilityHeading(binding.downloadTitle, true)
        val context = binding.root.context
        val bytes = bytesRead.coerceAtLeast(0)
        val percent = UpdateUtils.downloadProgressPercent(bytes, size)
        binding.progressPercent.text = percent?.let { NumberFormat.getPercentInstance().format(it / 100.0) } ?: "—"
        binding.textView.text = when {
            percent != null && size != null -> context.getString(R.string.update_bytes,
                Formatter.formatFileSize(context, bytes), Formatter.formatFileSize(context, size))
            bytes > 0 -> context.getString(R.string.update_bytes_unknown, Formatter.formatFileSize(context, bytes))
            else -> context.getString(R.string.update_preparing)
        }
        if (percent == null) {
            binding.progressBar.isIndeterminate = true
        } else {
            // Material's progress animation respects the system animator duration setting.
            binding.progressBar.setProgressCompat(percent, true)
        }
    }

    fun showFailure(
        context: Context,
        onRetry: () -> Unit,
        onBrowser: () -> Boolean,
        onCancel: () -> Unit = {},
        messageRes: Int = R.string.update_failed_message,
    ): AlertDialog {
        val dialog = context.getAlertDialogBuilder().setTitle(R.string.update_failed_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.update_retry, null)
            .setNeutralButton(R.string.update_browser, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }.create()
        dialog.show()
        // The buttons exist after show(). Bind synchronously so even an immediate
        // click uses our recovery behavior before Dialog's queued on-show message.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onRetry(); dialog.dismiss() }
        // A missing/blocked browser must leave Retry and Cancel reachable.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { if (onBrowser()) dialog.dismiss() }
        return dialog
    }
}

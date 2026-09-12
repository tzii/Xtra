package com.github.andreyasadchy.xtra.util.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.github.andreyasadchy.xtra.util.runUpdateAttempt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single in-process update, shared by Main and Settings. No Activity is retained.
 * A stopped/recreated screen renders the current state when it resumes. Process death
 * resets the operation; only an explicit new attempt cleans orphaned self-update sessions.
 */
@Singleton
class UpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloader: UpdateDownloader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val machine = UpdateStateMachine()
    val state: StateFlow<UpdateState> = machine.state
    private val lock = Any()
    private var job: Job? = null
    private var statusTimeout: Job? = null
    private var install: PendingInstall? = null
    private val installer: PackageInstaller get() = context.packageManager.packageInstaller

    private class PendingInstall(val attemptId: String, val key: InstallCallbackKey) {
        var callback: PendingIntent? = null
        var confirmation: Intent? = null
    }

    fun start(request: UpdateRequest) {
        synchronized(lock) {
            val attempt = machine.begin(request) ?: return
            job?.cancel()
            statusTimeout?.cancel()
            releaseLater(install, abandon = true)
            install = null
            // Assign before dispatch, even when a fake/fast transport completes immediately.
            job = scope.launch(start = CoroutineStart.LAZY) { prepare(attempt) }.also { it.start() }
        }
    }

    fun retry(id: String) {
        synchronized(lock) {
            val current = state.value
            if (current.attempt?.id != id || current.phase != UpdatePhase.FAILED) return
            start(current.attempt.request)
        }
    }

    fun cancel(id: String) {
        synchronized(lock) {
            // Invalidate first: a delayed network callback/worker cannot commit after this.
            if (!machine.cancel(id)) return
            job?.cancel()
            statusTimeout?.cancel()
            releaseLater(install, abandon = true)
            install = null
        }
    }

    fun cancelHost(host: UpdateHost) {
        synchronized(lock) {
            state.value.attempt?.takeIf { it.request.host == host }?.let {
                cancel(it.id)
                machine.dismiss(it.id)
            }
        }
    }

    fun dismiss(id: String) {
        synchronized(lock) { machine.dismiss(id) }
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

    /** Called only by a resumed UI after the per-source install permission is granted. */
    fun commit(id: String) {
        synchronized(lock) {
            if (!canInstall() || !machine.claimCommit(id)) return
            val pending = install?.takeIf { it.attemptId == id }
            if (pending == null) {
                fail(id, UpdateFailure.INSTALLER)
                return
            }
            job = scope.launch(start = CoroutineStart.LAZY) {
                val succeeded = runUpdateAttempt {
                    installer.openSession(pending.key.sessionId).use { session ->
                        currentCoroutineContext().ensureActive()
                        synchronized(lock) {
                            if (install !== pending || state.value.phase != UpdatePhase.COMMITTING) {
                                throw CancellationException("Update was superseded before commit")
                            }
                            val token = UpdateInstallIntents.callback(context, pending.key)
                            pending.callback = token
                            // Commit and cancellation are serialized. A completed OS install
                            // cannot be recalled, but cancelled attempts cannot start a new one.
                            session.commit(token.intentSender)
                        }
                    }
                }
                synchronized(lock) {
                    if (!succeeded) fail(id, UpdateFailure.INSTALLER)
                    else if (state.value.attempt?.id == id && state.value.phase == UpdatePhase.COMMITTING) {
                        statusTimeout?.cancel()
                        statusTimeout = scope.launch {
                            delay(60_000)
                            synchronized(lock) {
                                if (state.value.attempt?.id == id && state.value.phase == UpdatePhase.COMMITTING) {
                                    fail(id, UpdateFailure.CALLBACK_TIMEOUT)
                                }
                            }
                        }
                    }
                }
            }.also { it.start() }
        }
    }

    /** The receiver never starts UI. Wrong-session, cancelled and old-process callbacks are ignored. */
    fun onInstallStatus(intent: Intent) {
        synchronized(lock) {
            val pending = install ?: return
            if (state.value.attempt?.id != pending.attemptId || state.value.phase !in setOf(
                    UpdatePhase.COMMITTING, UpdatePhase.CONFIRMATION, UpdatePhase.INSTALLING,
                )) return
            try {
                if (!UpdateInstallIntents.matches(context, intent, pending.key)) return
                val reportedPackage = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                if (reportedPackage != null && reportedPackage != context.packageName) return
                if (!intent.hasExtra(PackageInstaller.EXTRA_STATUS)) {
                    fail(pending.attemptId, UpdateFailure.INVALID_CALLBACK)
                    return
                }
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        // A duplicate callback must not re-open an already launched installer.
                        if (state.value.phase != UpdatePhase.COMMITTING) return
                        val confirmation = UpdateInstallIntents.confirmation(context, intent, pending.key.sessionId)
                        if (confirmation == null) fail(pending.attemptId, UpdateFailure.INVALID_CALLBACK)
                        else {
                            pending.confirmation = confirmation
                            machine.confirmation(pending.attemptId)
                            statusTimeout?.cancel()
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        if (machine.complete(pending.attemptId)) {
                            statusTimeout?.cancel()
                            install = null
                            releaseLater(pending, abandon = false)
                        }
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> cancel(pending.attemptId)
                    else -> fail(pending.attemptId, UpdateFailure.INSTALLER)
                }
            } catch (_: RuntimeException) {
                // Malformed Parcelable/type data or an unavailable system resolver is a
                // recoverable install error, never an exported nested-intent trampoline.
                fail(pending.attemptId, UpdateFailure.INVALID_CALLBACK)
            }
        }
    }

    /** Returns a fresh, sanitized Intent exactly once for automatic foreground handoff. */
    fun takeConfirmation(id: String): Intent? = synchronized(lock) {
        val pending = install?.takeIf { it.attemptId == id } ?: return@synchronized null
        val intent = pending.confirmation ?: return@synchronized null
        if (!machine.claimConfirmation(id)) return@synchronized null
        Intent(intent)
    }

    /** An explicit Continue tap may reopen a dismissed system prompt; resume alone may not. */
    fun confirmationForRetry(id: String): Intent? = synchronized(lock) {
        val pending = install?.takeIf { it.attemptId == id } ?: return@synchronized null
        if (state.value.phase != UpdatePhase.INSTALLING) return@synchronized null
        pending.confirmation?.let(::Intent)
    }

    fun confirmationLaunchFailed(id: String) {
        synchronized(lock) { fail(id, UpdateFailure.INSTALLER) }
    }

    private suspend fun prepare(attempt: UpdateAttempt) {
        var prepared: PendingInstall? = null
        try {
            val succeeded = runUpdateAttempt {
                // A process-death restart does not implicitly resume an old install. New
                // work first abandons only this application's orphaned self-update sessions.
                synchronized(lock) {
                    requireCurrent(attempt.id, UpdatePhase.DOWNLOADING)
                    for (session in installer.mySessions) {
                        if (session.appPackageName == context.packageName) {
                            try { installer.abandonSession(session.sessionId) } catch (_: Exception) { }
                        }
                    }
                }
                val bytes = downloader.download(attempt.request) { received, total ->
                    machine.progress(attempt.id, received, total)
                }
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    if (!machine.preparing(attempt.id)) throw CancellationException("Update was superseded")
                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                        setAppPackageName(context.packageName)
                        setSize(bytes.size.toLong())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                        }
                    }
                    prepared = PendingInstall(attempt.id, InstallCallbackKey(
                        installer.createSession(params), UUID.randomUUID().toString(),
                    )).also { install = it }
                }
                val pending = checkNotNull(prepared)
                installer.openSession(pending.key.sessionId).use { session ->
                    session.openWrite("package", 0, bytes.size.toLong()).use { output ->
                        var offset = 0
                        while (offset < bytes.size) {
                            currentCoroutineContext().ensureActive()
                            val length = minOf(DEFAULT_BUFFER_SIZE, bytes.size - offset)
                            output.write(bytes, offset, length)
                            offset += length
                        }
                        session.fsync(output)
                    }
                }
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    if (!machine.ready(attempt.id, pending.key.sessionId)) {
                        throw CancellationException("Update was superseded before installation")
                    }
                }
            }
            if (!succeeded) synchronized(lock) {
                fail(attempt.id, if (state.value.phase == UpdatePhase.DOWNLOADING) UpdateFailure.DOWNLOAD else UpdateFailure.INSTALLER)
            }
        } finally {
            synchronized(lock) {
                // A cancelled worker may finish after a retry. Never clear the new job or
                // release the new session; cleanup is tied to the captured object/attempt.
                if (state.value.attempt?.id != attempt.id || !state.value.isActive) {
                    releaseLater(prepared, abandon = true)
                }
            }
        }
    }

    private fun requireCurrent(id: String, phase: UpdatePhase) {
        if (state.value.attempt?.id != id || state.value.phase != phase) {
            throw CancellationException("Update was superseded")
        }
    }

    private fun fail(id: String, reason: UpdateFailure) {
        if (!machine.fail(id, reason)) return
        statusTimeout?.cancel()
        releaseLater(install, abandon = true)
        install = null
    }

    private fun releaseLater(pending: PendingInstall?, abandon: Boolean) {
        if (pending == null) return
        // Revoke the capability synchronously; resource cleanup can use the IO dispatcher.
        pending.callback?.cancel()
        if (abandon) scope.launch {
            try { installer.abandonSession(pending.key.sessionId) } catch (_: Exception) { }
        }
    }
}

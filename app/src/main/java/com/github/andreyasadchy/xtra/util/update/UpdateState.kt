package com.github.andreyasadchy.xtra.util.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** A Settings activity, not an individual Settings fragment, owns the update UI. */
enum class UpdateHost { MAIN, SETTINGS }

data class UpdateRequest(val url: String, val networkLibrary: String?, val host: UpdateHost)
data class UpdateAttempt(val id: String, val request: UpdateRequest)

enum class UpdatePhase {
    IDLE, DOWNLOADING, PREPARING, READY, COMMITTING, CONFIRMATION, INSTALLING,
    FAILED, CANCELLED, COMPLETE,
}

enum class UpdateFailure { DOWNLOAD, INSTALLER, INVALID_CALLBACK, CALLBACK_TIMEOUT }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val attempt: UpdateAttempt? = null,
    val bytesRead: Long = 0,
    val totalBytes: Long? = null,
    val sessionId: Int? = null,
    val failure: UpdateFailure? = null,
) {
    val isActive: Boolean get() = phase in setOf(
        UpdatePhase.DOWNLOADING, UpdatePhase.PREPARING, UpdatePhase.READY,
        UpdatePhase.COMMITTING, UpdatePhase.CONFIRMATION, UpdatePhase.INSTALLING,
    )
}

/**
 * One current attempt, with retained state rather than replayed one-shot close events.
 * The lock linearizes cancellation, retry and callback claims across worker/UI threads.
 * This is in-process state: it deliberately does not promise process-death resumption.
 */
internal class UpdateStateMachine(private val nextId: () -> String = { UUID.randomUUID().toString() }) {
    private val mutableState = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    /** An already running operation is adopted by the requesting screen, not duplicated. */
    @Synchronized
    fun begin(request: UpdateRequest): UpdateAttempt? {
        val previous = mutableState.value
        if (previous.isActive) {
            val attempt = checkNotNull(previous.attempt)
            mutableState.value = previous.copy(attempt = attempt.copy(
                request = attempt.request.copy(host = request.host),
            ))
            return null
        }
        val attempt = UpdateAttempt(nextId(), request)
        mutableState.value = UpdateState(UpdatePhase.DOWNLOADING, attempt)
        return attempt
    }

    @Synchronized
    fun progress(id: String, bytesRead: Long, totalBytes: Long?) {
        val current = mutableState.value
        if (!current.matches(id, UpdatePhase.DOWNLOADING)) return
        val received = maxOf(current.bytesRead, bytesRead.coerceAtLeast(0))
        mutableState.value = current.copy(
            bytesRead = received,
            totalBytes = totalBytes?.takeIf { it > 0 && it >= received },
        )
    }

    @Synchronized
    fun preparing(id: String): Boolean = transition(id, UpdatePhase.DOWNLOADING, UpdatePhase.PREPARING)

    @Synchronized
    fun ready(id: String, sessionId: Int): Boolean {
        if (sessionId < 0) return false
        val current = mutableState.value
        if (!current.matches(id, UpdatePhase.PREPARING)) return false
        mutableState.value = current.copy(phase = UpdatePhase.READY, sessionId = sessionId)
        return true
    }

    @Synchronized
    fun claimCommit(id: String): Boolean = transition(id, UpdatePhase.READY, UpdatePhase.COMMITTING)

    @Synchronized
    fun confirmation(id: String): Boolean = transition(id, UpdatePhase.COMMITTING, UpdatePhase.CONFIRMATION)

    @Synchronized
    fun claimConfirmation(id: String): Boolean = transition(id, UpdatePhase.CONFIRMATION, UpdatePhase.INSTALLING)

    @Synchronized
    fun complete(id: String): Boolean {
        val current = mutableState.value
        // A package-install callback cannot turn a download/preparation into success.
        if (current.attempt?.id != id || current.phase !in setOf(
                UpdatePhase.COMMITTING, UpdatePhase.CONFIRMATION, UpdatePhase.INSTALLING,
            )) return false
        mutableState.value = current.copy(phase = UpdatePhase.COMPLETE)
        return true
    }

    @Synchronized
    fun fail(id: String, failure: UpdateFailure): Boolean {
        val current = mutableState.value
        if (current.attempt?.id != id || !current.isActive) return false
        mutableState.value = current.copy(phase = UpdatePhase.FAILED, failure = failure)
        return true
    }

    @Synchronized
    fun cancel(id: String): Boolean {
        val current = mutableState.value
        if (current.attempt?.id != id || !current.isActive) return false
        mutableState.value = current.copy(phase = UpdatePhase.CANCELLED)
        return true
    }

    @Synchronized
    fun dismiss(id: String): Boolean {
        val current = mutableState.value
        if (current.attempt?.id != id || current.isActive) return false
        mutableState.value = UpdateState()
        return true
    }

    private fun transition(id: String, from: UpdatePhase, to: UpdatePhase): Boolean {
        val current = mutableState.value
        if (!current.matches(id, from)) return false
        mutableState.value = current.copy(phase = to)
        return true
    }

    private fun UpdateState.matches(id: String, expected: UpdatePhase): Boolean =
        attempt?.id == id && phase == expected
}

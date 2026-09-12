package com.github.andreyasadchy.xtra.util.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Null payloads are real "no update" results; null state means no unconsumed result. */
data class UpdateCheckResult<T>(val requestId: Long, val value: T)

class UpdateCheckMailbox<T> {
    private var requestId = 0L
    private var published = false
    private val pending = MutableStateFlow<UpdateCheckResult<T>?>(null)
    val state: StateFlow<UpdateCheckResult<T>?> = pending.asStateFlow()

    @Synchronized
    fun begin(): Long {
        requestId += 1
        published = false
        pending.value = null
        return requestId
    }

    @Synchronized
    fun publish(id: Long, value: T): Boolean {
        if (id != requestId || published) return false
        published = true
        pending.value = UpdateCheckResult(id, value)
        return true
    }

    @Synchronized
    fun consume(result: UpdateCheckResult<T>): Boolean {
        if (pending.value != result) return false
        pending.value = null
        return true
    }
}

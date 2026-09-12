package com.github.andreyasadchy.xtra.util.update

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** These cases run unchanged in JUnit and in the SDK-independent Kotlin runner. */
internal data class UpdatePolicyCase(val name: String, val verify: () -> Unit) {
    override fun toString(): String = name
}

internal object UpdatePolicyCases {
    private val request = UpdateRequest("https://example.test/update.apk", "OkHttp", UpdateHost.MAIN)
    private fun machine(): UpdateStateMachine {
        var id = 0
        return UpdateStateMachine { "attempt-${++id}" }
    }
    private fun started(): Pair<UpdateStateMachine, String> = machine().let { it to it.begin(request)!!.id }
    private fun ready(): Pair<UpdateStateMachine, String> = started().also { (m, id) ->
        check(m.preparing(id)); check(m.ready(id, 17))
    }
    private inline fun <reified T : Throwable> fails(block: () -> Unit) {
        try { block() } catch (error: Throwable) {
            check(error is T) { "Expected ${T::class.java.simpleName}, got $error" }
            return
        }
        error("Expected ${T::class.java.simpleName}")
    }
    private fun contenders(count: Int = 32, action: () -> Boolean): Int {
        val ready = CountDownLatch(count)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(count)
        try {
            val results = (1..count).map { pool.submit<Boolean> {
                ready.countDown(); check(start.await(5, TimeUnit.SECONDS)); action()
            } }
            check(ready.await(5, TimeUnit.SECONDS)); start.countDown()
            return results.count { it.get(5, TimeUnit.SECONDS) }
        } finally { start.countDown(); pool.shutdownNow() }
    }
    private fun body(length: Long?, input: InputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
        active: () -> Unit = {}, progress: (Long, Long?) -> Unit = { _, _ -> }): ByteArray =
        readUpdateBody(input, length, active, progress)

    val all: List<UpdatePolicyCase> = buildList {
        fun case(name: String, verify: () -> Unit) { add(UpdatePolicyCase(name, verify)) }
        case("initial state is idle with no stale attempt") { check(machine().state.value == UpdateState()) }
        case("begin resets previous bytes session and failure") {
            val (m, id) = ready(); m.progress(id, 9, 10); m.fail(id, UpdateFailure.INSTALLER)
            val next = m.begin(request)!!
            check(next.id != id); check(m.state.value == UpdateState(UpdatePhase.DOWNLOADING, next))
        }
        case("active operation is adopted without replacing URL or transport") {
            val (m, id) = started(); m.progress(id, 12, 20)
            check(m.begin(request.copy(url = "https://other.test", networkLibrary = "Cronet", host = UpdateHost.SETTINGS)) == null)
            check(m.state.value.attempt == UpdateAttempt(id, request.copy(host = UpdateHost.SETTINGS)))
            check(m.state.value.bytesRead == 12L)
        }
        case("progress is monotonic and clamps negative values") {
            val (m, id) = started(); m.progress(id, -4, 100); check(m.state.value.bytesRead == 0L)
            m.progress(id, 50, 100); m.progress(id, 4, 100); check(m.state.value.bytesRead == 50L)
        }
        for (total in listOf<Long?>(null, -1, 0, 2)) case("unknown or inconsistent progress length $total is indeterminate") {
            val (m, id) = started(); m.progress(id, 3, total); check(m.state.value.totalBytes == null)
        }
        case("valid progress length remains available") {
            val (m, id) = started(); m.progress(id, 5, 20); check(m.state.value.totalBytes == 20L)
        }
        case("late progress does not rewind preparation") {
            val (m, id) = started(); m.progress(id, 5, 5); m.preparing(id)
            m.progress(id, 999, 1000); check(m.state.value.bytesRead == 5L)
        }
        case("out-of-order success and claims are rejected") {
            val (m, id) = started()
            check(!m.ready(id, 17)); check(!m.claimCommit(id)); check(!m.confirmation(id))
            check(!m.claimConfirmation(id)); check(!m.complete(id)); check(!m.dismiss(id))
        }
        case("invalid session never makes preparation ready") {
            val (m, id) = started(); m.preparing(id); check(!m.ready(id, -1))
            check(m.state.value.phase == UpdatePhase.PREPARING)
        }
        case("normal install sequence completes exactly once") {
            val (m, id) = ready(); check(m.claimCommit(id)); check(m.confirmation(id))
            check(m.claimConfirmation(id)); check(m.complete(id)); check(!m.complete(id))
            check(!m.cancel(id)); check(!m.fail(id, UpdateFailure.DOWNLOAD)); check(m.dismiss(id))
            check(m.state.value == UpdateState())
        }
        case("OS success without a confirmation callback is accepted after commit") {
            val (m, id) = ready(); check(m.claimCommit(id)); check(m.complete(id))
        }
        case("concurrent collectors can claim commit only once") {
            val (m, id) = ready(); check(contenders { m.claimCommit(id) } == 1)
        }
        case("concurrent collectors can claim confirmation only once") {
            val (m, id) = ready(); m.claimCommit(id); m.confirmation(id)
            check(contenders { m.claimConfirmation(id) } == 1)
        }
        case("concurrent terminal dismissal has one winner") {
            val (m, id) = started(); m.cancel(id); check(contenders { m.dismiss(id) } == 1)
        }
        for (phase in listOf(UpdatePhase.DOWNLOADING, UpdatePhase.PREPARING, UpdatePhase.READY,
            UpdatePhase.COMMITTING, UpdatePhase.CONFIRMATION, UpdatePhase.INSTALLING)) {
            case("cancellation invalidates all future writes in $phase") {
                val (m, id) = started()
                if (phase != UpdatePhase.DOWNLOADING) m.preparing(id)
                if (phase.ordinal >= UpdatePhase.READY.ordinal) m.ready(id, 17)
                if (phase.ordinal >= UpdatePhase.COMMITTING.ordinal) m.claimCommit(id)
                if (phase.ordinal >= UpdatePhase.CONFIRMATION.ordinal) m.confirmation(id)
                if (phase == UpdatePhase.INSTALLING) m.claimConfirmation(id)
                check(m.state.value.phase == phase); check(m.cancel(id))
                val cancelled = m.state.value
                m.progress(id, 99, 100); check(!m.preparing(id)); check(!m.ready(id, 18))
                check(!m.claimCommit(id)); check(!m.confirmation(id)); check(!m.claimConfirmation(id))
                check(!m.complete(id)); check(!m.fail(id, UpdateFailure.DOWNLOAD))
                check(m.state.value == cancelled)
            }
        }
        case("a thousand retry generations reject every previous worker") {
            val m = machine(); var old = m.begin(request)!!.id
            repeat(1000) {
                check(m.fail(old, UpdateFailure.DOWNLOAD)); val id = m.begin(request)!!.id
                val before = m.state.value
                m.progress(old, Long.MAX_VALUE, Long.MAX_VALUE); check(!m.preparing(old))
                check(!m.ready(old, 1)); check(!m.claimCommit(old)); check(!m.confirmation(old))
                check(!m.claimConfirmation(old)); check(!m.cancel(old)); check(!m.complete(old))
                check(!m.fail(old, UpdateFailure.INSTALLER)); check(!m.dismiss(old))
                check(m.state.value == before); old = id
            }
        }
        case("failure emitted with zero subscribers survives background and retry") { runBlocking {
            val (m, id) = started(); val seen = mutableListOf<UpdatePhase>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { m.state.collect { seen += it.phase } }
            collector.cancelAndJoin(); check(m.fail(id, UpdateFailure.DOWNLOAD))
            check(m.state.first().phase == UpdatePhase.FAILED)
            check(seen == listOf(UpdatePhase.DOWNLOADING)); val next = m.begin(request)!!
            check(m.state.first().attempt?.id == next.id); check(m.state.first().failure == null)
        } }
        case("unobserved confirmation survives stopped UI") { runBlocking {
            val (m, id) = ready(); m.claimCommit(id); m.confirmation(id)
            check(m.state.first().phase == UpdatePhase.CONFIRMATION)
            check(m.claimConfirmation(id)); check(!m.claimConfirmation(id))
        } }
        case("body copies known content and reports measured bytes") {
            val seen = mutableListOf<Pair<Long, Long?>>()
            check(body(3, progress = { bytes, size -> seen += bytes to size }).contentEquals(byteArrayOf(1, 2, 3)))
            check(seen.last() == 3L to 3L)
        }
        for (length in listOf<Long?>(null, -1)) case("body accepts unknown length $length without invented total") {
            val totals = mutableListOf<Long?>(); check(body(length, progress = { _, size -> totals += size }).size == 3)
            check(totals.all { it == null })
        }
        case("truncated body is rejected") { fails<EOFException> { body(4) } }
        case("body longer than declared is rejected") { fails<EOFException> { body(2) } }
        case("huge server length is not used as allocation size") { fails<EOFException> { body(Long.MAX_VALUE) } }
        case("empty response with unknown length is rejected") { fails<IllegalStateException> { body(null, ByteArrayInputStream(byteArrayOf())) } }
        case("empty response with zero length is rejected") { fails<IllegalStateException> { body(0, ByteArrayInputStream(byteArrayOf())) } }
        case("cancellation before read does not touch stream") {
            var touched = false
            val input = object : InputStream() { override fun read(): Int { touched = true; return -1 } }
            fails<CancellationException> { body(null, input, active = { throw CancellationException() }) }
            check(!touched)
        }
        case("cancellation during read prevents bytes being accepted") {
            var cancelled = false; var reported = false
            val input = object : InputStream() {
                override fun read(): Int = error("bulk read expected")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    cancelled = true; buffer[offset] = 9; return 1
                }
            }
            fails<CancellationException> { body(null, input,
                active = { if (cancelled) throw CancellationException() }, progress = { _, _ -> reported = true }) }
            check(!reported)
        }
        case("cancellation between chunks stops subsequent reads") {
            var cancelled = false; var reported = 0
            fails<CancellationException> { body(null, ByteArrayInputStream(ByteArray(50_000)),
                active = { if (cancelled) throw CancellationException() }, progress = { _, _ -> reported++; cancelled = true }) }
            check(reported == 1)
        }
        case("body reader does not close caller-owned stream") {
            var closed = false
            val input = object : ByteArrayInputStream(byteArrayOf(1)) { override fun close() { closed = true; super.close() } }
            check(body(1, input).size == 1); check(!closed)
        }
        case("large body progress is monotonic and reaches exact size") {
            val expected = ByteArray(256_019) { (it % 251).toByte() }; val progress = mutableListOf<Long>()
            check(body(expected.size.toLong(), ByteArrayInputStream(expected), progress = { value, _ -> progress += value }).contentEquals(expected))
            check(progress.zipWithNext().all { (a, b) -> b > a }); check(progress.last() == expected.size.toLong())
        }
        val key = InstallCallbackKey(17, "unguessable-attempt-nonce")
        case("callback requires matching URI session nonce and reported session") { check(InstallCallbackPolicy.matches(key, 17, key.nonce, 17)) }
        for ((session, nonce, reported) in listOf(Triple(18, key.nonce, 17), Triple(17, "wrong", 17),
            Triple(17, null, 17), Triple(17, key.nonce, 18), Triple(17, key.nonce, -1))) {
            case("callback rejects mismatch $session $nonce $reported") { check(!InstallCallbackPolicy.matches(key, session, nonce, reported)) }
        }
        case("empty callback nonce never authenticates") { check(!InstallCallbackPolicy.matches(InstallCallbackKey(17, ""), 17, "", 17)) }
        case("negative callback session never authenticates") { check(!InstallCallbackPolicy.matches(InstallCallbackKey(-1, "x"), -1, "x", -1)) }
        case("system confirmation requires the expected action and session") {
            check(InstallCallbackPolicy.allowsConfirmation(17, InstallCallbackPolicy.CONFIRM_INSTALL_ACTION, 17, true, true, true))
        }
        case("legacy Android confirmation action remains supported") {
            check(InstallCallbackPolicy.allowsConfirmation(17, InstallCallbackPolicy.CONFIRM_PERMISSIONS_ACTION, 17, true, true, true))
        }
        for (flag in 0..5) case("confirmation rejects invalid boundary field $flag") {
            check(!InstallCallbackPolicy.allowsConfirmation(if (flag == 0) -1 else 17,
                if (flag == 1) "android.intent.action.VIEW" else InstallCallbackPolicy.CONFIRM_INSTALL_ACTION,
                if (flag == 2) 18 else 17, flag != 3, flag != 4, flag != 5))
        }
        case("compact window is bottom aligned with insets") { check(updateWindowBounds(360, 800, 1f) == UpdateWindowBounds(328, 768, false)) }
        case("wide window is centered and capped") { check(updateWindowBounds(900, 400, 1f) == UpdateWindowBounds(560, 368, true)) }
        case("density affects dp breakpoint and pixel dimensions") { check(updateWindowBounds(1080, 1920, 3f) == UpdateWindowBounds(984, 1824, false)) }
        case("zero and tiny windows never produce negative dimensions") {
            for (width in -1..40) for (height in -1..40) {
                val b = updateWindowBounds(width, height, 1f)
                check(b.width in 1..width.coerceAtLeast(1)); check(b.bodyMaxHeight in 1..height.coerceAtLeast(1))
            }
        }
        for (density in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) case("invalid density $density uses safe fallback") {
            check(updateWindowBounds(360, 800, density) == updateWindowBounds(360, 800, 1f))
        }
        case("same dialog policy follows compact wide compact changes") {
            val sequence = listOf(360 to 800, 900 to 300, 320 to 700).map { (w, h) -> updateWindowBounds(w, h, 1f) }
            check(sequence.map { it.width } == listOf(328, 560, 288)); check(sequence.map { it.centered } == listOf(false, true, false))
        }
        case("check mailbox has no synthetic initial no-update event") { check(UpdateCheckMailbox<String?>().state.value == null) }
        case("null result is retained and consumed once") {
            val box = UpdateCheckMailbox<String?>(); val id = box.begin(); check(box.publish(id, null))
            val result = checkNotNull(box.state.value); check(result.value == null); check(box.consume(result))
            check(!box.consume(result)); check(box.state.value == null)
        }
        case("check result survives absence of subscribers") { runBlocking {
            val box = UpdateCheckMailbox<String>(); val id = box.begin(); box.publish(id, "1.3")
            check(box.state.first() == UpdateCheckResult(id, "1.3"))
        } }
        case("new check invalidates pending old result and late old producer") {
            val box = UpdateCheckMailbox<String>(); val old = box.begin(); box.publish(old, "old")
            val result = box.state.value!!; val next = box.begin(); check(box.state.value == null)
            check(!box.consume(result)); check(!box.publish(old, "late")); check(box.publish(next, "current"))
        }
        case("consumed check cannot be published a second time") {
            val box = UpdateCheckMailbox<String>(); val id = box.begin(); check(box.publish(id, "x"))
            check(box.consume(box.state.value!!)); check(!box.publish(id, "x")); check(box.state.value == null)
        }
        case("concurrent check consumers have exactly one winner") {
            val box = UpdateCheckMailbox<String>(); val id = box.begin(); box.publish(id, "x")
            val result = box.state.value!!; check(contenders { box.consume(result) } == 1)
        }
    }
}

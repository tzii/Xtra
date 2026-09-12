package com.github.andreyasadchy.xtra.util.update

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

/** The caller owns the stream and cancels its network request to unblock a pending read. */
internal fun readUpdateBody(
    input: InputStream,
    contentLength: Long?,
    ensureActive: () -> Unit,
    onProgress: (Long, Long?) -> Unit,
): ByteArray {
    val expected = contentLength?.takeIf { it >= 0 }
    // Do not preallocate a server-controlled Content-Length. Network backends still
    // buffer the final APK; streaming to disk is deliberately outside this fix.
    return ByteArrayOutputStream(32 * 1024).use { output ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var received = 0L
        while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count == -1) break
            if (count == 0) continue
            ensureActive()
            output.write(buffer, 0, count)
            received += count
            onProgress(received, expected?.takeIf { it > 0 })
        }
        ensureActive()
        if (expected != null && received != expected) {
            throw EOFException("The update response length did not match Content-Length")
        }
        check(received > 0) { "Empty update response" }
        output.toByteArray()
    }
}

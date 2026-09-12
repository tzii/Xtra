package com.github.andreyasadchy.xtra.util.update

import android.net.http.HttpEngine
import android.net.http.UrlResponseInfo as PlatformResponseInfo
import android.os.Build
import android.os.ext.SdkExtensions
import com.github.andreyasadchy.xtra.util.HttpEngineUtils
import com.github.andreyasadchy.xtra.util.getByteArrayCronetCallback
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.chromium.net.CronetEngine
import org.chromium.net.UrlResponseInfo as CronetResponseInfo
import java.io.IOException
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shared transport for all updater entry points; no Activity or View references. */
@Singleton
class UpdateDownloader @Inject constructor(
    private val httpEngine: Lazy<HttpEngine>?,
    private val cronetEngine: Lazy<CronetEngine>?,
    private val cronetExecutor: ExecutorService,
    private val okHttpClient: OkHttpClient,
) {
    suspend fun download(request: UpdateRequest, onProgress: (Long, Long?) -> Unit): ByteArray {
        currentCoroutineContext().ensureActive()
        // Consistent HTTP(S) validation for every backend, including customized feeds.
        val url = request.url.toHttpUrl().toString()
        return when {
            request.networkLibrary == "HttpEngine" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7 && httpEngine != null -> {
                val response = suspendCancellableCoroutine<Pair<PlatformResponseInfo, ByteArray>> { continuation ->
                    val call = httpEngine.get().newUrlRequestBuilder(
                        url, cronetExecutor, HttpEngineUtils.byteArrayUrlCallback(continuation),
                    ).build()
                    if (continuation.isActive) {
                        call.start()
                        // Register after start: a cancellation between the two calls is
                        // delivered immediately, rather than cancelling an unstarted call.
                        continuation.invokeOnCancellation { call.cancel() }
                    }
                }
                currentCoroutineContext().ensureActive()
                checkedBody(response.first.httpStatusCode, response.first.headers.asMap, response.second, onProgress)
            }
            request.networkLibrary == "Cronet" && cronetEngine != null -> {
                // Use the existing callback on every API instead of blocking on future.get().
                val response = suspendCancellableCoroutine<Pair<CronetResponseInfo, ByteArray>> { continuation ->
                    val call = cronetEngine.get().newUrlRequestBuilder(
                        url, getByteArrayCronetCallback(continuation), cronetExecutor,
                    ).build()
                    if (continuation.isActive) {
                        call.start()
                        continuation.invokeOnCancellation { call.cancel() }
                    }
                }
                currentCoroutineContext().ensureActive()
                checkedBody(response.first.httpStatusCode, response.first.allHeaders, response.second, onProgress)
            }
            else -> downloadOkHttp(url, onProgress)
        }
    }

    private suspend fun downloadOkHttp(url: String, onProgress: (Long, Long?) -> Unit): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val call = okHttpClient.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { call.cancel() }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (!it.isSuccessful) throw IOException("Unsuccessful update response")
                            readUpdateBody(
                                input = it.body.byteStream(),
                                contentLength = it.body.contentLength().takeIf { size -> size >= 0 },
                                ensureActive = {
                                    if (!continuation.isActive) throw CancellationException("Update cancelled")
                                },
                                onProgress = onProgress,
                            )
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }

    private fun checkedBody(
        status: Int,
        headers: Map<String, List<String>>,
        bytes: ByteArray,
        onProgress: (Long, Long?) -> Unit,
    ): ByteArray {
        if (status !in 200..299 || bytes.isEmpty()) throw IOException("Empty or unsuccessful update response")
        val size = headers.entries.firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
            ?.value?.singleOrNull()?.toLongOrNull()?.takeIf { it > 0 }
        // Cronet/HttpEngine can transparently decode content. Their wire length is
        // not necessarily the decoded byte count; only use it when it agrees.
        onProgress(bytes.size.toLong(), size?.takeIf { it == bytes.size.toLong() })
        return bytes
    }
}

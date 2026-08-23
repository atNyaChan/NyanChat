package me.rerere.rikkahub.data.ai

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import com.github.luben.zstd.ZstdInputStream
import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

class RequestLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        val requestHeaders = request.headers.toMap()
        val requestBody = runCatching {
            request.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            }
        }.getOrElse { "<${errorSummary(it)}>" }

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            Logging.logRequest(
                LogEntry.RequestLog(
                    tag = "HTTP",
                    url = request.url.toString(),
                    method = request.method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    durationMs = System.currentTimeMillis() - startTime,
                    error = errorSummary(e)
                )
            )
            throw e
        }

        val responseHeaders = response.headers.toMap()
        val body = response.body
        return response.newBuilder().body(LoggingResponseBody(body) { responseBytes, error, closedBeforeEof ->
            val responseBody = responseBytes?.let {
                decodeTextBody(it, responseHeaders, body.contentType())
            }
            val isEventStream = body.contentType()?.toString()
                ?.contains("event-stream", ignoreCase = true) == true
            val streamCompleted = !isEventStream || responseBody.hasStreamCompletionMarker()
            val effectiveError = when {
                error != null && (!isBenignStreamCancellation(error) || streamCompleted) ->
                    error.takeUnless { isBenignStreamCancellation(it) && streamCompleted }
                error != null || closedBeforeEof && !streamCompleted ->
                    StreamInterruptedException()
                else -> null
            }
            logCompleted(request.url.toString(), request.method, requestHeaders, requestBody, response, responseHeaders,
                startTime, responseBody, effectiveError)
        }).build()
    }

    private fun logCompleted(
        url: String,
        method: String,
        requestHeaders: Map<String, String>,
        requestBody: String?,
        response: Response,
        responseHeaders: Map<String, String>,
        startTime: Long,
        responseBody: String?,
        error: Throwable?,
    ) = Logging.logRequest(LogEntry.RequestLog(
        tag = "HTTP",
        url = url,
        method = method,
        requestHeaders = requestHeaders,
        requestBody = requestBody,
        responseCode = response.code,
        responseHeaders = responseHeaders,
        responseBody = responseBody,
        durationMs = System.currentTimeMillis() - startTime,
        error = error?.let(::errorSummary),
    ))

    private fun errorSummary(error: Throwable): String =
        (error.message ?: error::class.simpleName ?: "Request failed").lineSequence().first().trim()

    private fun isBenignStreamCancellation(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("stream was reset") &&
            (message.contains("cancel") || message.contains("canceled"))
    }

    private fun String?.hasStreamCompletionMarker(): Boolean {
        if (this == null) return false
        return contains("[DONE]") ||
            contains("response.completed") ||
            contains("message_stop")
    }

    private class StreamInterruptedException : Exception("stream_response_interrupted")

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        return names().associateWith { name ->
            if (name.equals("Proxy-Authorization", ignoreCase = true)) {
                "██"
            } else {
                values(name).joinToString("\n")
            }
        }
    }

    private fun decodeTextBody(
        bytes: ByteArray,
        headers: Map<String, String>,
        mediaType: MediaType?,
    ): String? {
        val contentType = mediaType?.toString().orEmpty().lowercase()
        val isText = contentType.startsWith("text/") || listOf(
            "json", "xml", "javascript", "event-stream", "x-www-form-urlencoded", "graphql"
        ).any(contentType::contains)
        if (!isText) return null

        val encodings = headers.entries.firstOrNull { it.key.equals("Content-Encoding", true) }
            ?.value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val decoded = runCatching {
            encodings.asReversed().fold(bytes) { current, encoding ->
                val input = ByteArrayInputStream(current)
                when (encoding.lowercase()) {
                    "gzip", "x-gzip" -> GZIPInputStream(input).readBytes()
                    "deflate" -> InflaterInputStream(input).readBytes()
                    "br" -> BrotliInputStream(input).readBytes()
                    "zstd" -> ZstdInputStream(input).readBytes()
                    "identity" -> current
                    else -> current
                }
            }
        }.getOrElse { bytes }
        return decoded.toString(mediaType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
    }

    private class LoggingResponseBody(
        private val delegate: ResponseBody,
        private val onComplete: (ByteArray?, Throwable?, Boolean) -> Unit,
    ) : ResponseBody() {
        private val captured = Buffer()
        private var completed = false
        private val loggingSource: BufferedSource by lazy {
            object : ForwardingSource(delegate.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long = try {
                    super.read(sink, byteCount).also { read ->
                        if (read > 0) sink.copyTo(captured, sink.size - read, read)
                        if (read == -1L) complete(error = null, closedBeforeEof = false)
                    }
                } catch (error: Throwable) {
                    complete(error = error, closedBeforeEof = true)
                    throw error
                }

                override fun close() {
                    try {
                        super.close()
                    } finally {
                        complete(error = null, closedBeforeEof = true)
                    }
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = delegate.contentType()
        override fun contentLength(): Long = delegate.contentLength()
        override fun source(): BufferedSource = loggingSource

        private fun complete(error: Throwable?, closedBeforeEof: Boolean) {
            if (completed) return
            completed = true
            onComplete(captured.clone().readByteArray(), error, closedBeforeEof)
        }
    }
}

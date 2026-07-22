package xyz.mdhv.riverwip.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Minimal HTTP GET over [HttpURLConnection] — zero third-party deps, so it is
 * `foss`-clean (no OkHttp/Play). Handles gzip, conditional requests
 * (ETag / Last-Modified → 304), and cross-scheme redirects (which
 * HttpURLConnection won't follow on its own).
 *
 * Everything the user fetches goes to the user's own chosen sources and nowhere
 * else (brief §0). No cookies, no persistent identifiers.
 */
class HttpClient(
    private val userAgent: String = "river/0.1 (+news-omission-reader)",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
    private val maxRedirects: Int = 5,
    private val maxBodyBytes: Int = 5 * 1024 * 1024,
) {
    data class Response(
        val code: Int,
        val body: String,
        val etag: String?,
        val lastModified: String?,
        val contentType: String?,
        val finalUrl: String,
    ) {
        val notModified: Boolean get() = code == HttpURLConnection.HTTP_NOT_MODIFIED
        val isSuccess: Boolean get() = code in 200..299
    }

    suspend fun get(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
    ): Response = withContext(Dispatchers.IO) {
        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty(
                    "Accept",
                    "application/rss+xml, application/atom+xml, application/xml, text/xml, application/json, text/html;q=0.8, */*;q=0.5",
                )
                if (etag != null) setRequestProperty("If-None-Match", etag)
                if (lastModified != null) setRequestProperty("If-Modified-Since", lastModified)
            }
            try {
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308) && redirects < maxRedirects) {
                    val loc = conn.getHeaderField("Location") ?: throw IOException("redirect without Location")
                    current = URL(URL(current), loc).toString()
                    redirects++
                    conn.disconnect()
                    continue
                }
                val ctype = conn.contentType
                val body = if (code == HttpURLConnection.HTTP_NOT_MODIFIED) "" else readBody(conn)
                return@withContext Response(
                    code = code,
                    body = body,
                    etag = conn.getHeaderField("ETag"),
                    lastModified = conn.getHeaderField("Last-Modified"),
                    contentType = ctype,
                    finalUrl = current,
                )
            } finally {
                conn.disconnect()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private fun readBody(conn: HttpURLConnection): String {
        val raw = if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
        raw ?: return ""
        val stream = if (conn.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
            GZIPInputStream(raw)
        } else {
            raw
        }
        return stream.use { input ->
            val buffer = ByteArray(8 * 1024)
            val out = StringBuilder()
            val bytes = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBodyBytes) break
                bytes.write(buffer, 0, n)
            }
            out.append(bytes.toString(Charsets.UTF_8.name()))
            out.toString()
        }
    }
}

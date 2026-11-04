package com.example.librusweekly.librus.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/// In-memory cookie jar. One HttpClient = one student's Synergia session, like the Swift
/// ephemeral URLSession, so cookies of different accounts never mix.
private class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val bucket = store.getOrPut(url.topPrivateDomain() ?: url.host) { mutableMapOf() }
        synchronized(bucket) {
            for (c in cookies) bucket["${c.name}@${c.domain}"] = c
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val bucket = store[url.topPrivateDomain() ?: url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return synchronized(bucket) {
            bucket.values.filter { it.expiresAt > now && it.matches(url) }
        }
    }

    fun all(): List<Cookie> = store.values.flatMap { synchronized(it) { it.values.toList() } }

    fun named(name: String, domain: String): String? =
        all().firstOrNull { it.name == name && it.domain.endsWith(domain) }?.value

    fun clear() { store.clear() }
}

/// Thin OkHttp wrapper matching the Swift HTTPClient surface used by the ported code.
class HttpClient {
    private val jar = InMemoryCookieJar()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(jar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val noRedirects: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    data class HttpResponse(val status: Int, val body: ByteArray, val headers: okhttp3.Headers, val finalUrl: String) {
        val location: String? get() = headers["Location"]
        val text: String by lazy { decodeText(body, headers["Content-Type"]) }
        fun json(): Json = try { Json.parse(text) }
        catch (e: Exception) { throw LibrusError.InvalidResponse("HTTP $status, ${text.take(200)}") }
    }

    suspend fun send(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        form: List<Pair<String, String>>? = null,
        body: ByteArray? = null,
        userAgent: String = LibrusConstants.USER_AGENT,
        followRedirects: Boolean = true
    ): HttpResponse = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url.toHttpUrl())
        reqBuilder.header("User-Agent", userAgent)
        for ((k, v) in headers) reqBuilder.header(k, v)

        val reqBody: RequestBody? = when {
            form != null -> {
                reqBuilder.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                formEncode(form).toRequestBody(null)
            }
            body != null -> body.toRequestBody(null)
            else -> null
        }
        reqBuilder.method(method, reqBody ?: if (method != "GET" && method != "HEAD") ByteArray(0).toRequestBody(null) else null)

        val callClient = if (followRedirects) client else noRedirects
        try {
            callClient.newCall(reqBuilder.build()).execute().use { response: Response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                HttpResponse(response.code, bytes, response.headers, response.request.url.toString())
            }
        } catch (e: LibrusError) {
            throw e
        } catch (e: IOException) {
            throw LibrusError.Network(e.message ?: "I/O error")
        }
    }

    fun cookie(name: String, domain: String): String? = jar.named(name, domain)
    fun clearCookies() = jar.clear()

    companion object {
        /// application/x-www-form-urlencoded with UTF-8 percent-encoding. Spaces → '+'.
        private fun formEncode(pairs: List<Pair<String, String>>): String =
            pairs.joinToString("&") { "${enc(it.first)}=${enc(it.second)}" }

        private fun enc(s: String): String =
            URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%20", "+")

        private fun decodeText(data: ByteArray, contentType: String?): String {
            val ct = contentType?.lowercase().orEmpty()
            val charset = when {
                "iso-8859-2" in ct -> Charset.forName("ISO-8859-2")
                "windows-1250" in ct -> Charset.forName("windows-1250")
                "utf-8" in ct -> Charsets.UTF_8
                else -> Charsets.UTF_8
            }
            return try { String(data, charset) }
            catch (e: Exception) { String(data) }
        }
    }
}

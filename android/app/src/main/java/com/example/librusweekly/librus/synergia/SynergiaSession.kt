package com.example.librusweekly.librus.synergia

import com.example.librusweekly.librus.core.HttpClient
import com.example.librusweekly.librus.core.LibrusConstants
import com.example.librusweekly.librus.core.LibrusError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/// Synergia login + password via the website login form (as used by the maintained librus-apix
/// project), since `api.librus.pl/OAuth/Token` no longer accepts the password grant. The same
/// cookies then give access to the API 2.0 through `synergia.librus.pl/gateway/api/2.0`.
class CredentialSynergiaSession(
    private val login: String,
    private val password: String,
    val http: HttpClient = HttpClient()
) {
    private val mutex = Mutex()
    private var expiryMs: Long = 0

    suspend fun ensureLoggedIn(force: Boolean = false) = mutex.withLock {
        if (!force && expiryMs - System.currentTimeMillis() > 60_000) return@withLock
        performLogin()
    }

    /// Short-lived `oauth_token` cookie needed by the gateway API.
    suspend fun refreshGatewayToken(): Boolean {
        val response = http.send(
            "${LibrusConstants.SYNERGIA_URL}/refreshToken",
            userAgent = LibrusConstants.WEB_USER_AGENT
        )
        return response.status == 200
    }

    private suspend fun performLogin() {
        http.clearCookies()
        val ua = LibrusConstants.WEB_USER_AGENT
        val oauth = LibrusConstants.API_OAUTH_URL

        val start = http.send(
            "${LibrusConstants.SYNERGIA_URL}/loguj/portalRodzina?v=${System.currentTimeMillis() / 1000}",
            userAgent = ua
        )
        if (start.status == 503 || "Przerwa techniczna" in start.text) throw LibrusError.Maintenance

        val response = http.send(
            "$oauth/Authorization?client_id=${LibrusConstants.SYNERGIA_WEB_CLIENT_ID}",
            method = "POST",
            form = listOf("action" to "login", "login" to login, "pass" to password),
            userAgent = ua
        )
        if (response.status == 503) throw LibrusError.Maintenance
        val json = response.json()
        if (json["status"].string == "error") {
            val message = json["errors"].array.firstOrNull()?.get("message")?.string ?: "nieznany błąd"
            if ("nieprawidłowy login" in message.lowercase()) throw LibrusError.InvalidLogin
            throw LibrusError.SynergiaSession(message)
        }

        val finish = http.send(
            "$oauth/Authorization/2FA?client_id=${LibrusConstants.SYNERGIA_WEB_CLIENT_ID}",
            userAgent = ua
        )
        if ("error=" in finish.finalUrl) {
            val reason = if ("access_denied" in finish.finalUrl) "brak dostępu" else "błąd"
            throw LibrusError.SynergiaSession("Librus odrzucił logowanie ($reason)")
        }
        if (!refreshGatewayToken()) {
            throw LibrusError.SynergiaSession("nie udało się uzyskać tokenu API")
        }
        expiryMs = System.currentTimeMillis() + 30 * 60 * 1000L
    }
}

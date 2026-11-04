package com.example.librusweekly.librus.api

import com.example.librusweekly.librus.core.Json
import com.example.librusweekly.librus.core.LibrusConstants
import com.example.librusweekly.librus.core.LibrusError
import com.example.librusweekly.librus.synergia.CredentialSynergiaSession

/// Librus API 2.0 client against the Synergia gateway (`synergia.librus.pl/gateway/api/2.0`).
/// Expired-session retry is handled once; a second failure surfaces as `TokenExpired` or `Api`.
class LibrusApi(private val session: CredentialSynergiaSession) {
    private val baseUrl = LibrusConstants.GATEWAY_API_URL
    private val http = session.http

    suspend fun get(endpoint: String, method: String = "GET"): Json =
        try { request(endpoint, method, forceRefresh = false) }
        catch (e: LibrusError) {
            if (e is LibrusError.TokenExpired) request(endpoint, method, forceRefresh = true) else throw e
        }

    private suspend fun request(endpoint: String, method: String, forceRefresh: Boolean): Json {
        if (forceRefresh) {
            if (!session.refreshGatewayToken()) session.ensureLoggedIn(force = true)
        } else {
            session.ensureLoggedIn(force = false)
        }

        val response = http.send("$baseUrl/$endpoint", method = method)
        if (response.status == 503) throw LibrusError.Maintenance
        val json = try { response.json() } catch (e: Exception) { Json.Null }
        if (response.status == 200) return json
        if (response.status == 401 && !forceRefresh) throw LibrusError.TokenExpired
        throwError(json, response.status)
        return json
    }

    private fun throwError(json: Json, status: Int): Nothing {
        val code = json["Code"].string ?: json["Message"].string ?: json["Status"].string ?: "HTTP $status"
        val message = json["Message"].string
        when (code) {
            "TokenIsExpired" -> throw LibrusError.TokenExpired
            "Maintenance" -> throw LibrusError.Maintenance
            "AccessDeny" -> {
                if (message == "Student timetable is not public") throw LibrusError.TimetableNotPublic
                throw LibrusError.AccessDenied(message ?: code)
            }
            "Request is denied", "Insufficient scopes" -> throw LibrusError.AccessDenied(code)
            "LuckyNumberIsNotActive" -> throw LibrusError.FeatureNotActive("szczęśliwy numerek")
            "NotesIsNotActive" -> throw LibrusError.FeatureNotActive("uwagi")
            else -> {
                if (status == 401) throw LibrusError.TokenExpired
                throw LibrusError.Api(code, message)
            }
        }
    }
}

package com.example.librusweekly.librus.core

/// Endpoints and client identifiers, ported from szkolny-android `data/api/Constants.kt` (GPL-3.0).
/// Public identifiers of the Librus mobile app from that open-source repository — not personal credentials.
object LibrusConstants {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile LibrusMobileApp"
    const val WEB_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

    // Konto Librus (portal) OAuth
    const val PORTAL_CLIENT_ID = "VaItV6oRutdo8fnjJwysnTjVlvaswf52ZqmXsJGP"
    const val PORTAL_REDIRECT_URL = "app://librus"
    const val PORTAL_AUTHORIZE_URL = "https://portal.librus.pl/konto-librus/redirect/dru"
    const val PORTAL_TOKEN_URL = "https://portal.librus.pl/oauth2/access_token"
    const val PORTAL_API_URL = "https://portal.librus.pl/api"
    const val PORTAL_ACCOUNTS_PATH = "/v3/SynergiaAccounts"
    const val PORTAL_FRESH_ACCOUNT_PATH = "/v3/SynergiaAccounts/fresh/"

    // Synergia API 2.0
    const val API_URL = "https://api.librus.pl/2.0"
    const val API_OAUTH_URL = "https://api.librus.pl/OAuth"
    const val SYNERGIA_WEB_CLIENT_ID = "46"
    const val GATEWAY_API_URL = "https://synergia.librus.pl/gateway/api/2.0"

    // Synergia website
    const val SYNERGIA_URL = "https://synergia.librus.pl"
    const val SYNERGIA_TOKEN_LOGIN_URL = "https://synergia.librus.pl/loguj/token/TOKEN/przenies"
}

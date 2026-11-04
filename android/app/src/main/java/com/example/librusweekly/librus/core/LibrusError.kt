package com.example.librusweekly.librus.core

sealed class LibrusError(message: String) : Exception(message) {
    class Network(msg: String) : LibrusError("Błąd połączenia: $msg")
    class InvalidResponse(msg: String) : LibrusError("Nieprawidłowa odpowiedź serwera: $msg")
    object InvalidLogin : LibrusError("Nieprawidłowy login lub hasło.")
    object CaptchaRequired : LibrusError("Librus wymaga rozwiązania CAPTCHA. Zaloguj się ponownie przez Konto Librus.")
    object PortalAccessDenied : LibrusError("Sesja Konta Librus wygasła. Zaloguj się ponownie.")
    object PortalAccountDisconnected : LibrusError("Konto Synergia wymaga akcji na portal.librus.pl.")
    object PortalAccountNotActivated : LibrusError("Konto Librus nie zostało aktywowane.")
    object PortalApiDisabled : LibrusError("Librus wyłączył dostęp API dla tego konta.")
    object TokenExpired : LibrusError("Token wygasł. Odśwież dane.")
    object Maintenance : LibrusError("Librus ma przerwę techniczną. Spróbuj później.")
    class AccessDenied(msg: String) : LibrusError("Brak dostępu: $msg")
    object TimetableNotPublic : LibrusError("Plan lekcji ucznia nie jest publiczny.")
    class FeatureNotActive(feature: String) : LibrusError("Funkcja nieaktywna w szkole: $feature")
    class Api(val code: String, val apiMessage: String?) :
        LibrusError("Błąd API Librus ($code)" + (apiMessage?.let { ": $it" } ?: ""))
    class SynergiaSession(msg: String) : LibrusError("Nie udało się zalogować do Synergii: $msg")
    class Parse(msg: String) : LibrusError("Nie udało się odczytać danych: $msg")
    object NotLoggedIn : LibrusError("Nie jesteś zalogowany.")
}

package com.example.librusweekly.state

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val code: String) {
    SYSTEM(""),
    ENGLISH("en"),
    POLISH("pl");

    val label: String get() = when (this) {
        SYSTEM -> "System"
        ENGLISH -> "English"
        POLISH -> "Polski"
    }

    companion object { fun of(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: SYSTEM }
}

/// Per-app language override, applied through AppCompatDelegate so it works on API 26+ the same
/// way the system per-app language does on API 33+.
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("librus-settings", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.of(prefs.getString(KEY_LANGUAGE, ""))
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value.code).apply()
            apply(value)
        }

    var sideBySide: Boolean
        get() = prefs.getBoolean(KEY_SIDE_BY_SIDE, false)
        set(value) { prefs.edit().putBoolean(KEY_SIDE_BY_SIDE, value).apply() }

    fun applyOnStartup() { apply(language) }

    private fun apply(language: AppLanguage) {
        val locales = if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
                      else LocaleListCompat.forLanguageTags(language.code)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SIDE_BY_SIDE = "sideBySide"
    }
}

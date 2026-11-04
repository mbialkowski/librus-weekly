package com.example.librusweekly.state

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.librusweekly.librus.auth.StoredAccount
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/// Persists the list of children + chosen one in EncryptedSharedPreferences (device-only, AES-GCM).
class AccountStore(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "librus-accounts",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(): Saved {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return Saved(emptyList(), null)
        val arr = JSONArray(raw)
        val accounts = mutableListOf<StoredAccount>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            accounts.add(StoredAccount(
                id = UUID.fromString(o.getString("id")),
                displayName = o.getString("displayName"),
                synergiaLogin = o.getString("login"),
                synergiaPassword = o.getString("password"),
                studentName = o.optString("studentName"),
                isParent = o.optBoolean("isParent", true)
            ))
        }
        val selected = prefs.getString(KEY_SELECTED, null)?.let { UUID.fromString(it) }
            ?.takeIf { id -> accounts.any { it.id == id } }
        return Saved(accounts, selected ?: accounts.firstOrNull()?.id)
    }

    fun save(accounts: List<StoredAccount>, selectedId: UUID?) {
        val arr = JSONArray()
        for (a in accounts) {
            arr.put(JSONObject().apply {
                put("id", a.id.toString())
                put("displayName", a.displayName)
                put("login", a.synergiaLogin)
                put("password", a.synergiaPassword)
                put("studentName", a.studentName)
                put("isParent", a.isParent)
            })
        }
        prefs.edit().apply {
            putString(KEY_ACCOUNTS, arr.toString())
            if (selectedId != null) putString(KEY_SELECTED, selectedId.toString()) else remove(KEY_SELECTED)
        }.apply()
    }

    data class Saved(val accounts: List<StoredAccount>, val selectedId: UUID?)

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_SELECTED = "selected"
    }
}

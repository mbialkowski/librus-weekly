package com.example.librusweekly.librus.auth

import java.util.UUID

/// Everything needed to log in again without asking for a password. Stored in EncryptedSharedPreferences.
data class StoredAccount(
    val id: UUID = UUID.randomUUID(),
    val displayName: String,
    val synergiaLogin: String,
    val synergiaPassword: String,
    val studentName: String,
    val isParent: Boolean
) {
    /// Human-readable name for the menu.
    val name: String get() = studentName.ifBlank { displayName }

    /// First name only — surnames are redundant when there are only a few children.
    val shortName: String
        get() = name.split(' ').firstOrNull { it.isNotEmpty() } ?: name
}

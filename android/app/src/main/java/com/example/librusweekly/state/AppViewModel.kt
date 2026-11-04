package com.example.librusweekly.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.librusweekly.librus.api.LibrusLogin
import com.example.librusweekly.librus.api.StudentClient
import com.example.librusweekly.librus.auth.StoredAccount
import com.example.librusweekly.librus.models.Day
import com.example.librusweekly.librus.models.StudentData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/// One child's in-memory state: client, latest fetched data, loading / error flags.
private class ChildState(val client: StudentClient) {
    var data: StudentData? = null
    var error: String? = null
    var loading: Boolean = false
}

data class AppUiState(
    val accounts: List<StoredAccount> = emptyList(),
    val selectedId: UUID? = null,
    val week: Day = Day.defaultTimetableWeek,
    val data: StudentData? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val sideBySide: Boolean = false
) {
    val isLoggedIn: Boolean get() = accounts.isNotEmpty()
    val current: StoredAccount? get() = accounts.firstOrNull { it.id == selectedId }
    val showsSideBySide: Boolean get() = sideBySide && accounts.size > 1
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AccountStore(app)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private val children = mutableMapOf<UUID, ChildState>()

    init {
        val saved = store.load()
        for (account in saved.accounts) children[account.id] = ChildState(StudentClient(account))
        _state.update { it.copy(accounts = saved.accounts, selectedId = saved.selectedId) }
        saved.selectedId?.let { ensureData(it) }
    }

    fun select(id: UUID) {
        _state.update { it.copy(selectedId = id, data = children[id]?.data, error = children[id]?.error, loading = children[id]?.loading == true) }
        persist()
        ensureData(id)
    }

    fun setWeek(week: Day) {
        _state.update { it.copy(week = week) }
        _state.value.selectedId?.let { ensureData(it, forceFetch = true) }
    }

    fun refresh() {
        _state.value.selectedId?.let { ensureData(it, forceFetch = true) }
    }

    fun addAccount(login: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val account = LibrusLogin.synergiaLogin(login, password)
                // Replace an existing child with the same login rather than duplicating.
                val existing = _state.value.accounts.firstOrNull { it.synergiaLogin == account.synergiaLogin }
                val accounts = _state.value.accounts.toMutableList()
                if (existing != null) {
                    accounts.remove(existing)
                    children.remove(existing.id)
                }
                accounts.add(account)
                children[account.id] = ChildState(StudentClient(account))
                _state.update { it.copy(accounts = accounts.toList(), selectedId = account.id) }
                persist()
                ensureData(account.id)
            } catch (e: Throwable) {
                onError(e.message ?: e::class.java.simpleName)
            }
        }
    }

    fun removeCurrent() {
        val id = _state.value.selectedId ?: return
        children.remove(id)
        val remaining = _state.value.accounts.filter { it.id != id }
        val nextSelected = remaining.firstOrNull()?.id
        _state.update { it.copy(accounts = remaining, selectedId = nextSelected, data = null, error = null) }
        persist()
        nextSelected?.let { ensureData(it) }
    }

    fun setSideBySide(enabled: Boolean) {
        _state.update { it.copy(sideBySide = enabled) }
    }

    private fun ensureData(id: UUID, forceFetch: Boolean = false) {
        val child = children[id] ?: return
        val cachedWeek = child.data?.timetable?.get(_state.value.week.string)
        if (child.loading) return
        if (!forceFetch && child.data != null && cachedWeek != null) {
            // Already have this week — just surface what we have.
            _state.update { it.copy(data = child.data, error = child.error, loading = false) }
            return
        }
        child.loading = true
        _state.update { if (it.selectedId == id) it.copy(loading = true) else it }
        viewModelScope.launch {
            try {
                val data = child.client.sync(_state.value.week)
                child.data = data
                child.error = null
            } catch (e: Throwable) {
                child.error = e.message ?: e::class.java.simpleName
            } finally {
                child.loading = false
                _state.update {
                    if (it.selectedId == id) it.copy(data = child.data, error = child.error, loading = false) else it
                }
            }
        }
    }

    private fun persist() {
        store.save(_state.value.accounts, _state.value.selectedId)
    }
}

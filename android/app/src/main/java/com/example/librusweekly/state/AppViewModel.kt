package com.example.librusweekly.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.librusweekly.librus.api.LibrusLogin
import com.example.librusweekly.librus.api.StudentClient
import com.example.librusweekly.librus.auth.StoredAccount
import com.example.librusweekly.librus.models.Day
import com.example.librusweekly.librus.models.StudentData
import com.example.librusweekly.librus.models.TimetableDay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private class ChildState(val client: StudentClient) {
    var data: StudentData? = null
    var changes: UpdateChanges? = null
    var timetables: MutableMap<String, List<TimetableDay>> = mutableMapOf()
    var error: String? = null
    var loading: Boolean = false
}

data class AppUiState(
    val accounts: List<StoredAccount> = emptyList(),
    val selectedId: UUID? = null,
    val week: Day = Day.defaultTimetableWeek,
    val data: StudentData? = null,
    val changes: UpdateChanges? = null,
    val datas: Map<UUID, StudentData> = emptyMap(),
    val allChanges: Map<UUID, UpdateChanges?> = emptyMap(),
    val loading: Boolean = false,
    val loadingIds: Set<UUID> = emptySet(),
    val error: String? = null,
    val errors: Map<UUID, String?> = emptyMap(),
    val sideBySide: Boolean = false
) {
    val isLoggedIn: Boolean get() = accounts.isNotEmpty()
    val current: StoredAccount? get() = accounts.firstOrNull { it.id == selectedId }
    val showsSideBySide: Boolean get() = sideBySide && accounts.size > 1
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val accountStore = AccountStore(app)
    private val cache = DataCache(app)
    val settings = AppSettings(app)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private val children = mutableMapOf<UUID, ChildState>()

    init {
        val saved = accountStore.load()
        for (account in saved.accounts) {
            val child = ChildState(StudentClient(account))
            cache.loadData(account.id)?.let {
                child.data = it
                child.timetables = it.timetable.toMutableMap()
            }
            cache.loadChanges(account.id)?.let { child.changes = it }
            children[account.id] = child
        }
        _state.update {
            it.copy(
                accounts = saved.accounts,
                selectedId = saved.selectedId,
                sideBySide = settings.sideBySide,
                data = saved.selectedId?.let { id -> children[id]?.data },
                changes = saved.selectedId?.let { id -> children[id]?.changes },
                datas = children.mapValues { (_, c) -> c.data }.filterValues { it != null }.mapValues { it.value!! },
                allChanges = children.mapValues { (_, c) -> c.changes }
            )
        }
        ensureVisible()
    }

    fun select(id: UUID) {
        val child = children[id]
        _state.update { it.copy(selectedId = id, data = child?.data, changes = child?.changes, error = child?.error, loading = child?.loading == true) }
        persistAccounts()
        ensureVisible()
    }

    fun setWeek(week: Day) {
        _state.update { it.copy(week = week) }
        ensureVisible()
    }

    fun refresh() {
        for (id in visibleIds()) fetch(id, force = true)
    }

    fun setSideBySide(enabled: Boolean) {
        settings.sideBySide = enabled
        _state.update { it.copy(sideBySide = enabled) }
        ensureVisible()
    }

    fun setLanguage(language: AppLanguage) {
        settings.language = language
    }

    fun addAccount(login: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val account = LibrusLogin.synergiaLogin(login, password)
                val existing = _state.value.accounts.firstOrNull { it.synergiaLogin == account.synergiaLogin }
                val accounts = _state.value.accounts.toMutableList()
                if (existing != null) {
                    accounts.remove(existing)
                    children.remove(existing.id)?.let { cache.remove(existing.id) }
                }
                accounts.add(account)
                children[account.id] = ChildState(StudentClient(account))
                _state.update { it.copy(accounts = accounts.toList(), selectedId = account.id, data = null, changes = null) }
                persistAccounts()
                ensureVisible()
            } catch (e: Throwable) {
                onError(e.message ?: e::class.java.simpleName)
            }
        }
    }

    /// Fetches the full body of a Synergia message on demand (used by the message sheet).
    suspend fun fetchMessageDetail(childId: UUID, messageId: String):
        com.example.librusweekly.librus.synergia.MessageDetail {
        val child = children[childId] ?: throw com.example.librusweekly.librus.core.LibrusError.NotLoggedIn
        return child.client.messageDetail(messageId)
    }

    fun removeCurrent() {
        val id = _state.value.selectedId ?: return
        children.remove(id)?.let { cache.remove(id) }
        val remaining = _state.value.accounts.filter { it.id != id }
        val nextSelected = remaining.firstOrNull()?.id
        _state.update {
            it.copy(
                accounts = remaining, selectedId = nextSelected,
                data = nextSelected?.let { id2 -> children[id2]?.data },
                changes = nextSelected?.let { id2 -> children[id2]?.changes },
                datas = it.datas.filterKeys { k -> k != id },
                allChanges = it.allChanges.filterKeys { k -> k != id }
            )
        }
        persistAccounts()
    }

    private fun visibleIds(): List<UUID> =
        if (_state.value.showsSideBySide) _state.value.accounts.map { it.id }
        else listOfNotNull(_state.value.selectedId)

    private fun ensureVisible() {
        for (id in visibleIds()) {
            val child = children[id] ?: continue
            val weekCached = child.timetables.containsKey(_state.value.week.string)
            if (child.data != null && weekCached) {
                publish(id)
            } else {
                fetch(id, force = false)
            }
        }
    }

    private fun fetch(id: UUID, force: Boolean) {
        val child = children[id] ?: return
        if (child.loading) return
        child.loading = true
        updateLoading()
        viewModelScope.launch {
            try {
                val week = _state.value.week
                val fresh = child.client.sync(week)
                val oldData = child.data
                val oldTimetables = child.timetables.toMap()
                if (oldData != null) {
                    child.changes = UpdateChanges.diff(oldData, oldTimetables, fresh)
                    cache.saveChanges(id, child.changes!!)
                }
                child.timetables.putAll(fresh.timetable)
                // Prune timetable cache to the window around the current week.
                val keep = (-4..8).map { Day.defaultTimetableWeek.adding(it * 7).string }.toSet()
                child.timetables.keys.retainAll(keep)
                fresh.timetable = child.timetables.toMutableMap()
                child.data = fresh
                child.error = null
                cache.saveData(id, fresh)
            } catch (e: Throwable) {
                child.error = e.message ?: e::class.java.simpleName
            } finally {
                child.loading = false
                publish(id)
            }
        }
    }

    private fun publish(id: UUID) {
        val child = children[id] ?: return
        _state.update { s ->
            s.copy(
                data = if (s.selectedId == id) child.data else s.data,
                changes = if (s.selectedId == id) child.changes else s.changes,
                error = if (s.selectedId == id) child.error else s.error,
                loading = children.values.any { it.loading && s.selectedId != null && children[s.selectedId] == it },
                loadingIds = children.filterValues { it.loading }.keys,
                datas = children.mapValues { it.value.data }.filterValues { it != null }.mapValues { it.value!! },
                allChanges = children.mapValues { it.value.changes },
                errors = children.mapValues { it.value.error }
            )
        }
    }

    private fun updateLoading() {
        val selected = _state.value.selectedId
        _state.update { it.copy(
            loading = selected?.let { id -> children[id]?.loading == true } ?: false,
            loadingIds = children.filterValues { it.loading }.keys
        ) }
    }

    private fun persistAccounts() {
        accountStore.save(_state.value.accounts, _state.value.selectedId)
    }
}

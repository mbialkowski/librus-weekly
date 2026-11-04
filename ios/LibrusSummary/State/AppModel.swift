import Foundation
import LibrusKit
import Observation

/// Everything cached for one child.
struct ChildState {
    var data: StudentData?
    var timetables: [String: [TimetableDay]] = [:]
    var changes: UpdateChanges?
}

@MainActor
@Observable
final class AppModel {
    /// One Synergia account per child.
    private(set) var accounts: [StoredAccount] = []
    private(set) var selectedID: UUID?
    private(set) var sideBySide = false
    private(set) var states: [UUID: ChildState] = [:]
    private(set) var loading: Set<UUID> = []
    private(set) var errors: [UUID: String] = [:]
    var week = Day.defaultTimetableWeek
    /// Set when a view wants the next SingleWeekList to scroll to a section (e.g. "announcements").
    var scrollTarget: String?

    private(set) var isAutoLoggingIn = false
    var autoLoginError: String?

    /// Name dictionaries (subjects, teachers) live in each client and are filled by a full sync,
    /// so a new client must sync once before single timetable weeks can be fetched.
    @ObservationIgnored private var clients: [UUID: StudentClient] = [:]
    @ObservationIgnored private var synced: Set<UUID> = []

    init() {
        let saved = KeychainStore.load()
        let stored = saved.accounts
        if stored.isEmpty {
            DataCache.removeAll()
        }
        let savedID = saved.selectedID.flatMap { id in stored.contains { $0.id == id } ? id : nil }
        var states: [UUID: ChildState] = [:]
        for account in stored {
            let data = DataCache.load(account.id)
            states[account.id] = ChildState(data: data, timetables: data?.timetable ?? [:],
                                            changes: DataCache.loadChanges(account.id))
        }
        accounts = stored
        selectedID = savedID ?? stored.first?.id
        sideBySide = saved.sideBySide ?? false
        self.states = states
    }

    var isLoggedIn: Bool { !accounts.isEmpty }
    var isLoading: Bool { !loading.isEmpty }
    var current: StoredAccount? { accounts.first { $0.id == selectedID } }
    var showsSideBySide: Bool { sideBySide && accounts.count > 1 }

    /// The children whose week is on screen.
    var visibleIDs: [UUID] {
        if showsSideBySide { return accounts.map(\.id) }
        return selectedID.map { [$0] } ?? []
    }

    /// Changes whenever the children on screen or the week change.
    var loadKey: String { visibleIDs.map(\.uuidString).joined(separator: ",") + "|" + week.string }

    var data: StudentData? { selectedID.flatMap { states[$0]?.data } }
    var error: String? { selectedID.flatMap { errors[$0] } }
    var changes: UpdateChanges? { selectedID.flatMap { states[$0]?.changes } }

    func name(of account: StoredAccount) -> String {
        let full = account.profiles.first?.studentName ?? account.displayName
        return full.split(separator: " ").first.map(String.init) ?? full
    }

    func summary(for id: UUID) -> WeekSummary? {
        guard let state = states[id], let data = state.data else { return nil }
        return WeekSummary(start: week, data: data, timetable: state.timetables[week.string])
    }

    var summary: WeekSummary? { selectedID.flatMap { summary(for: $0) } }

    // MARK: Children

    func addAccount(login: String, password: String, makeCurrent: Bool = true) async throws {
        let account = try await LibrusLogin.synergiaLogin(login: login, password: password)
        // Logging in again with the same login replaces that child instead of adding a duplicate.
        let logins = Set(account.profiles.map(\.login))
        for old in accounts where old.profiles.contains(where: { logins.contains($0.login) }) {
            forget(old.id)
        }
        accounts.append(account)
        states[account.id] = ChildState()
        if makeCurrent || current == nil {
            select(account.id)
        } else {
            persist()
        }
    }

    /// Logs in to every built-in account that isn't stored yet (or whose password changed).
    func autoLogin() async {
        guard !isAutoLoggingIn else { return }
        let pending = BuiltInAccounts.load().filter { builtIn in
            !accounts.contains { $0.profiles.first?.login == builtIn.login && $0.synergiaPassword == builtIn.password }
        }
        guard !pending.isEmpty else { return }
        isAutoLoggingIn = true
        defer { isAutoLoggingIn = false }
        var failures: [String] = []
        for account in pending {
            do {
                // Keeps showing the current child; the first one added becomes current if none is.
                try await addAccount(login: account.login, password: account.password, makeCurrent: false)
            } catch {
                failures.append("\(account.login): \(error.localizedDescription)")
            }
        }
        autoLoginError = failures.isEmpty ? nil : failures.joined(separator: "\n")
    }

    func select(_ id: UUID) {
        guard accounts.contains(where: { $0.id == id }) else { return }
        selectedID = id
        persist()
    }

    func setSideBySide(_ on: Bool) {
        sideBySide = on
        persist()
    }

    func removeCurrent() {
        guard let id = selectedID else { return }
        forget(id)
        selectedID = accounts.first?.id
        persist()
    }

    private func forget(_ id: UUID) {
        accounts.removeAll { $0.id == id }
        states[id] = nil
        errors[id] = nil
        clients[id] = nil
        synced.remove(id)
        DataCache.remove(id)
    }

    private func persist() {
        if accounts.isEmpty {
            KeychainStore.delete()
        } else {
            KeychainStore.save(SavedAccounts(accounts: accounts, selectedID: selectedID, sideBySide: sideBySide))
        }
    }

    private func client(for id: UUID) -> StudentClient? {
        if let client = clients[id] { return client }
        guard let account = accounts.first(where: { $0.id == id }),
              let profile = account.profiles.first else { return nil }
        let session = AccountSession(account: account) { [weak self] updated in
            await self?.accountUpdated(updated)
        }
        let client = StudentClient(profile: profile, session: session)
        clients[id] = client
        return client
    }

    private func accountUpdated(_ account: StoredAccount) {
        guard let index = accounts.firstIndex(where: { $0.id == account.id }) else { return }
        accounts[index] = account
        persist()
    }

    // MARK: Data

    /// Refresh button: fresh data for every child on screen.
    func refreshVisible() async {
        let ids = visibleIDs
        await withTaskGroup(of: Void.self) { group in
            for id in ids {
                group.addTask { await self.refresh(id) }
            }
        }
    }

    /// Shown week or children changed: use the cache, download only what isn't cached yet.
    func ensureVisibleWeek() async {
        let ids = visibleIDs
        await withTaskGroup(of: Void.self) { group in
            for id in ids {
                group.addTask { await self.ensureWeek(id) }
            }
        }
    }

    /// Full download (grades, events, homework, announcements, the shown week's timetable),
    /// remembering what changed since the previous one.
    func refresh(_ id: UUID) async {
        guard !loading.contains(id), let client = client(for: id) else { return }
        let week = self.week
        loading.insert(id)
        defer {
            loading.remove(id)
            followWeekChange(id, loaded: week)
        }
        do {
            var fresh = try await client.sync(weekStart: week)
            do {
                let today = Day.today
                let homework = try await client.synergia.homework(
                    from: min(week, today.adding(days: -35)),
                    to: max(week.adding(days: 6), today.adding(days: 35))
                )
                fresh.events += homework.map(\.asEvent)
            } catch {
                fresh.warnings.append("Homework: \(error.localizedDescription)")
            }
            guard clients[id] === client, var state = states[id] else { return }
            synced.insert(id)
            if let old = state.data {
                state.changes = UpdateChanges(old: old, oldTimetables: state.timetables, fresh: fresh)
            }
            state.timetables.merge(fresh.timetable) { $1 }
            state.data = fresh
            states[id] = state
            errors[id] = nil
            save(id)
        } catch {
            errors[id] = error.localizedDescription
        }
    }

    private func ensureWeek(_ id: UUID) async {
        guard let state = states[id], !loading.contains(id) else { return }
        // Nothing cached for this child yet.
        if state.data == nil {
            await refresh(id)
            return
        }
        // This week is cached: show it without going to Librus.
        guard state.timetables[week.string] == nil else { return }
        // A single week needs the name dictionaries, which only a full sync fills.
        guard synced.contains(id), let client = client(for: id) else {
            await refresh(id)
            return
        }
        let week = self.week
        loading.insert(id)
        defer {
            loading.remove(id)
            followWeekChange(id, loaded: week)
        }
        do {
            let days = try await client.timetable(weekStart: week)
            guard clients[id] === client else { return }
            states[id]?.timetables[week.string] = days
            errors[id] = nil
            save(id)
        } catch {
            errors[id] = error.localizedDescription
        }
    }

    /// If the week changed while a download was running, the new week's task was skipped.
    private func followWeekChange(_ id: UUID, loaded: Day) {
        guard week != loaded, visibleIDs.contains(id) else { return }
        Task { await self.ensureWeek(id) }
    }

    private func save(_ id: UUID) {
        guard let state = states[id], var cached = state.data else { return }
        // Keep a range of weeks around the current one, not every week ever viewed.
        let current = Day.today.weekStart
        let keep = Set((-4...8).map { current.adding(days: 7 * $0).string })
        cached.timetable = state.timetables.filter { keep.contains($0.key) }
        DataCache.save(cached, for: id)
        if let changes = state.changes {
            DataCache.saveChanges(changes, for: id)
        }
    }
}

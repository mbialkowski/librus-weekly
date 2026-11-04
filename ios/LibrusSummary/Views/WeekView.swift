import LibrusKit
import SwiftUI

/// Shell around the week: one child's list, or all children side by side.
struct WeekView: View {
    @Environment(AppModel.self) private var model
    @State private var showAddChild = false
    @State private var confirmRemove = false
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            Group {
                if model.showsSideBySide {
                    SideBySideList()
                } else {
                    SingleWeekList()
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { await model.refreshVisible() }
            .task(id: model.loadKey) { await model.ensureVisibleWeek() }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    ChildMenu(showAddChild: $showAddChild, confirmRemove: $confirmRemove,
                              showSettings: $showSettings)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    RefreshButton()
                }
            }
            .sheet(isPresented: $showAddChild) {
                LoginView(isAddingChild: true)
                    .environment(model)
            }
            .sheet(isPresented: $showSettings) {
                SettingsView().environment(AppSettings.shared)
            }
            .confirmationDialog(removeTitle, isPresented: $confirmRemove, titleVisibility: .visible) {
                Button(model.accounts.count > 1 ? "Remove" : "Log out", role: .destructive) {
                    model.removeCurrent()
                }
            }
        }
    }

    private var title: String {
        if model.showsSideBySide { return "All children" }
        return model.current.map { model.name(of: $0) } ?? "School Week"
    }

    private var removeTitle: String {
        let name = model.current.map { model.name(of: $0) } ?? "this child"
        return model.accounts.count > 1
            ? "Remove \(name) and delete the saved password?"
            : "Log out and delete the saved password?"
    }
}

private struct RefreshButton: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        if model.isLoading {
            ProgressView()
        } else {
            Button {
                Task { await model.refreshVisible() }
            } label: {
                Label("Get fresh data", systemImage: "arrow.clockwise")
            }
        }
    }
}

private struct ChildMenu: View {
    @Environment(AppModel.self) private var model
    @Binding var showAddChild: Bool
    @Binding var confirmRemove: Bool
    @Binding var showSettings: Bool

    var body: some View {
        Menu {
            ForEach(model.accounts) { account in
                Button {
                    model.select(account.id)
                    model.setSideBySide(false)
                } label: {
                    if account.id == model.selectedID && !model.showsSideBySide {
                        Label(model.name(of: account), systemImage: "checkmark")
                    } else {
                        Text(model.name(of: account))
                    }
                }
            }
            if model.accounts.count > 1 {
                Button {
                    model.setSideBySide(true)
                } label: {
                    if model.showsSideBySide {
                        Label("Side by side", systemImage: "checkmark")
                    } else {
                        Label("Side by side", systemImage: "rectangle.split.2x1")
                    }
                }
            }
            Divider()
            Button { showAddChild = true } label: {
                Label("Add child", systemImage: "person.badge.plus")
            }
            if !model.showsSideBySide {
                Button(role: .destructive) { confirmRemove = true } label: {
                    if model.accounts.count > 1 {
                        Label("Remove this child", systemImage: "person.badge.minus")
                    } else {
                        Label("Log out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
            Divider()
            Button { showSettings = true } label: {
                Label("Settings", systemImage: "gearshape")
            }
        } label: {
            Image(systemName: model.accounts.count > 1 ? "person.2.circle" : "person.crop.circle")
        }
    }
}

// MARK: - One child

private struct SingleWeekList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let highlighted = model.changes?.highlightedEventIDs ?? []
        let newGrades = model.changes?.newGradeIDs ?? []
        let newAnnouncements = model.changes?.newAnnouncementIDs ?? []
        ScrollViewReader { proxy in
        List {
            WeekNavigator()

            if let error = model.error {
                Label(error, systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.orange)
            }
            if let error = model.autoLoginError {
                Label("Automatic login failed. \(error)", systemImage: "person.crop.circle.badge.exclamationmark")
                    .foregroundStyle(.orange)
            }

            if let summary = model.summary, let data = model.data {
                ChangesSection(changes: model.changes, data: data)
                OverviewSection(summary: summary)
                WarningsSection(warnings: data.warnings)
                if !summary.grades.isEmpty {
                    Section("New grades") {
                        ForEach(summary.grades) { GradeRow(item: $0, isNew: newGrades.contains($0.id)) }
                    }
                }
                if !summary.cancelled.isEmpty {
                    Section("Cancelled lessons") {
                        ForEach(summary.cancelled) { CancelledRow(lesson: $0) }
                    }
                }
                if !summary.addedEvents.isEmpty {
                    Section("Added to the calendar this week") {
                        ForEach(summary.addedEvents) {
                            EventRow(item: $0, showDay: true, isNew: highlighted.contains($0.id))
                        }
                    }
                }
                ForEach(summary.days) { day in
                    DaySection(day: day, hasTimetable: summary.hasTimetable, highlighted: highlighted)
                }
                if !summary.announcements.isEmpty {
                    Section("Announcements") {
                        ForEach(summary.announcements) {
                            AnnouncementRow(announcement: $0, isNew: newAnnouncements.contains($0.id))
                        }
                    }
                    .id("announcements")
                }
            } else if model.isLoading {
                HStack { Spacer(); ProgressView(); Spacer() }
            }
        }
        .onAppear {
            if let target = model.scrollTarget {
                model.scrollTarget = nil
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                    withAnimation { proxy.scrollTo(target, anchor: .top) }
                }
            }
        }
        }
    }
}

private struct ChangesSection: View {
    let changes: UpdateChanges?
    let data: StudentData

    private static let maxEvents = 8

    var body: some View {
        Section {
            if let changes {
                if changes.count == 0 {
                    Text("No changes since \(changes.since.updateLabel).")
                        .foregroundStyle(.secondary)
                } else {
                    if !changes.newGradeIDs.isEmpty {
                        Label(changes.newGradeIDs.count == 1 ? "1 new grade" : "\(changes.newGradeIDs.count) new grades",
                              systemImage: "star.fill")
                    }
                    let added = events(changes.newEventIDs)
                    ForEach(added.prefix(Self.maxEvents)) { ChangeLine(prefix: "New", event: $0) }
                    if added.count > Self.maxEvents {
                        Text("and \(added.count - Self.maxEvents) more new").foregroundStyle(.secondary)
                    }
                    ForEach(events(changes.changedEventIDs).prefix(Self.maxEvents)) { ChangeLine(prefix: "Changed", event: $0) }
                    ForEach(changes.lessonChanges) { lesson in
                        Label("\(lesson.changeLabel): \(lesson.day.shortLabel) · \(lesson.number). \(lesson.subject ?? "Lesson")",
                              systemImage: "calendar.badge.exclamationmark")
                    }
                    if !changes.newAnnouncementIDs.isEmpty {
                        Label(changes.newAnnouncementIDs.count == 1
                              ? "1 new announcement" : "\(changes.newAnnouncementIDs.count) new announcements",
                              systemImage: "megaphone")
                    }
                }
            } else {
                Text("Showing saved data. Tap \(Image(systemName: "arrow.clockwise")) to get fresh data and see what changed.")
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("Updated \(data.fetchedAt.updateLabel)")
        }
        .font(.subheadline)
    }

    private func events(_ ids: Set<Int>) -> [AgendaEvent] {
        data.events.filter { ids.contains($0.id) }.sorted { $0.day < $1.day }
    }
}

private struct ChangeLine: View {
    let prefix: String
    let event: AgendaEvent

    private var text: String {
        var parts = ["\(prefix): \(event.category)"]
        if let subject = event.subject { parts.append(subject) }
        return parts.joined(separator: ", ") + " · " + event.day.shortLabel
    }

    var body: some View {
        Label {
            Text(text)
        } icon: {
            Image(systemName: event.category == "zadanie domowe" ? "book" : "pencil.and.list.clipboard")
        }
    }
}

private struct OverviewSection: View {
    let summary: WeekSummary

    var body: some View {
        Section {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 12) {
                Stat(value: "\(summary.grades.count)", label: summary.grades.count == 1 ? "new grade" : "new grades")
                Stat(value: "\(summary.eventCount)", label: summary.eventCount == 1 ? "test/event" : "tests/events")
                Stat(value: "\(summary.addedEvents.count)", label: "newly added")
                Stat(value: summary.hasTimetable ? "\(summary.cancelled.count)" : "–", label: "cancelled")
                Stat(value: summary.hasTimetable ? "\(summary.otherChangeCount)" : "–",
                     label: summary.otherChangeCount == 1 ? "other change" : "other changes")
                Stat(value: "\(summary.announcements.count)",
                     label: summary.announcements.count == 1 ? "notice" : "notices")
                if let lucky = summary.luckyNumber {
                    Stat(value: "\(lucky.number)", label: "lucky \(lucky.day.weekdayShort)")
                }
            }
        }
    }
}

private struct Stat: View {
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.title2.bold()).monospacedDigit().foregroundStyle(.tint)
            Text(label).font(.caption).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct WarningsSection: View {
    let warnings: [String]

    var body: some View {
        if !warnings.isEmpty {
            Section("Not everything loaded") {
                ForEach(warnings, id: \.self) { warning in
                    Label(warning, systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }
            }
        }
    }
}

private struct DaySection: View {
    let day: DaySummary
    let hasTimetable: Bool
    let highlighted: Set<Int>

    var body: some View {
        Section {
            ForEach(day.events) { EventRow(item: $0, showDay: false, isNew: highlighted.contains($0.id)) }
            ForEach(day.lessons) { LessonRow(lesson: $0) }
            if day.lessons.isEmpty && day.events.isEmpty {
                if hasTimetable {
                    Text("No lessons").foregroundStyle(.secondary)
                } else {
                    Text("Loading…").foregroundStyle(.secondary)
                }
            }
        } header: {
            Text(day.day.longLabel)
                .foregroundStyle(day.day == .today ? Color.accentColor : .secondary)
        }
    }
}

// MARK: - Shared pieces

struct WeekNavigator: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Section {
            HStack {
                Button { model.week = model.week.adding(days: -7) } label: { Image(systemName: "chevron.left") }
                Spacer()
                VStack(spacing: 2) {
                    Text("\(model.week.shortLabel) – \(model.week.adding(days: 6).shortLabel)")
                        .font(.headline)
                    if model.week != Day.defaultTimetableWeek {
                        Button("This week") { model.week = Day.defaultTimetableWeek }
                            .font(.caption)
                    }
                }
                Spacer()
                Button { model.week = model.week.adding(days: 7) } label: { Image(systemName: "chevron.right") }
            }
            .buttonStyle(.borderless)
        }
    }
}

struct NewBadge: View {
    var body: some View {
        Text("NEW")
            .font(.caption2.weight(.bold))
            .padding(.horizontal, 4)
            .padding(.vertical, 1)
            .background(Color.accentColor, in: Capsule())
            .foregroundStyle(.white)
    }
}

struct CategoryChip: View {
    let event: AgendaEvent
    var font: Font = .caption.weight(.semibold)

    var body: some View {
        Text(event.category)
            .font(font)
            .lineLimit(1)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background((Color(hex: event.colorHex) ?? .blue).opacity(0.85), in: Capsule())
            .foregroundStyle(.black.opacity(0.8))
    }
}

struct GradeChip: View {
    let grade: Grade
    var font: Font = .subheadline.weight(.semibold)

    var body: some View {
        Text(grade.name)
            .font(font)
            .monospacedDigit()
            .frame(minWidth: 24)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background((Color(hex: grade.colorHex) ?? .blue).opacity(0.85), in: RoundedRectangle(cornerRadius: 6))
            .foregroundStyle(.black.opacity(0.8))
    }
}

struct EventRow: View {
    let item: EventItem
    let showDay: Bool
    var isNew = false

    var body: some View {
        let event = item.event
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                CategoryChip(event: event)
                if let subject = event.subject {
                    Text(subject).font(.subheadline.weight(.semibold))
                }
                if isNew { NewBadge() }
                Spacer()
                Text(item.when(showDay: showDay)).font(.caption).monospacedDigit().foregroundStyle(.secondary)
            }
            if !event.topic.isEmpty {
                Text(event.topic).font(.subheadline).textSelection(.enabled)
            }
            if !details.isEmpty {
                Text(details).font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private var details: String {
        var parts: [String] = []
        if let teacher = item.event.teacher { parts.append(teacher) }
        if let classroom = item.classroom { parts.append("room \(classroom)") }
        if let added = item.event.addedDate { parts.append("added \(Day(date: added).shortLabel)") }
        return parts.joined(separator: " · ")
    }
}

extension EventItem {
    func when(showDay: Bool) -> String {
        var parts: [String] = []
        if showDay { parts.append(event.day.shortLabel) }
        if let start {
            parts.append(end.map { "\(start)–\($0)" } ?? start)
        }
        if let lessonNumber { parts.append("lesson \(lessonNumber)") }
        return parts.joined(separator: " · ")
    }
}

extension Lesson {
    var isGone: Bool { kind == .cancelled || kind == .shiftedSource }

    var changeLabel: String {
        switch kind {
        case .normal: return "Lesson"
        case .cancelled: return "Cancelled"
        case .change: return "Substitution"
        case .shiftedSource, .shiftedTarget: return "Moved"
        }
    }

    var note: String? {
        switch kind {
        case .normal:
            return nil
        case .cancelled:
            return "Cancelled"
        case .change:
            let old = [oldSubject, oldTeacher].compactMap { $0 }.joined(separator: ", ")
            return old.isEmpty ? "Substitution" : "Substitution for \(old)"
        case .shiftedSource:
            return "Moved" + (otherDay.map { " to \($0.shortLabel)" } ?? "")
                + (otherNumber.map { ", lesson \($0)" } ?? "")
        case .shiftedTarget:
            return "Moved" + (otherDay.map { " from \($0.shortLabel)" } ?? "")
                + (otherNumber.map { ", lesson \($0)" } ?? "")
        }
    }
}

struct CancelledRow: View {
    let lesson: Lesson

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(lesson.day.weekdayShort)
                .font(.subheadline.weight(.semibold))
                .frame(width: 40, alignment: .leading)
            Text("\(lesson.number). \(lesson.subject ?? "Lesson")")
                .strikethrough()
                .foregroundStyle(.secondary)
            Spacer()
            Text("\(lesson.start)–\(lesson.end)")
                .font(.caption)
                .monospacedDigit()
                .foregroundStyle(.secondary)
        }
    }
}

struct LessonRow: View {
    let lesson: Lesson

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text("\(lesson.number)")
                .font(.headline)
                .monospacedDigit()
                .frame(width: 24, alignment: .trailing)
            VStack(alignment: .leading, spacing: 2) {
                Text(lesson.subject ?? "Lesson")
                    .strikethrough(lesson.isGone)
                    .foregroundStyle(lesson.isGone ? .secondary : .primary)
                let details = [lesson.classroom, lesson.teacher].compactMap { $0 }.joined(separator: " · ")
                if !details.isEmpty {
                    Text(details).font(.caption).foregroundStyle(.secondary)
                }
                if let note = lesson.note {
                    Text(note)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(lesson.kind == .cancelled ? Color.red : Color.orange)
                }
            }
            Spacer()
            Text("\(lesson.start)–\(lesson.end)")
                .font(.caption)
                .monospacedDigit()
                .foregroundStyle(.secondary)
        }
    }
}

struct GradeRow: View {
    let item: NewGrade
    var isNew = false

    var body: some View {
        let grade = item.grade
        HStack(alignment: .top, spacing: 12) {
            GradeChip(grade: grade)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(item.subject ?? "Subject").font(.subheadline.weight(.semibold))
                    if isNew { NewBadge() }
                }
                if !details.isEmpty {
                    Text(details).font(.caption).foregroundStyle(.secondary)
                }
                if let comment = grade.comment, !comment.isEmpty {
                    Text(comment).font(.caption).textSelection(.enabled)
                }
                Text(addedBy).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private var details: String {
        let grade = item.grade
        var parts: [String] = []
        if !grade.category.isEmpty { parts.append(grade.category) }
        if grade.weight > 0 { parts.append("weight \(Self.weight(grade.weight))") }
        if let kind = kindLabel { parts.append(kind) }
        if grade.isImprovement { parts.append("improvement") }
        return parts.joined(separator: " · ")
    }

    private var kindLabel: String? {
        switch item.grade.kind {
        case .normal: return nil
        case .semesterProposed: return "predicted semester grade"
        case .semesterFinal: return "semester grade"
        case .yearProposed: return "predicted final grade"
        case .yearFinal: return "final grade"
        }
    }

    private var addedBy: String {
        let day = item.grade.addedDate.map { Day(date: $0).longLabel }
        return [item.grade.teacherName, day].compactMap { $0 }.joined(separator: " · ")
    }

    private static func weight(_ value: Double) -> String {
        value == value.rounded() ? String(Int(value)) : String(format: "%.1f", value)
    }
}

struct AnnouncementRow: View {
    let announcement: Announcement
    var isNew = false

    var body: some View {
        DisclosureGroup {
            Text(announcement.text)
                .font(.subheadline)
                .textSelection(.enabled)
                .padding(.vertical, 4)
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(announcement.subject).font(.subheadline.weight(.semibold))
                    if isNew { NewBadge() }
                }
                let meta = [announcement.author, announcement.startDay?.shortLabel].compactMap { $0 }
                if !meta.isEmpty {
                    Text(meta.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }
}

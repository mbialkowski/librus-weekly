import LibrusKit
import SwiftUI

/// All children's weeks in columns, one row per day.
struct SideBySideList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let ids = model.accounts.map(\.id)
        let summaries = summaries(for: ids)
        VStack(spacing: 0) {
            Columns(ids: ids) { id in ChildHeader(id: id) }
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(.regularMaterial)
            listBody(ids: ids, summaries: summaries)
        }
    }

    @ViewBuilder
    private func listBody(ids: [UUID], summaries: [UUID: WeekSummary]) -> some View {
        List {
            WeekNavigator()

            Section("This week") {
                Columns(ids: ids) { id in
                    ChildOverview(summary: summaries[id], changes: model.states[id]?.changes)
                }
            }

            if summaries.values.contains(where: { !$0.grades.isEmpty }) {
                Section("New grades") {
                    Columns(ids: ids) { id in
                        GradesColumn(grades: summaries[id]?.grades ?? [],
                                     newIDs: model.states[id]?.changes?.newGradeIDs ?? [])
                    }
                }
            }

            ForEach(days(summaries), id: \.self) { day in
                Section {
                    Columns(ids: ids) { id in
                        DayColumn(summary: summaries[id], day: day,
                                  highlighted: model.states[id]?.changes?.highlightedEventIDs ?? [])
                    }
                } header: {
                    Text(day.longLabel)
                        .foregroundStyle(day == .today ? Color.accentColor : .secondary)
                }
            }

            if summaries.values.contains(where: { !$0.cancelled.isEmpty }) {
                Section("Cancelled lessons") {
                    Columns(ids: ids) { id in
                        CancelledColumn(lessons: summaries[id]?.cancelled ?? [])
                    }
                }
            }

            if summaries.values.contains(where: { !$0.addedEvents.isEmpty }) {
                Section("Added this week") {
                    Columns(ids: ids) { id in
                        AddedEventsColumn(items: summaries[id]?.addedEvents ?? [],
                                          newIDs: model.states[id]?.changes?.highlightedEventIDs ?? [])
                    }
                }
            }

            do {
                let split = splitAnnouncements(ids: ids, summaries: summaries, model: model)
                let hasPerKid = ids.contains { !(split.perKid[$0]?.isEmpty ?? true) }
                if hasPerKid || !split.shared.isEmpty {
                    Section("Announcements") {
                        if hasPerKid {
                            Columns(ids: ids) { id in
                                AnnouncementsAccordion(
                                    items: split.perKid[id] ?? [],
                                    newIDs: split.perKidNewIDs[id] ?? [],
                                    label: nil
                                )
                            }
                        }
                        if !split.shared.isEmpty {
                            AnnouncementsAccordion(
                                items: split.shared,
                                newIDs: split.sharedNewIDs,
                                label: "Shared"
                            )
                        }
                    }
                }
            }

            if ids.contains(where: { (model.states[$0]?.data?.warnings.isEmpty == false) }) {
                Section("Not everything loaded") {
                    Columns(ids: ids) { id in
                        WarningsColumn(warnings: model.states[id]?.data?.warnings ?? [])
                    }
                }
            }
        }
    }

    private func summaries(for ids: [UUID]) -> [UUID: WeekSummary] {
        var result: [UUID: WeekSummary] = [:]
        for id in ids {
            result[id] = model.summary(for: id)
        }
        return result
    }

    /// Mon–Fri, plus a weekend day when any child has something on it.
    private func days(_ summaries: [UUID: WeekSummary]) -> [Day] {
        (0..<7).compactMap { offset -> Day? in
            let day = model.week.adding(days: offset)
            let busy = summaries.values.contains { summary in summary.days.contains { $0.day == day } }
            return offset < 5 || busy ? day : nil
        }
    }
}

/// Equal-width columns separated by thin lines.
private struct Columns<Content: View>: View {
    let ids: [UUID]
    @ViewBuilder let content: (UUID) -> Content

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(Array(ids.enumerated()), id: \.element) { index, id in
                if index > 0 { Divider() }
                content(id).frame(maxWidth: .infinity, alignment: .topLeading)
            }
        }
    }
}

/// Pinned header row above the list — tap the name to switch to the single-child view.
/// Lives outside the List so it stays visible as the user scrolls.
private struct ChildHeader: View {
    @Environment(AppModel.self) private var model
    let id: UUID

    var body: some View {
        let account = model.accounts.first { $0.id == id }
        Button {
            model.select(id)
            model.setSideBySide(false)
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(account.map { model.name(of: $0) } ?? "Child")
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(2)
                    if model.loading.contains(id) { ProgressView().controlSize(.mini) }
                }
                if let fetchedAt = model.states[id]?.data?.fetchedAt {
                    Text("Updated \(fetchedAt.updateLabel)").font(.caption2).foregroundStyle(.secondary)
                }
                if let error = model.errors[id] {
                    Text(error).font(.caption2).foregroundStyle(.orange)
                        .fixedSize(horizontal: false, vertical: true)
                        .textSelection(.enabled)
                }
            }
        }
        .buttonStyle(.borderless)
        .foregroundStyle(.primary)
    }
}

private struct ChildOverview: View {
    let summary: WeekSummary?
    let changes: UpdateChanges?

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            if let summary {
                line(summary.grades.count, "new grade", "new grades")
                line(summary.eventCount, "test/event", "tests/events")
                if summary.hasTimetable {
                    line(summary.cancelled.count, "cancelled", "cancelled")
                    line(summary.otherChangeCount, "other change", "other changes")
                }
                if let lucky = summary.luckyNumber {
                    Text("Lucky number \(lucky.number) (\(lucky.day.weekdayShort))").font(.caption)
                }
                if let changes {
                    Text(changes.count == 0 ? "No changes at last update" : "\(changes.count) changed at last update")
                        .font(.caption2)
                        .foregroundStyle(changes.count == 0 ? Color.secondary : Color.accentColor)
                }
            } else {
                Text("Loading…").font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private func line(_ count: Int, _ one: String, _ many: String) -> some View {
        Text("\(count) \(count == 1 ? one : many)")
            .font(.caption)
            .foregroundStyle(count == 0 ? .secondary : .primary)
    }
}

private struct GradesColumn: View {
    let grades: [NewGrade]
    let newIDs: Set<Int>

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if grades.isEmpty {
                Text("None").font(.caption).foregroundStyle(.secondary)
            }
            ForEach(grades) { item in
                HStack(spacing: 4) {
                    GradeChip(grade: item.grade, font: .caption.weight(.semibold))
                    Text(item.subject ?? "Subject").font(.caption).lineLimit(1)
                    if newIDs.contains(item.id) { NewBadge() }
                }
            }
        }
    }
}

private struct DayColumn: View {
    let summary: WeekSummary?
    let day: Day
    let highlighted: Set<Int>

    var body: some View {
        let entry = summary?.days.first { $0.day == day }
        VStack(alignment: .leading, spacing: 6) {
            if let entry {
                if !lessonRange(entry).isEmpty {
                    Text(lessonRange(entry)).font(.caption2.weight(.semibold))
                        .monospacedDigit().foregroundStyle(.secondary)
                }
                ForEach(entry.events) { CompactEvent(item: $0, isNew: highlighted.contains($0.id)) }
                if !entry.lessons.isEmpty {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(entry.lessons) { CompactLesson(lesson: $0) }
                    }
                } else if entry.events.isEmpty {
                    Text(summary?.hasTimetable == true ? "No lessons" : "Loading…")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else {
                Text(summary == nil ? "Loading…" : "—").font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    /// Shows the day's full span (first lesson start – last lesson end) so you can see at a
    /// glance when the day begins and ends. Cancelled / moved-away lessons are skipped.
    private func lessonRange(_ entry: DaySummary) -> String {
        let present = entry.lessons.filter { $0.kind != .cancelled && $0.kind != .shiftedSource }
        let starts = present.compactMap { hhmmToMinutes($0.start) }
        let ends = present.compactMap { hhmmToMinutes($0.end) }
        guard let first = starts.min(), let last = ends.max() else { return "" }
        func fmt(_ m: Int) -> String { String(format: "%d:%02d", m / 60, m % 60) }
        return "\(fmt(first))–\(fmt(last))"
    }
}

private func hhmmToMinutes(_ s: String?) -> Int? {
    guard let s, s.count >= 4 else { return nil }
    let parts = s.prefix(5).split(separator: ":")
    guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
    return h * 60 + m
}

private struct CompactEvent: View {
    let item: EventItem
    let isNew: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                CategoryChip(event: item.event, font: .caption2.weight(.semibold))
                if isNew { NewBadge() }
            }
            if let subject = item.event.subject {
                Text(subject).font(.caption.weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)
            }
            if !item.event.topic.isEmpty {
                Text(item.event.topic).font(.caption2)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
            }
            let when = item.when(showDay: false)
            if !when.isEmpty {
                Text(when).font(.caption2).foregroundStyle(.secondary)
            }
        }
    }
}

private struct CompactLesson: View {
    let lesson: Lesson

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text("\(lesson.number)")
                .font(.caption2.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: 14, alignment: .trailing)
            VStack(alignment: .leading, spacing: 1) {
                Text(lesson.subject ?? "Lesson")
                    .font(.caption)
                    .fixedSize(horizontal: false, vertical: true)
                    .strikethrough(lesson.isGone)
                    .foregroundStyle(color)
                if let note = lesson.note {
                    Text(note)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(lesson.kind == .cancelled ? Color.red : Color.orange)
                        .fixedSize(horizontal: false, vertical: true)
                }
                let meta = [lesson.classroom, lesson.teacher].compactMap { $0 }.joined(separator: " · ")
                if lesson.kind != .normal && !meta.isEmpty {
                    Text(meta)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private var color: Color {
        switch lesson.kind {
        case .normal: return .primary
        case .cancelled, .shiftedSource: return .red
        case .change, .shiftedTarget: return .orange
        }
    }
}

private struct CancelledColumn: View {
    let lessons: [Lesson]

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            if lessons.isEmpty {
                Text("None").font(.caption).foregroundStyle(.secondary)
            }
            ForEach(lessons) { lesson in
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(lesson.day.weekdayShort)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(width: 26, alignment: .leading)
                    Text("\(lesson.number). \(lesson.subject ?? "Lesson")")
                        .font(.caption)
                        .strikethrough()
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
        }
    }
}

private struct AddedEventsColumn: View {
    let items: [EventItem]
    let newIDs: Set<Int>

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if items.isEmpty {
                Text("None").font(.caption).foregroundStyle(.secondary)
            }
            ForEach(items) { item in
                VStack(alignment: .leading, spacing: 1) {
                    HStack(spacing: 4) {
                        Text(item.event.day.shortLabel)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(.secondary)
                        CategoryChip(event: item.event, font: .caption2.weight(.semibold))
                        if newIDs.contains(item.id) { NewBadge() }
                    }
                    if let subject = item.event.subject {
                        Text(subject).font(.caption).lineLimit(1)
                    }
                    if !item.event.topic.isEmpty {
                        Text(item.event.topic).font(.caption2).foregroundStyle(.secondary).lineLimit(2)
                    }
                }
            }
        }
    }
}

private struct AnnouncementsAccordion: View {
    let items: [Announcement]
    let newIDs: Set<String>
    /// Optional prefix on the header (e.g. "Shared" for the dedup'd bucket across kids).
    var label: String? = nil
    @State private var expanded = false
    @State private var openIDs: Set<String> = []

    var body: some View {
        if items.isEmpty {
            Text("None").font(.caption).foregroundStyle(.secondary)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Button { withAnimation { expanded.toggle() } } label: {
                    HStack(spacing: 4) {
                        let count = items.count == 1 ? "1 announcement" : "\(items.count) announcements"
                        Text(label.map { "\($0): \(count)" } ?? count)
                            .font(.caption.weight(.semibold))
                        if newIDs.count > 0 { NewBadge() }
                        Spacer(minLength: 2)
                        Image(systemName: expanded ? "chevron.up" : "chevron.down")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                .buttonStyle(.borderless)
                .foregroundStyle(.primary)

                if expanded {
                    ForEach(items) { item in
                        AnnouncementAccordionRow(
                            announcement: item,
                            isNew: newIDs.contains(item.id),
                            isOpen: openIDs.contains(item.id),
                            toggle: {
                                withAnimation {
                                    if openIDs.contains(item.id) { openIDs.remove(item.id) }
                                    else { openIDs.insert(item.id) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private struct AnnouncementAccordionRow: View {
    let announcement: Announcement
    let isNew: Bool
    let isOpen: Bool
    let toggle: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Button(action: toggle) {
                HStack(spacing: 4) {
                    Text(announcement.subject)
                        .font(.caption.weight(.semibold))
                        .fixedSize(horizontal: false, vertical: true)
                    if isNew { NewBadge() }
                    Spacer(minLength: 2)
                    Image(systemName: isOpen ? "chevron.up" : "chevron.down")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.borderless)
            .foregroundStyle(.primary)

            let meta = [announcement.author, announcement.startDay?.shortLabel].compactMap { $0 }
            if !meta.isEmpty {
                Text(meta.joined(separator: " · "))
                    .font(.caption2).foregroundStyle(.secondary)
            }
            if isOpen {
                Text(announcement.text)
                    .font(.caption)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
                    .padding(.top, 2)
            }
        }
        .padding(.leading, 4)
    }
}

private struct WarningsColumn: View {
    let warnings: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            if warnings.isEmpty {
                Text("—").font(.caption).foregroundStyle(.secondary)
            }
            ForEach(warnings, id: \.self) { warning in
                Label(warning, systemImage: "exclamationmark.triangle")
                    .font(.caption2)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
            }
        }
    }
}

// MARK: - Announcement dedup across kids

private struct AnnouncementSplit {
    var perKid: [UUID: [Announcement]]
    var perKidNewIDs: [UUID: Set<String>]
    var shared: [Announcement]
    var sharedNewIDs: Set<String>
}

/// Announcements that every kid has (same subject + date + start of body) are shown once,
/// in a full-width "Shared" accordion. The rest stays per-child.
@MainActor
private func splitAnnouncements(ids: [UUID], summaries: [UUID: WeekSummary], model: AppModel) -> AnnouncementSplit {
    func key(_ a: Announcement) -> String {
        "\(a.subject)|\(a.startDay?.string ?? "")|\(a.text.prefix(120))"
    }

    var byKid: [UUID: [Announcement]] = [:]
    for id in ids { byKid[id] = summaries[id]?.announcements ?? [] }

    var keyToKids: [String: Set<UUID>] = [:]
    var exemplar: [String: Announcement] = [:]
    for id in ids {
        for ann in byKid[id] ?? [] {
            let k = key(ann)
            keyToKids[k, default: []].insert(id)
            if exemplar[k] == nil { exemplar[k] = ann }
        }
    }

    let allKids = Set(ids)
    var sharedKeys: Set<String> = []
    var shared: [Announcement] = []
    for (k, kids) in keyToKids where kids == allKids {
        sharedKeys.insert(k)
        shared.append(exemplar[k]!)
    }
    shared.sort { ($0.startDay?.string ?? "") > ($1.startDay?.string ?? "") }

    var perKid: [UUID: [Announcement]] = [:]
    var perKidNewIDs: [UUID: Set<String>] = [:]
    for id in ids {
        perKid[id] = (byKid[id] ?? []).filter { !sharedKeys.contains(key($0)) }
        perKidNewIDs[id] = model.states[id]?.changes?.newAnnouncementIDs ?? []
    }

    var sharedNewIDs: Set<String> = []
    for k in sharedKeys {
        for id in ids {
            let kidNew = model.states[id]?.changes?.newAnnouncementIDs ?? []
            let kidAnns = byKid[id] ?? []
            if kidAnns.contains(where: { key($0) == k && kidNew.contains($0.id) }) {
                sharedNewIDs.insert(exemplar[k]!.id)
                break
            }
        }
    }

    return AnnouncementSplit(perKid: perKid, perKidNewIDs: perKidNewIDs,
                             shared: shared, sharedNewIDs: sharedNewIDs)
}

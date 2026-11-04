import LibrusKit
import SwiftUI

/// All children's weeks in columns, one row per day.
struct SideBySideList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let ids = model.accounts.map(\.id)
        let summaries = summaries(for: ids)
        List {
            WeekNavigator()

            Section {
                Columns(ids: ids) { id in ChildHeader(id: id) }
            } footer: {
                Text("Tap a name to see that child's full week.")
            }

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

            if summaries.values.contains(where: { !$0.announcements.isEmpty }) {
                Section("Announcements") {
                    Columns(ids: ids) { id in
                        AnnouncementsLink(id: id,
                                          count: summaries[id]?.announcements.count ?? 0,
                                          newCount: model.states[id]?.changes?.newAnnouncementIDs.count ?? 0)
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
                    Text(error).font(.caption2).foregroundStyle(.orange).lineLimit(3)
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
                Text(subject).font(.caption.weight(.semibold)).lineLimit(1)
            }
            if !item.event.topic.isEmpty {
                Text(item.event.topic).font(.caption2).lineLimit(3)
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
            Text(lesson.subject ?? "Lesson")
                .font(.caption)
                .lineLimit(1)
                .strikethrough(lesson.isGone)
                .foregroundStyle(color)
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

private struct AnnouncementsLink: View {
    @Environment(AppModel.self) private var model
    let id: UUID
    let count: Int
    let newCount: Int

    var body: some View {
        if count == 0 {
            Text("None").font(.caption).foregroundStyle(.secondary)
        } else {
            Button {
                model.select(id)
                model.setSideBySide(false)
                model.scrollTarget = "announcements"
            } label: {
                HStack(spacing: 4) {
                    Text(count == 1 ? "1 announcement" : "\(count) announcements")
                        .font(.caption.weight(.semibold))
                    if newCount > 0 { NewBadge() }
                    Spacer(minLength: 2)
                    Image(systemName: "chevron.right").font(.caption2).foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.borderless)
        }
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
                    .lineLimit(3)
            }
        }
    }
}

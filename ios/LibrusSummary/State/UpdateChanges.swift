import Foundation
import LibrusKit

/// What the latest refresh brought, compared to the data cached before it.
struct UpdateChanges: Codable {
    var since: Date
    var at: Date
    var newGradeIDs: Set<Int>
    var newEventIDs: Set<Int>
    var changedEventIDs: Set<Int>
    var newAnnouncementIDs: Set<String>
    /// Cancellations, substitutions and moves that appeared in timetable weeks both downloads had.
    var lessonChanges: [Lesson]

    init(old: StudentData, oldTimetables: [String: [TimetableDay]], fresh: StudentData) {
        since = old.fetchedAt
        at = fresh.fetchedAt

        let oldGrades = Set(old.grades.map(\.id))
        newGradeIDs = Set(fresh.grades.map(\.id).filter { !oldGrades.contains($0) })

        let oldEvents = Dictionary(old.events.map { ($0.id, $0) }) { a, _ in a }
        var added = Set<Int>()
        var changed = Set<Int>()
        for event in fresh.events {
            if let before = oldEvents[event.id] {
                if before.day != event.day || before.topic != event.topic
                    || before.time != event.time || before.lessonNumber != event.lessonNumber {
                    changed.insert(event.id)
                }
            } else {
                added.insert(event.id)
            }
        }
        newEventIDs = added
        changedEventIDs = changed

        let oldAnnouncements = Set(old.announcements.map(\.id))
        newAnnouncementIDs = Set(fresh.announcements.map(\.id).filter { !oldAnnouncements.contains($0) })

        var lessons: [Lesson] = []
        for (week, days) in fresh.timetable {
            guard let before = oldTimetables[week] else { continue }
            let known = Set(before.flatMap { $0.lessons.map(\.id) })
            lessons += days.flatMap(\.lessons).filter { $0.kind != .normal && !known.contains($0.id) }
        }
        lessonChanges = lessons.sorted { ($0.day, $0.number) < ($1.day, $1.number) }
    }

    var count: Int {
        newGradeIDs.count + newEventIDs.count + changedEventIDs.count + newAnnouncementIDs.count + lessonChanges.count
    }

    /// Events to mark as new in the lists.
    var highlightedEventIDs: Set<Int> { newEventIDs.union(changedEventIDs) }
}

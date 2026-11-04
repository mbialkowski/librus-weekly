import Foundation
import LibrusKit

struct NewGrade: Identifiable {
    let grade: Grade
    let subject: String?
    var id: Int { grade.id }
}

/// A calendar event with the hours and classroom of the lesson it takes place in, when known.
struct EventItem: Identifiable {
    let event: AgendaEvent
    let start: String?
    let end: String?
    let classroom: String?
    let lessonNumber: Int?
    var id: Int { event.id }

    /// Homework has no lesson number, so it goes with that day's first lesson of its subject.
    init(_ event: AgendaEvent, lessons: [Lesson]) {
        self.event = event
        let lesson: Lesson?
        if let number = event.lessonNumber {
            lesson = lessons.first { $0.number == number && $0.kind != .shiftedSource }
        } else if let subject = event.subject {
            lesson = lessons.first { $0.subject == subject && $0.kind != .shiftedSource && $0.kind != .cancelled }
        } else {
            lesson = nil
        }
        lessonNumber = event.lessonNumber ?? lesson?.number
        if let lesson, event.time == nil || event.time == lesson.start {
            start = lesson.start
            end = lesson.end
        } else {
            start = event.time
            end = nil
        }
        classroom = lesson?.classroom
    }
}

struct DaySummary: Identifiable {
    let day: Day
    let lessons: [Lesson]
    let events: [EventItem]
    var id: String { day.string }
}

/// One Monday–Sunday week: Mon–Fri always, weekend days only when something is on.
struct WeekSummary {
    let start: Day
    let end: Day
    /// False while the week's timetable hasn't been downloaded yet.
    let hasTimetable: Bool
    let days: [DaySummary]
    /// Grades added during this week, newest first.
    let grades: [NewGrade]
    let cancelled: [Lesson]
    /// Events teachers added to the calendar during this week, whatever day they are on.
    let addedEvents: [EventItem]
    let announcements: [Announcement]
    let messages: [MessageSummary]
    let luckyNumber: LuckyNumber?

    init(start: Day, data: StudentData, timetable: [TimetableDay]?) {
        let end = start.adding(days: 6)
        self.start = start
        self.end = end
        hasTimetable = timetable != nil

        let lessonsByDay = Dictionary((timetable ?? []).map { ($0.day, $0.lessons) }) { a, _ in a }
        let events = data.events.filter { $0.day >= start && $0.day <= end }
        var days: [DaySummary] = []
        for offset in 0..<7 {
            let day = start.adding(days: offset)
            let lessons = lessonsByDay[day] ?? []
            let dayEvents = events
                .filter { $0.day == day }
                .sorted { ($0.lessonNumber ?? 0, $0.time ?? "") < ($1.lessonNumber ?? 0, $1.time ?? "") }
                .map { EventItem($0, lessons: lessons) }
            if offset < 5 || !lessons.isEmpty || !dayEvents.isEmpty {
                days.append(DaySummary(day: day, lessons: lessons, events: dayEvents))
            }
        }
        self.days = days
        cancelled = days.flatMap { $0.lessons.filter { $0.kind == .cancelled } }

        addedEvents = data.events
            .filter { event in
                guard let added = event.addedDate else { return false }
                let day = Day(date: added)
                return day >= start && day <= end
            }
            .sorted { ($0.day, $0.lessonNumber ?? 0) < ($1.day, $1.lessonNumber ?? 0) }
            .map { EventItem($0, lessons: lessonsByDay[$0.day] ?? []) }

        let subjects = Dictionary(data.subjects.map { ($0.id, $0.name) }) { a, _ in a }
        grades = data.grades
            .compactMap { grade -> (Grade, Date)? in
                guard let added = grade.addedDate else { return nil }
                let day = Day(date: added)
                return day >= start && day <= end ? (grade, added) : nil
            }
            .sorted { $0.1 > $1.1 }
            .map { NewGrade(grade: $0.0, subject: subjects[$0.0.subjectID]) }

        let cutoff = Day.today.adding(days: -30)
        announcements = data.announcements.filter { a in
            (a.startDay.map { $0 <= end } ?? true)
                && (a.endDay.map { $0 >= start } ?? true)
                && (a.startDay.map { $0 >= cutoff } ?? true)
        }
        messages = data.messages.filter { m in
            guard let day = Day(String(m.date.prefix(10))) else { return true }
            return day >= cutoff
        }
        luckyNumber = data.luckyNumber.flatMap { $0.day >= start && $0.day <= end ? $0 : nil }
    }

    var eventCount: Int { days.reduce(0) { $0 + $1.events.count } }
    var otherChangeCount: Int {
        days.reduce(0) { $0 + $1.lessons.filter { $0.kind != .normal && $0.kind != .cancelled }.count }
    }
}

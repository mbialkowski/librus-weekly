package com.example.librusweekly.state

import com.example.librusweekly.librus.models.*
import java.util.Date

/// A calendar event with the hours and classroom of the lesson it takes place in, when known.
data class EventItem(
    val event: AgendaEvent,
    val start: String?,
    val end: String?,
    val classroom: String?,
    val lessonNumber: Int?
) {
    val id: Int get() = event.id

    companion object {
        /// Homework has no lesson number, so it goes with that day's first lesson of its subject.
        fun from(event: AgendaEvent, lessons: List<Lesson>): EventItem {
            val lesson = when {
                event.lessonNumber != null ->
                    lessons.firstOrNull { it.number == event.lessonNumber && it.kind != LessonKind.SHIFTED_SOURCE }
                event.subject != null ->
                    lessons.firstOrNull { it.subject == event.subject && it.kind != LessonKind.SHIFTED_SOURCE && it.kind != LessonKind.CANCELLED }
                else -> null
            }
            val sameTime = lesson != null && (event.time == null || event.time == lesson.start)
            return EventItem(
                event = event,
                start = if (sameTime) lesson?.start else event.time,
                end = if (sameTime) lesson?.end else null,
                classroom = lesson?.classroom,
                lessonNumber = event.lessonNumber ?: lesson?.number
            )
        }
    }
}

data class DaySummary(val day: Day, val lessons: List<Lesson>, val events: List<EventItem>)

data class NewGrade(val grade: Grade, val subject: String?) { val id: Int get() = grade.id }

/// One Monday–Sunday week, derived from StudentData.
class WeekSummary(start: Day, data: StudentData, timetable: List<TimetableDay>?) {
    val start: Day = start
    val end: Day = start.adding(6)
    val hasTimetable: Boolean = timetable != null
    val days: List<DaySummary>
    val grades: List<NewGrade>
    val cancelled: List<Lesson>
    val addedEvents: List<EventItem>
    val announcements: List<Announcement>
    val messages: List<com.example.librusweekly.librus.synergia.MessageSummary>
    val luckyNumber: LuckyNumber?

    init {
        val lessonsByDay = (timetable ?: emptyList()).associate { it.day to it.lessons }
        val weekEvents = data.events.filter { it.day >= start && it.day <= end }
        val builtDays = mutableListOf<DaySummary>()
        for (offset in 0..6) {
            val day = start.adding(offset)
            val lessons = lessonsByDay[day] ?: emptyList()
            val dayEvents = weekEvents.filter { it.day == day }
                .sortedWith(compareBy({ it.lessonNumber ?: 0 }, { it.time.orEmpty() }))
                .map { EventItem.from(it, lessons) }
            if (offset < 5 || lessons.isNotEmpty() || dayEvents.isNotEmpty())
                builtDays.add(DaySummary(day, lessons, dayEvents))
        }
        days = builtDays
        cancelled = days.flatMap { it.lessons.filter { l -> l.kind == LessonKind.CANCELLED } }

        addedEvents = data.events
            .filter { event ->
                val added = event.addedDate ?: return@filter false
                val day = Day.of(added)
                day >= start && day <= end
            }
            .sortedWith(compareBy({ it.day }, { it.lessonNumber ?: 0 }))
            .map { EventItem.from(it, lessonsByDay[it.day] ?: emptyList()) }

        val subjects = data.subjects.associate { it.id to it.name }
        grades = data.grades
            .mapNotNull { g ->
                val added = g.addedDate ?: return@mapNotNull null
                val day = Day.of(added)
                if (day >= start && day <= end) g to added else null
            }
            .sortedByDescending { it.second }
            .map { NewGrade(it.first, subjects[it.first.subjectId]) }

        val cutoff = Day.today.adding(-30)
        announcements = data.announcements.filter { a ->
            (a.startDay == null || a.startDay <= end) &&
                (a.endDay == null || a.endDay >= start) &&
                (a.startDay == null || a.startDay >= cutoff)
        }
        messages = data.messages.filter { m ->
            val day = Day.of(m.date.take(10)) ?: return@filter true
            day >= cutoff
        }
        luckyNumber = data.luckyNumber?.takeIf { it.day >= start && it.day <= end }
    }

    val eventCount: Int get() = days.sumOf { it.events.size }
    val otherChangeCount: Int get() = days.sumOf {
        it.lessons.count { l -> l.kind != LessonKind.NORMAL && l.kind != LessonKind.CANCELLED }
    }
}

/// Polish plural agreement — "1 ocena", "2 oceny", "5 ocen".
fun pl(n: Int, one: String, few: String, many: String): String {
    val last = n % 10
    val lastTwo = n % 100
    return when {
        n == 1 -> "1 $one"
        last in 2..4 && lastTwo !in 12..14 -> "$n $few"
        else -> "$n $many"
    }
}

/// Convert a Date to a local `Day`, through the Warsaw calendar.
fun Day.Companion.of(date: Date): Day = Day(Day.parser.format(date))

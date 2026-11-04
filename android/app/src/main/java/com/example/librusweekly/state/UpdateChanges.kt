package com.example.librusweekly.state

import com.example.librusweekly.librus.models.Lesson
import com.example.librusweekly.librus.models.LessonKind
import com.example.librusweekly.librus.models.StudentData
import com.example.librusweekly.librus.models.TimetableDay
import java.util.Date

/// What the latest refresh brought, compared to the data cached before it.
data class UpdateChanges(
    val since: Date,
    val at: Date,
    val newGradeIds: Set<Int>,
    val newEventIds: Set<Int>,
    val changedEventIds: Set<Int>,
    val newAnnouncementIds: Set<String>,
    val lessonChanges: List<Lesson>
) {
    val count: Int
        get() = newGradeIds.size + newEventIds.size + changedEventIds.size +
            newAnnouncementIds.size + lessonChanges.size

    /// Events that should show the NEW badge (both newly added and ones whose details moved).
    val highlightedEventIds: Set<Int> get() = newEventIds + changedEventIds

    companion object {
        fun diff(old: StudentData, oldTimetables: Map<String, List<TimetableDay>>, fresh: StudentData): UpdateChanges {
            val oldGradeIds = old.grades.map { it.id }.toSet()
            val newGrades = fresh.grades.map { it.id }.filter { it !in oldGradeIds }.toSet()

            val oldEvents = old.events.associateBy { it.id }
            val added = mutableSetOf<Int>()
            val changed = mutableSetOf<Int>()
            for (event in fresh.events) {
                val before = oldEvents[event.id]
                if (before == null) {
                    added.add(event.id)
                } else if (before.day != event.day || before.topic != event.topic
                        || before.time != event.time || before.lessonNumber != event.lessonNumber) {
                    changed.add(event.id)
                }
            }

            val oldAnns = old.announcements.map { it.id }.toSet()
            val newAnns = fresh.announcements.map { it.id }.filter { it !in oldAnns }.toSet()

            val lessons = mutableListOf<Lesson>()
            for ((week, days) in fresh.timetable) {
                val before = oldTimetables[week] ?: continue
                val known = before.flatMap { it.lessons.map { l -> l.id } }.toSet()
                lessons += days.flatMap { it.lessons }
                    .filter { it.kind != LessonKind.NORMAL && it.id !in known }
            }
            return UpdateChanges(
                since = old.fetchedAt,
                at = fresh.fetchedAt,
                newGradeIds = newGrades,
                newEventIds = added,
                changedEventIds = changed,
                newAnnouncementIds = newAnns,
                lessonChanges = lessons.sortedWith(compareBy({ it.day }, { it.number }))
            )
        }
    }
}

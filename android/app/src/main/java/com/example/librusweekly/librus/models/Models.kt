package com.example.librusweekly.librus.models

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/// School days are Warsaw calendar dates; format them in that zone so they never shift.
data class Day(val string: String) : Comparable<Day> {
    val date: Date get() = parser.parse(string) ?: Date()
    override fun compareTo(other: Day): Int = string.compareTo(other.string)
    override fun toString(): String = string

    fun adding(days: Int): Day {
        val cal = warsawCalendar().apply { time = date; add(Calendar.DATE, days) }
        return Day(parser.format(cal.time))
    }

    /// Monday of this day's week.
    val weekStart: Day
        get() {
            val cal = warsawCalendar().apply { time = date }
            val weekday = cal.get(Calendar.DAY_OF_WEEK)  // Sunday=1 .. Saturday=7
            return adding(-(((weekday + 5) % 7)))
        }

    companion object {
        private val tz: TimeZone = TimeZone.getTimeZone("Europe/Warsaw")
        private val polish: Locale = Locale("pl", "PL")

        val parser: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale("en", "US_POSIX")).apply {
            timeZone = tz
            isLenient = false
        }
        val dateTimeParser: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("en", "US_POSIX")).apply {
            timeZone = tz
            isLenient = false
        }

        fun of(string: String?): Day? {
            if (string.isNullOrEmpty()) return null
            val head = string.take(10)
            return try { parser.parse(head)?.let { Day(head) } } catch (e: Exception) { null }
        }

        fun of(date: Date): Day = Day(parser.format(date))

        val today: Day get() = of(Date())

        /// The current week, or the coming week on weekends (like Android upstream).
        val defaultTimetableWeek: Day
            get() {
                val cal = warsawCalendar()
                val weekday = cal.get(Calendar.DAY_OF_WEEK)
                return if (weekday == 7 || weekday == 1) today.weekStart.adding(7) else today.weekStart
            }

        fun warsawCalendar(): Calendar = GregorianCalendar(tz, polish).apply {
            firstDayOfWeek = Calendar.MONDAY
        }

        fun parseDateTime(s: String?): Date? {
            if (s.isNullOrEmpty()) return null
            return try { dateTimeParser.parse(s.take(19)) }
            catch (e: Exception) {
                try { parser.parse(s.take(10)) } catch (ex: Exception) { null }
            }
        }
    }
}

data class Subject(val id: Int, val name: String, val short: String)

data class Teacher(val id: Int, val firstName: String, val lastName: String) {
    val fullName: String get() = "$firstName $lastName".trim()
}

enum class GradeKind {
    NORMAL, SEMESTER_PROPOSED, SEMESTER_FINAL, YEAR_PROPOSED, YEAR_FINAL;

    val label: String
        get() = when (this) {
            NORMAL -> "Ocena"
            SEMESTER_PROPOSED -> "Przewidywana śródroczna"
            SEMESTER_FINAL -> "Śródroczna"
            YEAR_PROPOSED -> "Przewidywana roczna"
            YEAR_FINAL -> "Roczna"
        }
}

data class Grade(
    val id: Int,
    val name: String,
    val kind: GradeKind,
    val value: Double?,
    var weight: Double,
    val category: String,
    val colorHex: String?,
    val comment: String?,
    val semester: Int,
    val teacherName: String?,
    val subjectId: Int,
    val addedDate: Date?,
    val isImprovement: Boolean,
    var improvedById: Int? = null
) {
    val countsToAverage: Boolean get() = kind == GradeKind.NORMAL && weight > 0 && value != null
}

enum class LessonKind { NORMAL, CANCELLED, CHANGE, SHIFTED_SOURCE, SHIFTED_TARGET }

data class Lesson(
    val day: Day,
    val number: Int,
    val start: String,
    val end: String,
    val kind: LessonKind,
    val subject: String?,
    val teacher: String?,
    val classroom: String?,
    val oldSubject: String? = null,
    val oldTeacher: String? = null,
    val otherDay: Day? = null,
    val otherNumber: Int? = null
) {
    val id: String get() = "$day-$number-${kind.name}-${subject.orEmpty()}"
}

data class TimetableDay(val day: Day, val lessons: List<Lesson>)

data class AgendaEvent(
    val id: Int,
    val day: Day,
    val time: String?,
    val lessonNumber: Int?,
    val topic: String,
    val category: String,
    val colorHex: String?,
    val subject: String?,
    val teacher: String?,
    val addedDate: Date?
)

enum class AttendanceBase { PRESENT, PRESENT_CUSTOM, ABSENT, ABSENT_EXCUSED, BELATED, RELEASED, UNKNOWN;
    val isAbsence: Boolean get() = this == ABSENT || this == ABSENT_EXCUSED
}

data class AttendanceEntry(
    val id: String,
    val day: Day,
    val lessonNumber: Int,
    val typeName: String,
    val typeShort: String,
    val base: AttendanceBase,
    val colorHex: String?,
    val subject: String?,
    val teacher: String?,
    val semester: Int
)

enum class NoticeKind { POSITIVE, NEGATIVE, NEUTRAL }

data class Notice(
    val id: Int,
    val day: Day,
    val kind: NoticeKind,
    val category: String,
    val text: String,
    val teacher: String?
)

data class Announcement(
    val id: String,
    val subject: String,
    val text: String,
    val startDay: Day?,
    val endDay: Day?,
    val author: String?,
    val read: Boolean
)

data class LuckyNumber(val day: Day, val number: Int)

/// Everything fetched for one student.
data class StudentData(
    var fetchedAt: Date = Date(),
    var studentName: String? = null,
    var accountName: String? = null,
    var subjects: List<Subject> = emptyList(),
    var grades: List<Grade> = emptyList(),
    var events: MutableList<AgendaEvent> = mutableListOf(),
    var attendance: List<AttendanceEntry> = emptyList(),
    var notices: List<Notice> = emptyList(),
    var announcements: List<Announcement> = emptyList(),
    var messages: List<com.example.librusweekly.librus.synergia.MessageSummary> = emptyList(),
    var luckyNumber: LuckyNumber? = null,
    var timetable: MutableMap<String, List<TimetableDay>> = mutableMapOf(),
    var warnings: MutableList<String> = mutableListOf()
)

/// Fixes "KOWALSKI JAN" / "jan kowalski" → "Jan Kowalski".
fun String.fixedName(): String = lowercase().split(' ').filter { it.isNotEmpty() }
    .joinToString(" ") { word ->
        word.split('-').joinToString("-") { seg ->
            if (seg.isEmpty()) "" else seg.substring(0, 1).uppercase() + seg.substring(1)
        }
    }

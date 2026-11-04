package com.example.librusweekly.librus.api

import com.example.librusweekly.librus.core.Json
import com.example.librusweekly.librus.models.*

/// Pure JSON → model mapping. Kept free of networking so it can be checked against fixtures.
object LibrusParsers {
    class Dictionaries {
        var subjects: Map<Int, Subject> = emptyMap()
        var teachers: Map<Int, Teacher> = emptyMap()
        var classrooms: Map<Int, String> = emptyMap()
        var gradeCategories: Map<Int, GradeCategory> = emptyMap()
        var gradeComments: Map<Int, String> = emptyMap()
        var eventCategories: Map<Int, EventCategory> = emptyMap()
        var attendanceTypes: Map<Int, AttendanceType> = emptyMap()
        var noticeCategories: Map<Int, String> = emptyMap()
        var lessons: Map<Int, LessonRef> = emptyMap()

        fun subjectName(id: Int?): String? = id?.let { subjects[it]?.name }
        fun teacherName(id: Int?): String? = id?.let { teachers[it]?.fullName }
    }

    data class GradeCategory(val name: String, val weight: Double, val colorHex: String?)
    data class EventCategory(val name: String, val colorHex: String?)
    data class AttendanceType(val name: String, val short: String, val base: AttendanceBase, val colorHex: String?)
    data class LessonRef(val subjectId: Int?, val teacherId: Int?)

    // Dictionaries

    fun subjects(json: Json): Map<Int, Subject> = buildMap {
        for (s in json["Subjects"].array) {
            val id = s["Id"].int ?: continue
            put(id, Subject(id, s["Name"].string.orEmpty(), s["Short"].string.orEmpty()))
        }
        if (!containsKey(1)) put(1, Subject(1, "Zachowanie", "zach"))
    }

    fun teachers(json: Json): Map<Int, Teacher> = buildMap {
        for (u in json["Users"].array) {
            val id = u["Id"].int ?: continue
            put(id, Teacher(id,
                (u["FirstName"].string.orEmpty()).fixedName(),
                (u["LastName"].string.orEmpty()).fixedName()))
        }
    }

    fun classrooms(json: Json): Map<Int, String> = buildMap {
        for (c in json["Classrooms"].array) {
            val id = c["Id"].int ?: continue
            val name = c["Name"].string.orEmpty()
            val symbol = c["Symbol"].string.orEmpty()
            put(id, if (symbol.isEmpty() || symbol.lowercase() in name.lowercase()) name else "$symbol $name")
        }
    }

    fun gradeCategories(json: Json): Map<Int, GradeCategory> = buildMap {
        for (c in json["Categories"].array) {
            val id = c["Id"].int ?: continue
            val counts = c["CountToTheAverage"].bool == true
            val weight = if (counts) c["Weight"].double ?: 0.0 else 0.0
            put(id, GradeCategory(
                (c["Name"].string.orEmpty()).trim(),
                weight,
                librusColor(c["Color"].refId)
            ))
        }
    }

    fun gradeComments(json: Json): Map<Int, String> = buildMap {
        for (c in json["Comments"].array) {
            val id = c["Id"].int ?: continue
            val text = c["Text"].string ?: continue
            put(id, text.trim())
        }
    }

    fun eventCategories(json: Json): Map<Int, EventCategory> = buildMap {
        for (c in json["Categories"].array) {
            val id = c["Id"].int ?: continue
            put(id, EventCategory(c["Name"].string.orEmpty(), librusColor(c["Color"].refId)))
        }
    }

    fun attendanceTypes(json: Json): Map<Int, AttendanceType> = buildMap {
        for (t in json["Types"].array) {
            val id = t["Id"].int ?: continue
            val standard = t["Standard"].bool ?: false
            val base = when (t["StandardType"].refId ?: id) {
                1 -> AttendanceBase.ABSENT
                2 -> AttendanceBase.BELATED
                3 -> AttendanceBase.ABSENT_EXCUSED
                4 -> AttendanceBase.RELEASED
                else -> if (standard) AttendanceBase.PRESENT else AttendanceBase.PRESENT_CUSTOM
            }
            put(id, AttendanceType(
                t["Name"].string.orEmpty(),
                t["Short"].string ?: "?",
                base,
                t["ColorRGB"].string?.let { "#$it" }
            ))
        }
    }

    fun noticeCategories(json: Json): Map<Int, String> = buildMap {
        for (c in json["Categories"].array) {
            val id = c["Id"].int ?: continue
            put(id, c["CategoryName"].string.orEmpty())
        }
    }

    fun lessons(json: Json): Map<Int, LessonRef> = buildMap {
        for (l in json["Lessons"].array) {
            val id = l["Id"].int ?: continue
            put(id, LessonRef(l["Subject"].refId, l["Teacher"].refId))
        }
    }

    // Data

    fun grades(json: Json, dict: Dictionaries): List<Grade> {
        val improvements = mutableMapOf<Int, Int>() // old grade id → new grade id
        val built = json["Grades"].array.mapNotNull { g ->
            val id = g["Id"].int ?: return@mapNotNull null
            val semester = g["Semester"].int ?: return@mapNotNull null
            val name = g["Grade"].string.orEmpty()
            val category = g["Category"].refId?.let { dict.gradeCategories[it] }
            val kind = when {
                g["IsConstituent"].bool == true -> GradeKind.NORMAL
                g["IsSemester"].bool == true -> GradeKind.SEMESTER_FINAL
                g["IsSemesterProposition"].bool == true -> GradeKind.SEMESTER_PROPOSED
                g["IsFinal"].bool == true -> GradeKind.YEAR_FINAL
                g["IsFinalProposition"].bool == true -> GradeKind.YEAR_PROPOSED
                else -> GradeKind.NORMAL
            }
            val value = GradeMath.valueOf(name)
            val zeroWeight = name.lowercase() in listOf("-", "+", "np", "bz")
            val comment = g["Comments"].array.firstOrNull()?.refId?.let { dict.gradeComments[it] }
            g["Improvement"].refId?.let { oldId -> improvements[oldId] = id }
            Grade(
                id = id,
                name = name,
                kind = kind,
                value = value,
                weight = if (zeroWeight) 0.0 else (category?.weight ?: 0.0),
                category = category?.name ?: "",
                colorHex = category?.colorHex,
                comment = comment,
                semester = semester,
                teacherName = dict.teacherName(g["AddedBy"].refId),
                subjectId = g["Subject"].refId ?: -1,
                addedDate = Day.parseDateTime(g["AddDate"].string),
                isImprovement = !g["Improvement"].isNull
            )
        }
        return built.map { grade ->
            grade.improvedById = improvements[grade.id]
            if (grade.improvedById != null && grade.name.lowercase() == "nb") grade.weight = 0.0
            grade
        }
    }

    fun timetable(json: Json, dict: Dictionaries): List<TimetableDay> {
        val days = json["Timetable"].obj ?: return emptyList()
        return days.mapNotNull { (dateString, dayJson) ->
            val day = Day.of(dateString) ?: return@mapNotNull null
            val lessons = dayJson.array.flatMap { it.array }.mapNotNull { lesson(day, it, dict) }
            TimetableDay(day, lessons.sortedWith(compareBy({ it.number }, { it.start })))
        }.sortedBy { it.day }
    }

    private fun lesson(day: Day, l: Json, dict: Dictionaries): Lesson? {
        val number = l["LessonNo"].int ?: return null
        val start = l["HourFrom"].string ?: return null
        val end = l["HourTo"].string ?: return null
        val isSubstitution = l["IsSubstitutionClass"].bool ?: false
        val isCancelled = l["IsCanceled"].bool ?: false
        val subject = l["Subject"]["Name"].string ?: dict.subjectName(l["Subject"].refId)
        val teacher = dict.teacherName(l["Teacher"].refId)
            ?: listOfNotNull(l["Teacher"]["FirstName"].string, l["Teacher"]["LastName"].string)
                .joinToString(" ").ifBlank { null }
        val classroom = l["Classroom"].refId?.let { dict.classrooms[it] }

        if (isSubstitution && isCancelled) {
            return Lesson(day, number, start, end, LessonKind.SHIFTED_SOURCE,
                subject, teacher, classroom,
                otherDay = Day.of(l["NewDate"].string), otherNumber = l["NewLessonNo"].int)
        }
        if (isSubstitution) {
            val orgDay = Day.of(l["OrgDate"].string)
            val orgNumber = l["OrgLessonNo"].int
            val moved = orgDay != null && (orgDay != day || orgNumber != number)
            return Lesson(day, number, start, end,
                if (moved) LessonKind.SHIFTED_TARGET else LessonKind.CHANGE,
                subject, teacher, classroom,
                oldSubject = dict.subjectName(l["OrgSubject"].refId),
                oldTeacher = dict.teacherName(l["OrgTeacher"].refId),
                otherDay = if (moved) orgDay else null,
                otherNumber = if (moved) orgNumber else null)
        }
        return Lesson(day, number, start, end,
            if (isCancelled) LessonKind.CANCELLED else LessonKind.NORMAL,
            subject, teacher, classroom)
    }

    /// Parent-teacher conferences (zebrania / wywiadówki / konsultacje) come from a separate
    /// endpoint than homework/calendar events. Ids are offset to avoid clashing with HomeWorks.
    fun parentTeacherConferences(json: Json, dict: Dictionaries): List<AgendaEvent> =
        json["ParentTeacherConferences"].array.mapNotNull { c ->
            val rawId = c["Id"].int ?: return@mapNotNull null
            val day = Day.of(c["Date"].string) ?: return@mapNotNull null
            val name = (c["Name"].string ?: "Zebranie").trim()
            val topicBase = (c["Topic"].string ?: "").trim()
            val room = c["Room"].string?.trim()?.ifEmpty { null }
            val topic = when {
                room != null && topicBase.isEmpty() -> "Sala $room"
                room != null -> "$topicBase\n\nSala $room"
                else -> topicBase
            }
            AgendaEvent(
                id = rawId + 10_000_000,
                day = day,
                time = c["Time"].string?.take(5),
                lessonNumber = null,
                topic = topic,
                category = name,
                colorHex = "#BA55D3",
                subject = null,
                teacher = dict.teacherName(c["Teacher"].refId),
                addedDate = null
            )
        }

    fun events(json: Json, dict: Dictionaries): List<AgendaEvent> =
        json["HomeWorks"].array.mapNotNull { e ->
            val id = e["Id"].int ?: return@mapNotNull null
            val day = Day.of(e["Date"].string) ?: return@mapNotNull null
            var topic = (e["Content"].string.orEmpty()).trim()
            val online = e["onlineLessonUrl"]
            online["url"].string?.let { url ->
                val prefix = online["text"].string?.let { "$it - " } ?: ""
                topic += "\n\n$prefix$url"
            }
            val category = e["Category"].refId?.let { dict.eventCategories[it] }
            AgendaEvent(
                id = id, day = day,
                time = e["TimeFrom"].string?.take(5),
                lessonNumber = e["LessonNo"].int,
                topic = topic,
                category = category?.name ?: "Wydarzenie",
                colorHex = category?.colorHex,
                subject = dict.subjectName(e["Subject"].refId),
                teacher = dict.teacherName(e["CreatedBy"].refId),
                addedDate = Day.parseDateTime(e["AddDate"].string)
            )
        }.sortedBy { it.day }

    fun attendance(json: Json, dict: Dictionaries): List<AttendanceEntry> =
        json["Attendances"].array.mapNotNull { a ->
            val rawId = a["Id"].string ?: return@mapNotNull null
            val number = a["LessonNo"].int ?: return@mapNotNull null
            val day = Day.of(a["Date"].string) ?: return@mapNotNull null
            val semester = a["Semester"].int ?: return@mapNotNull null
            val typeId = a["Type"].refId ?: return@mapNotNull null
            val type = dict.attendanceTypes[typeId]
            val lesson = a["Lesson"].refId?.let { dict.lessons[it] }
            AttendanceEntry(
                id = rawId, day = day, lessonNumber = number,
                typeName = type?.name ?: "nieznany rodzaj",
                typeShort = type?.short ?: "?",
                base = type?.base ?: AttendanceBase.UNKNOWN,
                colorHex = type?.colorHex,
                subject = dict.subjectName(lesson?.subjectId),
                teacher = dict.teacherName(a["AddedBy"].refId ?: lesson?.teacherId),
                semester = semester
            )
        }.sortedWith(compareByDescending<AttendanceEntry> { it.day }.thenByDescending { it.lessonNumber })

    fun notices(json: Json, dict: Dictionaries): List<Notice> =
        json["Notes"].array.mapNotNull { n ->
            val id = n["Id"].int ?: return@mapNotNull null
            val day = Day.of(n["Date"].string) ?: return@mapNotNull null
            val kind = when (n["Positive"].int) {
                0 -> NoticeKind.NEGATIVE
                1 -> NoticeKind.POSITIVE
                else -> NoticeKind.NEUTRAL
            }
            Notice(id, day, kind,
                n["Category"].refId?.let { dict.noticeCategories[it] } ?: "",
                n["Text"].string.orEmpty(),
                dict.teacherName(n["Teacher"].refId))
        }.sortedByDescending { it.day }

    fun announcements(json: Json, dict: Dictionaries): List<Announcement> =
        json["SchoolNotices"].array.mapNotNull { a ->
            val id = a["Id"].string ?: return@mapNotNull null
            Announcement(
                id, a["Subject"].string.orEmpty(), a["Content"].string.orEmpty(),
                Day.of(a["StartDate"].string), Day.of(a["EndDate"].string),
                dict.teacherName(a["AddedBy"].refId),
                a["WasRead"].bool ?: false
            )
        }.sortedByDescending { it.startDay?.string.orEmpty() }

    fun luckyNumber(json: Json): LuckyNumber? {
        val l = json["LuckyNumber"]
        val number = l["LuckyNumber"].int ?: return null
        val day = Day.of(l["LuckyNumberDay"].string) ?: return null
        return LuckyNumber(day, number)
    }

    /// Librus color ids → hex (table from `DataLibrus.getColor`).
    fun librusColor(id: Int?): String? {
        val table = mapOf(
            1 to "#F0E68C", 2 to "#87CEFA", 3 to "#B0C4DE", 4 to "#F0F8FF", 5 to "#F0FFFF", 6 to "#F5F5DC",
            7 to "#FFEBCD", 8 to "#FFF8DC", 9 to "#A9A9A9", 10 to "#BDB76B", 11 to "#8FBC8F", 12 to "#DCDCDC",
            13 to "#DAA520", 14 to "#E6E6FA", 15 to "#FFA07A", 16 to "#32CD32", 17 to "#66CDAA", 18 to "#66CDAA",
            19 to "#C0C0C0", 20 to "#D2B48C", 21 to "#3333FF", 22 to "#7B68EE", 23 to "#BA55D3", 24 to "#FFB6C1",
            25 to "#FF1493", 26 to "#DC143C", 27 to "#FF0000", 28 to "#FF8C00", 29 to "#FFD700", 30 to "#ADFF2F",
            31 to "#7CFC00"
        )
        return id?.let { table[it] } ?: "#2196F3"
    }
}

object GradeMath {
    var plusValue = 0.5
    var minusValue = 0.25

    /// "4+" → 4.5, "3-" → 2.75, "np" → null.
    fun valueOf(grade: String): Double? {
        val trimmed = grade.trim()
        val first = trimmed.firstOrNull() ?: return null
        val base = first.digitToIntOrNull()?.takeIf { it in 0..6 } ?: return null
        val rest = trimmed.drop(1)
        return when {
            rest.isEmpty() -> base.toDouble()
            rest == "+" -> base + plusValue
            rest == "-" -> base - minusValue
            else -> trimmed.replace(',', '.').toDoubleOrNull() ?: base.toDouble()
        }
    }

    /// Weighted average of grades that count (normal grades with weight > 0).
    fun average(grades: List<Grade>): Double? {
        val counted = grades.filter { it.countsToAverage }
        val weights = counted.sumOf { it.weight }
        if (weights <= 0) return null
        return counted.sumOf { (it.value ?: 0.0) * it.weight } / weights
    }
}

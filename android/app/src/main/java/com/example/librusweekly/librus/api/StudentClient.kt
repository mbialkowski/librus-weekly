package com.example.librusweekly.librus.api

import com.example.librusweekly.librus.auth.StoredAccount
import com.example.librusweekly.librus.core.Json
import com.example.librusweekly.librus.core.LibrusError
import com.example.librusweekly.librus.models.*
import com.example.librusweekly.librus.synergia.CredentialSynergiaSession
import com.example.librusweekly.librus.synergia.SynergiaHomework
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Date
import java.util.UUID

/// Everything the app does for one student: full sync, single-week timetable, homework.
class StudentClient(val profile: StoredAccount) {
    val session = CredentialSynergiaSession(profile.synergiaLogin, profile.synergiaPassword)
    val api = LibrusApi(session)
    val homework = SynergiaHomework(session)
    private var dict = LibrusParsers.Dictionaries()

    /// Full refresh. Individual sections may fail (e.g. lucky number disabled in a school);
    /// those are reported in `warnings` instead of failing the whole sync.
    suspend fun sync(weekStart: Day = Day.defaultTimetableWeek): StudentData = coroutineScope {
        val warnings = mutableListOf<String>()
        // Fail fast on auth problems before firing many requests.
        val me = api.get("Me")["Me"]

        val subjects    = optional("Przedmioty")       { LibrusParsers.subjects(api.get("Subjects")) }
        val teachers    = optional("Nauczyciele")      { LibrusParsers.teachers(api.get("Users")) }
        val classrooms  = optional("Sale")             { LibrusParsers.classrooms(api.get("Classrooms")) }
        val gradeCats   = optional("Kategorie ocen")   { LibrusParsers.gradeCategories(api.get("Grades/Categories")) }
        val gradeCmts   = optional("Komentarze ocen")  { LibrusParsers.gradeComments(api.get("Grades/Comments")) }
        val eventCats   = optional("Kategorie wydarzeń") { LibrusParsers.eventCategories(api.get("HomeWorks/Categories")) }
        val attendTypes = optional("Typy frekwencji")  { LibrusParsers.attendanceTypes(api.get("Attendances/Types")) }
        val noticeCats  = optional("Kategorie uwag")   { LibrusParsers.noticeCategories(api.get("Notes/Categories")) }
        val lessons     = optional("Lekcje")           { LibrusParsers.lessons(api.get("Lessons")) }

        val gradesJson    = async { optional("Oceny")                { api.get("Grades") } }
        val eventsJson    = async { optional("Terminarz")            { api.get("HomeWorks") } }
        val attendanceJson = async { optional("Frekwencja")          { api.get("Attendances") } }
        val notesJson     = async { optional("Uwagi")                { api.get("Notes") } }
        val noticesJson   = async { optional("Ogłoszenia")           { api.get("SchoolNotices") } }
        val luckyJson     = async { optional("Szczęśliwy numerek")   { api.get("LuckyNumbers") } }
        val timetableJson = async { optional("Plan lekcji")          { api.get("Timetables?weekStart=$weekStart") } }

        val d = LibrusParsers.Dictionaries().apply {
            this.subjects = collect(subjects, warnings) ?: emptyMap()
            this.teachers = collect(teachers, warnings) ?: emptyMap()
            this.classrooms = collect(classrooms, warnings) ?: emptyMap()
            this.gradeCategories = collect(gradeCats, warnings) ?: emptyMap()
            this.gradeComments = collect(gradeCmts, warnings) ?: emptyMap()
            this.eventCategories = collect(eventCats, warnings) ?: emptyMap()
            this.attendanceTypes = collect(attendTypes, warnings) ?: emptyMap()
            this.noticeCategories = collect(noticeCats, warnings) ?: emptyMap()
            this.lessons = collect(lessons, warnings) ?: emptyMap()
        }
        dict = d

        val data = StudentData()
        val account = me["Account"]
        val user = me["User"]
        data.studentName = listOfNotNull(user["FirstName"].string, user["LastName"].string)
            .joinToString(" ").fixedName().ifBlank { null }
        if (account["GroupId"].int == 5) {
            data.accountName = listOfNotNull(account["FirstName"].string, account["LastName"].string)
                .joinToString(" ").fixedName().ifBlank { null }
        }
        data.subjects = d.subjects.values.sortedBy { it.name }
        collect(gradesJson.await(), warnings)?.let { data.grades = LibrusParsers.grades(it, d) }
        collect(eventsJson.await(), warnings)?.let { data.events = LibrusParsers.events(it, d).toMutableList() }
        collect(attendanceJson.await(), warnings)?.let { data.attendance = LibrusParsers.attendance(it, d) }
        collect(notesJson.await(), warnings)?.let { data.notices = LibrusParsers.notices(it, d) }
        collect(noticesJson.await(), warnings)?.let { data.announcements = LibrusParsers.announcements(it, d) }
        collect(luckyJson.await(), warnings)?.let { data.luckyNumber = LibrusParsers.luckyNumber(it) }
        collect(timetableJson.await(), warnings)?.let {
            data.timetable[weekStart.string] = LibrusParsers.timetable(it, d)
        }

        // Add homework from the web page as calendar events.
        try {
            val today = Day.today
            val hw = homework.homework(
                if (weekStart < today.adding(-35)) weekStart else today.adding(-35),
                if (weekStart.adding(6) > today.adding(35)) weekStart.adding(6) else today.adding(35)
            )
            data.events.addAll(hw.map { it.asEvent() })
        } catch (e: LibrusError) {
            warnings.add("Zadania domowe: ${e.message}")
        } catch (e: Exception) {
            warnings.add("Zadania domowe: ${e.message ?: e::class.java.simpleName}")
        }

        data.warnings = warnings
        data.fetchedAt = Date()
        data
    }

    /// One timetable week, using dictionaries from the last sync.
    suspend fun timetable(weekStart: Day): List<TimetableDay> {
        val json = api.get("Timetables?weekStart=$weekStart")
        return LibrusParsers.timetable(json, dict)
    }

    // helpers

    private inline fun <T> optional(label: String, work: () -> T): Result<T> =
        try { Result.success(work()) }
        catch (e: Throwable) { Result.failure(LabeledError(label, e)) }

    private fun <T> collect(result: Result<T>, warnings: MutableList<String>): T? =
        result.fold(
            onSuccess = { it },
            onFailure = { e ->
                val labeled = e as? LabeledError
                if (labeled != null) {
                    // Disabled features are expected in some schools; don't nag.
                    if (labeled.cause is LibrusError.FeatureNotActive) return@fold null
                    warnings.add("${labeled.label}: ${labeled.cause.message}")
                } else {
                    warnings.add(e.message ?: e::class.java.simpleName)
                }
                null
            }
        )

    private class LabeledError(val label: String, override val cause: Throwable) : Throwable(cause.message)
}

/// First-time Synergia login + profile fetch. Throws LibrusError on failure.
object LibrusLogin {
    suspend fun synergiaLogin(login: String, password: String): StoredAccount {
        val session = CredentialSynergiaSession(login, password)
        session.ensureLoggedIn(force = false)
        val me = LibrusApi(session).get("Me")["Me"]
        val account = me["Account"]
        val user = me["User"]
        val groupId = account["GroupId"].int ?: 0
        val name = listOfNotNull(user["FirstName"].string, user["LastName"].string)
            .joinToString(" ").fixedName()
        return StoredAccount(
            id = UUID.randomUUID(),
            displayName = login,
            synergiaLogin = login,
            synergiaPassword = password,
            studentName = name.ifBlank { login },
            isParent = groupId in 5..6
        )
    }
}

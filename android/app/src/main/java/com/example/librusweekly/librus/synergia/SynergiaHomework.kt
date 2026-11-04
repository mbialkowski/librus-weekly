package com.example.librusweekly.librus.synergia

import com.example.librusweekly.librus.core.LibrusConstants
import com.example.librusweekly.librus.core.LibrusError
import com.example.librusweekly.librus.models.AgendaEvent
import com.example.librusweekly.librus.models.Day
import com.example.librusweekly.librus.models.fixedName
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/// One row of the Synergia "Moje zadania" (homework) page.
data class HomeworkItem(
    val id: Int,
    val subject: String,
    val teacher: String,
    val topic: String,
    val addedDay: Day?,
    val dueDay: Day
) {
    /// Negated id so these can't clash with HomeWorks ids.
    fun asEvent(): AgendaEvent = AgendaEvent(
        id = -id, day = dueDay, time = null, lessonNumber = null, topic = topic,
        category = "zadanie domowe", colorHex = null,
        subject = subject.ifBlank { null },
        teacher = teacher.ifBlank { null }?.fixedName(),
        addedDate = addedDay?.date
    )
}

/// Homework scraper against `synergia.librus.pl/moje_zadania`.
class SynergiaHomework(private val session: CredentialSynergiaSession) {
    private val http = session.http

    suspend fun homework(from: Day, to: Day): List<HomeworkItem> {
        val form = listOf("dataOd" to from.string, "dataDo" to to.string, "przedmiot" to "-1")
        session.ensureLoggedIn()
        var doc = fetchPage(form)
        if (isLoggedOut(doc)) {
            session.ensureLoggedIn(force = true)
            doc = fetchPage(form)
            if (isLoggedOut(doc)) throw LibrusError.SynergiaSession("sesja wygasła")
        }
        return parse(doc)
    }

    private suspend fun fetchPage(form: List<Pair<String, String>>): Document {
        val url = "${LibrusConstants.SYNERGIA_URL}/moje_zadania"
        val response = http.send(url, method = "POST", form = form, userAgent = LibrusConstants.WEB_USER_AGENT)
        if ("Przerwa techniczna" in response.text) throw LibrusError.Maintenance
        return try { Jsoup.parse(response.text, url) }
        catch (e: Exception) { throw LibrusError.Parse("HTML moje_zadania") }
    }

    private fun isLoggedOut(doc: Document): Boolean =
        doc.selectFirst("form[action*=logowanie]") != null ||
            doc.selectFirst("input[name=login]") != null

    /// Columns: 0 subject, 1 teacher, 2 topic, 4 added, 6 due, 9 details button.
    private fun parse(doc: Document): List<HomeworkItem> {
        val body = doc.selectFirst("table.myHomeworkTable > tbody") ?: return emptyList()
        return body.children().mapNotNull { row ->
            val cells = row.children()
            if (cells.size < 10) return@mapNotNull null
            val due = Day.of(cells[6].text().trim()) ?: return@mapNotNull null
            val id = podgladId(cells[9].select("input").attr("onclick")) ?: return@mapNotNull null
            HomeworkItem(
                id = id,
                subject = cells[0].text().trim(),
                teacher = cells[1].text().trim(),
                topic = cells[2].text().trim(),
                addedDay = Day.of(cells[4].text().trim()),
                dueDay = due
            )
        }
    }

    private fun podgladId(onclick: String): Int? {
        val marker = onclick.indexOf("/podglad/")
        if (marker < 0) return null
        val tail = onclick.substring(marker + "/podglad/".length)
        val digits = tail.takeWhile { it.isDigit() }
        return digits.toIntOrNull()
    }
}

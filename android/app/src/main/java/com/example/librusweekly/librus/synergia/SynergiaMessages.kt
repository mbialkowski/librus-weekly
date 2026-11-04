package com.example.librusweekly.librus.synergia

import com.example.librusweekly.librus.core.LibrusConstants
import com.example.librusweekly.librus.core.LibrusError
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

enum class MessageFolder(val code: String) { INBOX("5"), SENT("6") }

data class MessageSummary(
    val id: String,
    val folder: MessageFolder,
    /// Sender when folder = INBOX, recipient when folder = SENT.
    val correspondent: String,
    val subject: String,
    val date: String,
    val unread: Boolean,
    val hasAttachments: Boolean
)

data class MessageAttachment(
    val id: String,
    val messageId: String,
    val filename: String
)

data class MessageDetail(
    val id: String,
    val sender: String,
    val subject: String,
    val date: String,
    /// Raw HTML of the body; UI strips tags for display.
    val bodyHtml: String,
    val attachments: List<MessageAttachment>
)

/// Lists and fetches individual inbox / sent messages from the Synergia web UI. Mirrors the Swift
/// `SynergiaWeb.messages/.message` pair; shares the same signed-in session as `SynergiaHomework`.
class SynergiaMessages(private val session: CredentialSynergiaSession) {
    private val http = session.http

    suspend fun messages(folder: MessageFolder, page: Int = 0): List<MessageSummary> {
        val path = "wiadomosci/1/${folder.code}"
        session.ensureLoggedIn()
        var doc = fetch(path, listOf(
            "numer_strony105" to page.toString(),
            "porcjowanie_pojemnik105" to "105",
        ))
        if (isLoggedOut(doc)) {
            session.ensureLoggedIn(force = true)
            doc = fetch(path, listOf(
                "numer_strony105" to page.toString(),
                "porcjowanie_pojemnik105" to "105",
            ))
            if (isLoggedOut(doc)) throw LibrusError.SynergiaSession("sesja wygasła")
        }
        return parseList(doc, folder)
    }

    suspend fun message(id: String, folder: MessageFolder): MessageDetail {
        val path = "wiadomosci/1/${folder.code}/$id/f0"
        session.ensureLoggedIn()
        var doc = fetch(path, null)
        if (isLoggedOut(doc)) {
            session.ensureLoggedIn(force = true)
            doc = fetch(path, null)
            if (isLoggedOut(doc)) throw LibrusError.SynergiaSession("sesja wygasła")
        }
        return parseDetail(doc, id)
    }

    private suspend fun fetch(path: String, form: List<Pair<String, String>>?): Document {
        val url = "${LibrusConstants.SYNERGIA_URL}/$path"
        val response = http.send(
            url,
            method = if (form != null) "POST" else "GET",
            form = form,
            userAgent = LibrusConstants.WEB_USER_AGENT,
        )
        if ("Przerwa techniczna" in response.text) throw LibrusError.Maintenance
        return try { Jsoup.parse(response.text, url) }
        catch (e: Exception) { throw LibrusError.Parse("HTML wiadomości") }
    }

    private fun isLoggedOut(doc: Document): Boolean =
        doc.selectFirst("form[action*=logowanie]") != null ||
            doc.selectFirst("input[name=login]") != null

    /// Columns of the wiadomości list, mirroring szkolny-android / the iOS parser:
    /// 0 attachments icon, 1 attachment column (img?), 2 correspondent, 3 subject (link),
    /// 4 date, 5 extra.
    private fun parseList(doc: Document, folder: MessageFolder): List<MessageSummary> {
        val table = doc.selectFirst("table.decorated.stretch") ?: return emptyList()
        return table.select("tbody > tr").mapNotNull { row ->
            val cells = row.children()
            if (cells.size < 6) return@mapNotNull null
            val link = row.selectFirst("a[href*=/wiadomosci/]")?.attr("href").orEmpty()
            val parts = link.split("/")
            val idx = parts.indexOf("wiadomosci")
            if (idx < 0 || parts.size <= idx + 3) return@mapNotNull null
            val id = parts[idx + 3]
            val title = cells[3]
            val unread = when (folder) {
                MessageFolder.INBOX -> {
                    val style = (title.attr("style") + cells[2].attr("style"))
                        .replace(" ", "")
                    "font-weight:bold" in style
                }
                MessageFolder.SENT -> {
                    val readFlag = if (cells.size >= 7) cells[5].text().trim() else ""
                    readFlag == "NIE"
                }
            }
            MessageSummary(
                id = id,
                folder = folder,
                correspondent = clean(cells[2].text()),
                subject = clean(title.text()),
                date = clean(cells[4].text()),
                unread = unread,
                hasAttachments = !cells[1].select("img").isEmpty()
            )
        }
    }

    private fun parseDetail(doc: Document, id: String): MessageDetail {
        val content = doc.selectFirst("div.container-message-content")
            ?: throw LibrusError.Parse("treść wiadomości")
        val headers = mutableListOf<String>()
        val table = doc.selectFirst("table.stretch")
        if (table != null) {
            for (row in table.select("tr").take(3)) {
                headers += clean(row.selectFirst("td.left")?.text().orEmpty())
            }
        }
        val attachments = mutableListOf<MessageAttachment>()
        val re = Regex("""pobierz_zalacznik/(\d+)/(\d+)""")
        for (img in doc.select("img[onclick*=pobierz_zalacznik]")) {
            val onclick = img.attr("onclick").replace("\\/", "/")
            val match = re.find(onclick) ?: continue
            val tr = img.parents().firstOrNull { it.tagName() == "tr" }
            val name = clean(tr?.selectFirst("td")?.text() ?: "załącznik")
            attachments += MessageAttachment(
                id = match.groupValues[2],
                messageId = match.groupValues[1],
                filename = name
            )
        }
        return MessageDetail(
            id = id,
            sender = headers.getOrNull(0).orEmpty(),
            subject = headers.getOrNull(1).orEmpty(),
            date = headers.getOrNull(2).orEmpty(),
            bodyHtml = content.html(),
            attachments = attachments
        )
    }

    private fun clean(s: String): String =
        s.replace(" ", " ").trim()
}

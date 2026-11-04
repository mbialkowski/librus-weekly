import Foundation
import SwiftSoup

// Added in librus-summary (not part of upstream szkolny.eu-iOS).

/// One row of the Synergia "Moje zadania" (homework) page.
public struct HomeworkItem: Codable, Hashable, Identifiable, Sendable {
    public let id: Int
    public let subject: String
    public let teacher: String
    public let topic: String
    public let addedDay: Day?
    public let dueDay: Day

    /// As a calendar event on its due date. Ids are negated so they can't clash with `HomeWorks` ids.
    public var asEvent: AgendaEvent {
        AgendaEvent(
            id: -id, day: dueDay, time: nil, lessonNumber: nil, topic: topic,
            category: "zadanie domowe", colorHex: nil,
            subject: subject.isEmpty ? nil : subject,
            teacher: teacher.isEmpty ? nil : teacher.fixedName,
            addedDate: addedDay?.date
        )
    }
}

extension SynergiaWeb {
    /// Homework from the "Moje zadania" page (port of szkolny-android `LibrusSynergiaHomework`).
    public func homework(from: Day, to: Day) async throws -> [HomeworkItem] {
        let form = [("dataOd", from.string), ("dataDo", to.string), ("przedmiot", "-1")]
        try await ensureSession()
        var doc = try await fetchHomeworkPage(form)
        if SynergiaParser.isLoggedOut(doc) {
            try await ensureSession(force: true)
            doc = try await fetchHomeworkPage(form)
            if SynergiaParser.isLoggedOut(doc) { throw LibrusError.synergiaSession("sesja wygasła") }
        }
        return try SynergiaParser.homework(doc)
    }

    private func fetchHomeworkPage(_ form: [(String, String)]) async throws -> Document {
        let url = URL(string: "\(LibrusConstants.synergiaURL)/moje_zadania")!
        let response = try await http.send(url, method: "POST", form: form, userAgent: LibrusConstants.webUserAgent)
        if response.text.contains("Przerwa techniczna") { throw LibrusError.maintenance }
        do { return try SwiftSoup.parse(response.text, url.absoluteString) } catch {
            throw LibrusError.parse("HTML moje_zadania")
        }
    }
}

extension SynergiaParser {
    public static func homework(_ html: String) throws -> [HomeworkItem] {
        try homework(SwiftSoup.parse(html))
    }

    /// Columns as read by szkolny-android: 0 subject, 1 teacher, 2 topic, 4 added, 6 due, 9 details button.
    static func homework(_ doc: Document) throws -> [HomeworkItem] {
        guard let body = try doc.select("table.myHomeworkTable > tbody").first() else { return [] }
        var items: [HomeworkItem] = []
        for row in body.children().array() {
            let cells = row.children().array()
            guard cells.count >= 10 else { continue }
            func text(_ i: Int) throws -> String {
                try cells[i].text().trimmingCharacters(in: .whitespacesAndNewlines)
            }
            guard let due = Day(try text(6)),
                  let id = podgladID(try cells[9].select("input").attr("onclick")) else { continue }
            items.append(HomeworkItem(
                id: id, subject: try text(0), teacher: try text(1), topic: try text(2),
                addedDay: Day(try text(4)), dueDay: due
            ))
        }
        return items
    }

    static func podgladID(_ onclick: String) -> Int? {
        guard let marker = onclick.range(of: "/podglad/") else { return nil }
        return Int(onclick[marker.upperBound...].prefix { $0.isNumber })
    }
}

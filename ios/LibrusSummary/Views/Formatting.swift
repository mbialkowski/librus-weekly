import LibrusKit
import SwiftUI

extension Color {
    init?(hex: String?) {
        guard var s = hex?.trimmingCharacters(in: .whitespaces) else { return nil }
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt64(s, radix: 16) else { return nil }
        self.init(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
                  blue: Double(v & 0xFF) / 255)
    }
}

/// School days are Warsaw dates; format them in that zone so they never shift by a day.
private enum Formatters {
    static func make(_ template: String) -> DateFormatter {
        let f = DateFormatter()
        f.timeZone = Day.calendar.timeZone
        f.setLocalizedDateFormatFromTemplate(template)
        return f
    }

    static let long = make("EEEEdMMMM")
    static let short = make("dMMM")
    static let weekday = make("EEE")
}

extension Day {
    var longLabel: String {
        let label = Formatters.long.string(from: date)
        return label.prefix(1).uppercased() + label.dropFirst()
    }

    var shortLabel: String { Formatters.short.string(from: date) }
    var weekdayShort: String { Formatters.weekday.string(from: date) }
}

extension Date {
    /// "21:23" today, otherwise "3 Nov 2026, 21:23".
    var updateLabel: String {
        Calendar.current.isDateInToday(self)
            ? formatted(date: .omitted, time: .shortened)
            : formatted(date: .abbreviated, time: .shortened)
    }
}

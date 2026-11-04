import Foundation
import Observation

enum AppLanguage: String, CaseIterable, Identifiable {
    case system = ""
    case english = "en"
    case polish = "pl"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: return "System"
        case .english: return "English"
        case .polish: return "Polski"
        }
    }

    var locale: Locale? {
        switch self {
        case .system: return nil
        case .english: return Locale(identifier: "en_US")
        case .polish: return Locale(identifier: "pl_PL")
        }
    }
}

@MainActor
@Observable
final class AppSettings {
    static let shared = AppSettings()

    var language: AppLanguage {
        didSet { UserDefaults.standard.set(language.rawValue, forKey: Self.key) }
    }

    private static let key = "appLanguage"

    private init() {
        let stored = UserDefaults.standard.string(forKey: Self.key) ?? ""
        language = AppLanguage(rawValue: stored) ?? .system
    }
}

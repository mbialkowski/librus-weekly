import SwiftUI

@main
struct LibrusSummaryApp: App {
    @State private var model = AppModel()
    @State private var settings = AppSettings.shared

    var body: some Scene {
        WindowGroup {
            Group {
                if model.isLoggedIn {
                    WeekView()
                } else if model.isAutoLoggingIn {
                    ProgressView("Logging in to Librus…")
                } else {
                    LoginView()
                }
            }
            .environment(model)
            .environment(settings)
            .applyLanguage(settings.language)
            .preferredColorScheme(.light)
            .task { await model.autoLogin() }
        }
    }
}

private extension View {
    @ViewBuilder
    func applyLanguage(_ language: AppLanguage) -> some View {
        if let locale = language.locale {
            self.environment(\.locale, locale)
        } else {
            self
        }
    }
}

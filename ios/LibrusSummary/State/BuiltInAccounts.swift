import Foundation

/// Logins bundled into a private build from `Resources/BuiltInAccounts.json`, which is
/// git-ignored so the passwords never reach the repository (see ios/README.md).
struct BuiltInAccount: Decodable {
    let login: String
    let password: String
}

enum BuiltInAccounts {
    static func load() -> [BuiltInAccount] {
        guard let url = Bundle.main.url(forResource: "BuiltInAccounts", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return [] }
        return ((try? JSONDecoder().decode([BuiltInAccount].self, from: data)) ?? [])
            .filter { !$0.login.isEmpty && !$0.password.isEmpty }
    }
}

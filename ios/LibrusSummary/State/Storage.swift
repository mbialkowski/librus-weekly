import Foundation
import LibrusKit
import Security

struct SavedAccounts: Codable {
    var accounts: [StoredAccount] = []
    var selectedID: UUID?
    var sideBySide: Bool?
}

/// Synergia accounts (including passwords) live only in the Keychain, on this device.
enum KeychainStore {
    private static let base: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "librus-summary.account",
        kSecAttrAccount as String: "account",
    ]

    static func load() -> SavedAccounts {
        var query = base
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return SavedAccounts() }
        if let saved = try? JSONDecoder().decode(SavedAccounts.self, from: data) {
            return saved
        }
        // Builds before multi-child support stored a single account.
        if let single = try? JSONDecoder().decode(StoredAccount.self, from: data) {
            return SavedAccounts(accounts: [single], selectedID: single.id)
        }
        return SavedAccounts()
    }

    static func save(_ saved: SavedAccounts) {
        guard let data = try? JSONEncoder().encode(saved) else { return }
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        if SecItemUpdate(base as CFDictionary, attributes as CFDictionary) == errSecItemNotFound {
            SecItemAdd(base.merging(attributes) { $1 } as CFDictionary, nil)
        }
    }

    static func delete() {
        SecItemDelete(base as CFDictionary)
    }
}

/// Last downloaded data per child, so the app opens instantly and works offline.
enum DataCache {
    private static var directory: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("cache", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private static func file(for id: UUID) -> URL {
        directory.appendingPathComponent("\(id.uuidString).json")
    }

    static func load(_ id: UUID) -> StudentData? {
        guard let data = try? Data(contentsOf: file(for: id)) else { return nil }
        return try? JSONDecoder().decode(StudentData.self, from: data)
    }

    static func save(_ studentData: StudentData, for id: UUID) {
        guard let data = try? JSONEncoder().encode(studentData) else { return }
        try? data.write(to: file(for: id), options: [.atomic, .completeFileProtection])
    }

    private static func changesFile(for id: UUID) -> URL {
        directory.appendingPathComponent("\(id.uuidString).changes.json")
    }

    static func loadChanges(_ id: UUID) -> UpdateChanges? {
        guard let data = try? Data(contentsOf: changesFile(for: id)) else { return nil }
        return try? JSONDecoder().decode(UpdateChanges.self, from: data)
    }

    static func saveChanges(_ changes: UpdateChanges, for id: UUID) {
        guard let data = try? JSONEncoder().encode(changes) else { return }
        try? data.write(to: changesFile(for: id), options: [.atomic, .completeFileProtection])
    }

    static func remove(_ id: UUID) {
        try? FileManager.default.removeItem(at: file(for: id))
        try? FileManager.default.removeItem(at: changesFile(for: id))
    }

    static func removeAll() {
        try? FileManager.default.removeItem(at: directory)
    }
}

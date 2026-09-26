import Foundation
import Security

/// Secure storage for authentication tokens using iOS Keychain
actor KeychainService {
    static let shared = KeychainService()

    private let service = Constants.Keychain.service

    // MARK: - Token Storage

    func saveToken(_ token: String, expiresAt: Date) throws {
        try self.save(key: Constants.Keychain.tokenKey, data: Data(token.utf8))

        let expiryData = try JSONEncoder().encode(expiresAt)
        try self.save(key: Constants.Keychain.tokenExpiryKey, data: expiryData)
    }

    func getToken() -> String? {
        guard let data = get(key: Constants.Keychain.tokenKey) else { return nil }

        // Check if token is expired
        if let expiryData = get(key: Constants.Keychain.tokenExpiryKey),
           let expiresAt = try? JSONDecoder().decode(Date.self, from: expiryData) {
            if Date() >= expiresAt {
                // Token expired, clean up
                self.deleteToken()
                return nil
            }
        }

        return String(data: data, encoding: .utf8)
    }

    func deleteToken() {
        self.delete(key: Constants.Keychain.tokenKey)
        self.delete(key: Constants.Keychain.tokenExpiryKey)
    }

    // MARK: - User Storage

    func saveUser(_ user: User) throws {
        let data = try JSONEncoder().encode(user)
        try self.save(key: Constants.Keychain.userKey, data: data)
    }

    func getUser() -> User? {
        guard let data = get(key: Constants.Keychain.userKey) else { return nil }
        return try? JSONDecoder().decode(User.self, from: data)
    }

    func deleteUser() {
        self.delete(key: Constants.Keychain.userKey)
    }

    // MARK: - Clear All

    func clearAll() {
        self.deleteToken()
        self.deleteUser()
    }

    // MARK: - Private Keychain Operations

    private func baseQuery(for key: String) -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: self.service,
            kSecAttrAccount as String: key
        ]
        if let accessGroup = Constants.Keychain.accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        return query
    }

    private func save(key: String, data: Data) throws {
        self.delete(key: key)

        var query = self.baseQuery(for: key)
        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly

        let status = SecItemAdd(query as CFDictionary, nil)

        guard status == errSecSuccess else {
            throw KeychainError.unableToSave(status)
        }
    }

    private func get(key: String) -> Data? {
        var query = self.baseQuery(for: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess else { return nil }

        return result as? Data
    }

    private func delete(key: String) {
        SecItemDelete(self.baseQuery(for: key) as CFDictionary)
    }
}

// MARK: - Keychain Errors

// MARK: - TokenProviding Conformance

extension KeychainService: TokenProviding {}

enum KeychainError: LocalizedError {
    case unableToSave(OSStatus)
    case itemNotFound

    var errorDescription: String? {
        switch self {
        case let .unableToSave(status):
            String(localized: "Unable to save to Keychain (status: \(status))")
        case .itemNotFound:
            String(localized: "Item not found in Keychain")
        }
    }
}

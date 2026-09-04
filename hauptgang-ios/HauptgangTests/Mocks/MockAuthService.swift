import Foundation
@testable import Hauptgang

/// Mock authentication service for testing
/// Allows controlling login results and tracking method calls
final class MockAuthService: AuthServiceProtocol, @unchecked Sendable {
    var loginResult: Result<User, Error> = .success(User(id: 1, email: "test@example.com"))
    var logoutCalled = false
    var deleteAccountCalled = false
    var deleteAccountResult: Result<Void, Error> = .success(())
    var currentUser: User?
    var updateLifecycleResult: User?
    private(set) var lastLifecycleValue: Bool?
    private(set) var lastOAuthCredential: OAuthCredential?

    func login(email _: String, password _: String) async throws -> User {
        try self.loginResult.get()
    }

    func signup(
        name _: String,
        email _: String,
        password _: String,
        passwordConfirmation _: String
    ) async throws -> User {
        try self.loginResult.get()
    }

    func login(with credential: OAuthCredential) async throws -> User {
        self.lastOAuthCredential = credential
        return try self.loginResult.get()
    }

    func updateName(_ name: String) async throws -> User {
        var user = try self.loginResult.get()
        user.name = name
        return user
    }

    func updateLifecycleNotifications(_ enabled: Bool) async throws -> User {
        self.lastLifecycleValue = enabled
        guard let user = self.updateLifecycleResult else { throw APIError.invalidResponse }
        return user
    }

    func logout() async {
        self.logoutCalled = true
        self.currentUser = nil
    }

    func deleteAccount() async throws {
        try self.deleteAccountResult.get()
        self.deleteAccountCalled = true
        self.currentUser = nil
    }

    func getCurrentUser() async -> User? {
        self.currentUser
    }

    func isAuthenticated() async -> Bool {
        self.currentUser != nil
    }
}

/// Error type for testing failure scenarios
enum MockAuthError: Error, LocalizedError {
    case invalidCredentials
    case networkError

    var errorDescription: String? {
        switch self {
        case .invalidCredentials:
            "Invalid email or password"
        case .networkError:
            "Network connection failed"
        }
    }
}

import Foundation

/// Protocol defining authentication service operations
/// Enables dependency injection for testing
protocol AuthServiceProtocol: Sendable {
    func login(email: String, password: String) async throws -> User
    func signup(name: String, email: String, password: String, passwordConfirmation: String) async throws -> User
    func updateName(_ name: String) async throws -> User
    func updateLifecycleNotifications(_ enabled: Bool) async throws -> User
    func logout() async
    func deleteAccount() async throws
    func getCurrentUser() async -> User?
    func isAuthenticated() async -> Bool
}

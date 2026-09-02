import Foundation
import SwiftUI

/// App-wide authentication state manager
/// Injected as an environment object for global access
@MainActor
final class AuthManager: ObservableObject {
    @Published private(set) var authState: AuthState = .unknown

    private let authService: AuthServiceProtocol

    init(authService: AuthServiceProtocol = AuthService.shared) {
        self.authService = authService
    }

    // MARK: - Auth State

    enum AuthState: Equatable {
        case unknown
        case unauthenticated
        case authenticated(User)

        var isAuthenticated: Bool {
            if case .authenticated = self {
                return true
            }
            return false
        }

        var user: User? {
            if case let .authenticated(user) = self {
                return user
            }
            return nil
        }

        static func == (lhs: AuthState, rhs: AuthState) -> Bool {
            switch (lhs, rhs) {
            case (.unknown, .unknown):
                true
            case (.unauthenticated, .unauthenticated):
                true
            case let (.authenticated(lhsUser), .authenticated(rhsUser)):
                lhsUser == rhsUser
            default:
                false
            }
        }
    }

    // MARK: - Public Methods

    /// Check authentication status on app launch
    func checkAuthStatus() async {
        if let user = await authService.getCurrentUser() {
            self.authState = .authenticated(user)
        } else {
            self.authState = .unauthenticated
        }
    }

    /// Update state after successful login
    func signIn(user: User) {
        self.authState = .authenticated(user)
    }

    /// Update the current user's display name
    func updateName(_ name: String) async throws {
        let updated = try await authService.updateName(name)
        self.authState = .authenticated(updated)
    }

    /// Update the current user's lifecycle notification preference
    func updateLifecycleNotifications(_ enabled: Bool) async throws {
        let updated = try await authService.updateLifecycleNotifications(enabled)
        self.authState = .authenticated(updated)
    }

    /// Sign out and clear credentials
    func signOut() async {
        await CookbookContext.shared.reset()
        // Drop the device token server-side BEFORE clearing the API token.
        await PushNotificationService.shared.unregister()
        await PushNotificationService.shared.setAuthenticated(false)
        await RecipeViewTracker.shared.reset()
        await self.authService.logout()
        self.authState = .unauthenticated
    }

    /// Permanently delete the user's account and clear local state.
    /// Server-side `User.dependent: :destroy` cascades through api_tokens and device_tokens,
    /// so we only touch local state after the destroy succeeds — otherwise a network failure
    /// would leave a signed-in user with their cookbook context and push registration wiped.
    func deleteAccount() async throws {
        try await self.authService.deleteAccount()
        await CookbookContext.shared.reset()
        await PushNotificationService.shared.unregister()
        await PushNotificationService.shared.setAuthenticated(false)
        self.authState = .unauthenticated
    }
}

import Foundation
import UIKit

/// Coordinates authentication operations between API and Keychain
final class AuthService: AuthServiceProtocol {
    static let shared = AuthService()

    private let keychain = KeychainService.shared
    private let api = APIClient.shared

    private init() {}

    // MARK: - Login

    func login(email: String, password: String) async throws -> User {
        let deviceName = await getDeviceName()
        let onboardingDeviceId = OnboardingService.consumeDeviceIdForAuth()

        let request = LoginRequest(
            email: email,
            password: password,
            deviceName: deviceName,
            onboardingDeviceId: onboardingDeviceId
        )

        let response: AuthResponse = try await api.request(
            endpoint: "session",
            method: .post,
            body: request
        )

        // Store credentials securely
        try await self.keychain.saveToken(response.token, expiresAt: response.expiresAt)
        try await self.keychain.saveUser(response.user)

        return response.user
    }

    // MARK: - Signup

    func signup(name: String, email: String, password: String, passwordConfirmation: String) async throws -> User {
        let deviceName = await getDeviceName()
        let onboardingDeviceId = OnboardingService.consumeDeviceIdForAuth()

        let request = SignupRequest(
            name: name,
            email: email,
            password: password,
            passwordConfirmation: passwordConfirmation,
            deviceName: deviceName,
            onboardingDeviceId: onboardingDeviceId
        )

        let response: AuthResponse = try await api.request(
            endpoint: "registration",
            method: .post,
            body: request
        )

        try await self.keychain.saveToken(response.token, expiresAt: response.expiresAt)
        try await self.keychain.saveUser(response.user)

        return response.user
    }

    // MARK: - Update Profile

    func updateName(_ name: String) async throws -> User {
        let request = AccountUpdateRequest(user: AccountUpdateBody(name: name))

        let response: AccountUpdateResponse = try await api.request(
            endpoint: "account",
            method: .patch,
            body: request,
            authenticated: true
        )

        try await self.keychain.saveUser(response.user)
        return response.user
    }

    func updateLifecycleNotifications(_ enabled: Bool) async throws -> User {
        let request = AccountUpdateRequest(user: AccountUpdateBody(lifecycleNotificationsEnabled: enabled))

        let response: AccountUpdateResponse = try await api.request(
            endpoint: "account",
            method: .patch,
            body: request,
            authenticated: true
        )

        try await self.keychain.saveUser(response.user)
        return response.user
    }

    // MARK: - Logout

    func logout() async {
        // Best effort server-side logout
        do {
            try await self.api.requestVoid(
                endpoint: "session",
                method: .delete,
                authenticated: true
            )
        } catch {
            // Continue with local cleanup even if server logout fails
            print("Server logout failed: \(error.localizedDescription)")
        }

        // Always clear local credentials
        await self.keychain.clearAll()
    }

    // MARK: - Delete Account

    func deleteAccount() async throws {
        try await self.api.requestVoid(
            endpoint: "account",
            method: .delete,
            authenticated: true
        )
        await self.keychain.clearAll()
    }

    // MARK: - Session Check

    func getCurrentUser() async -> User? {
        // Token validity is checked in getToken()
        guard await self.keychain.getToken() != nil else {
            // Token missing or expired, clear user data
            await self.keychain.clearAll()
            return nil
        }

        return await self.keychain.getUser()
    }

    func isAuthenticated() async -> Bool {
        await self.getCurrentUser() != nil
    }

    // MARK: - Private

    @MainActor
    private func getDeviceName() -> String {
        UIDevice.current.name
    }
}

// MARK: - Request Types

private struct LoginRequest: Encodable {
    let email: String
    let password: String
    let deviceName: String
    let onboardingDeviceId: String?
}

private struct SignupRequest: Encodable {
    let name: String
    let email: String
    let password: String
    let passwordConfirmation: String
    let deviceName: String
    let onboardingDeviceId: String?
}

private struct AccountUpdateRequest: Encodable {
    let user: AccountUpdateBody
}

private struct AccountUpdateBody: Encodable {
    var name: String?
    var lifecycleNotificationsEnabled: Bool?
}

private struct AccountUpdateResponse: Decodable {
    let user: User
}

import Foundation
import SwiftUI

/// Login/signup form state and validation
@MainActor
final class AuthViewModel: ObservableObject {
    @Published var name = ""
    @Published var email = ""
    @Published var password = ""
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published private(set) var showsAppleAccountCreationConfirmation = false
    @Published var isSignUp = false {
        didSet {
            self.errorMessage = nil
            self.showsAppleAccountCreationConfirmation = false
            self.nameDirty = false
            self.emailDirty = false
            self.passwordDirty = false
        }
    }

    /// Tracks whether fields have been blurred at least once
    @Published var nameDirty = false
    @Published var emailDirty = false
    @Published var passwordDirty = false

    private let authService: AuthServiceProtocol
    private let appleSignInProvider: AppleSignInProviding

    init(
        initialIsSignUp: Bool = false,
        authService: AuthServiceProtocol = AuthService.shared,
        appleSignInProvider: AppleSignInProviding = AppleSignInService()
    ) {
        self.isSignUp = initialIsSignUp
        self.authService = authService
        self.appleSignInProvider = appleSignInProvider
    }

    // MARK: - Validation

    var isFormValid: Bool {
        let trimmedEmail = self.email.trimmingCharacters(in: .whitespaces)
        let baseValid = !trimmedEmail.isEmpty &&
            !self.password.isEmpty &&
            self.isValidEmail(trimmedEmail)

        if self.isSignUp {
            let trimmedName = self.name.trimmingCharacters(in: .whitespaces)
            return baseValid &&
                !trimmedName.isEmpty &&
                self.password.count >= 12
        }

        return baseValid
    }

    var nameError: String? {
        guard self.isSignUp, self.nameDirty else { return nil }
        if self.name.trimmingCharacters(in: .whitespaces).isEmpty {
            return "Please enter your name"
        }
        return nil
    }

    var emailError: String? {
        guard self.emailDirty else { return nil }
        let trimmed = self.email.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty {
            return nil
        }
        if !self.isValidEmail(trimmed) {
            return "Please enter a valid email"
        }
        return nil
    }

    func markAllDirty() {
        self.nameDirty = true
        self.emailDirty = true
        self.passwordDirty = true
    }

    // MARK: - Login

    func login(authManager: AuthManager) async -> Bool {
        await self.performAuthAction(authManager: authManager) {
            try await self.authService.login(
                email: self.email.trimmingCharacters(in: .whitespaces).lowercased(),
                password: self.password
            )
        }
    }

    // MARK: - Signup

    func signup(authManager: AuthManager) async -> Bool {
        await self.performAuthAction(authManager: authManager) {
            try await self.authService.signup(
                name: self.name.trimmingCharacters(in: .whitespaces),
                email: self.email.trimmingCharacters(in: .whitespaces).lowercased(),
                password: self.password,
                passwordConfirmation: self.password
            )
        }
    }

    func login(with credential: OAuthCredential, authManager: AuthManager) async -> Bool {
        await self.performAuthAction(authManager: authManager, requiresValidForm: false) {
            try await self.authService.login(with: credential)
        }
    }

    func signInWithApple(authManager: AuthManager) async -> Bool {
        guard !self.isLoading else { return false }
        self.showsAppleAccountCreationConfirmation = false
        return await self.performAppleSignIn(allowAccountCreation: false, authManager: authManager)
    }

    func confirmAppleAccountCreation(authManager: AuthManager) async -> Bool {
        guard self.showsAppleAccountCreationConfirmation, !self.isLoading else { return false }
        self.showsAppleAccountCreationConfirmation = false
        return await self.performAppleSignIn(allowAccountCreation: true, authManager: authManager)
    }

    func cancelAppleAccountCreation() {
        self.showsAppleAccountCreationConfirmation = false
    }

    func useExistingAccount() {
        self.showsAppleAccountCreationConfirmation = false
        self.isSignUp = false
    }

    func present(_ error: Error) {
        if let localizedError = error as? LocalizedError,
           let description = localizedError.errorDescription {
            self.errorMessage = description
        } else {
            self.errorMessage = "An unexpected error occurred. Please try again."
        }
    }

    // MARK: - Private

    private func performAuthAction(
        authManager: AuthManager,
        requiresValidForm: Bool = true,
        action: () async throws -> User
    ) async -> Bool {
        guard !self.isLoading, !requiresValidForm || self.isFormValid else { return false }

        self.isLoading = true
        self.errorMessage = nil
        defer { self.isLoading = false }

        do {
            let user = try await action()
            authManager.signIn(user: user)
            return true
        } catch let error as APIError {
            self.errorMessage = error.localizedDescription
        } catch {
            self.errorMessage = "An unexpected error occurred. Please try again."
        }

        return false
    }

    private func performAppleSignIn(allowAccountCreation: Bool, authManager: AuthManager) async -> Bool {
        guard !self.isLoading else { return false }

        self.isLoading = true
        self.errorMessage = nil
        defer { self.isLoading = false }

        do {
            guard let credential = try await self.appleSignInProvider.signIn() else { return false }
            let user = try await self.authService.login(
                with: credential,
                allowAccountCreation: allowAccountCreation
            )
            authManager.signIn(user: user)
            return true
        } catch APIError.appleAccountCreationConfirmationRequired where !allowAccountCreation {
            self.showsAppleAccountCreationConfirmation = true
        } catch let error as APIError {
            self.errorMessage = error.localizedDescription
        } catch {
            self.present(error)
        }

        return false
    }

    private func isValidEmail(_ email: String) -> Bool {
        let emailRegex = #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"#
        return email.range(of: emailRegex, options: .regularExpression) != nil
    }
}

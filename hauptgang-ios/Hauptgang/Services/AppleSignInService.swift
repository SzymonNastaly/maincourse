import AuthenticationServices
import Combine
import CryptoKit
import Foundation
import UIKit

@MainActor
final class AppleSignInService: ObservableObject {
    private var rawNonce: String?
    private var activeDelegate: AuthorizationDelegate?

    /// Runs the Apple authorization sheet directly, so the button above it can be
    /// drawn to the app's own spec instead of `SignInWithAppleButton`'s.
    /// Returns nil when the person cancels.
    func signIn() async throws -> OAuthCredential? {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        self.configure(request)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        let result = await withCheckedContinuation { continuation in
            let delegate = AuthorizationDelegate { result in
                continuation.resume(returning: result)
            }
            self.activeDelegate = delegate
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            controller.performRequests()
        }
        self.activeDelegate = nil

        return try self.credential(from: result)
    }

    func configure(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = UUID().uuidString
        self.rawNonce = nonce
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
    }

    func credential(from result: Result<ASAuthorization, Error>) throws -> OAuthCredential? {
        defer { self.rawNonce = nil }

        switch result {
        case let .success(authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                throw OAuthSignInError.missingAppleCredential
            }
            guard let nonce = self.rawNonce else {
                throw OAuthSignInError.missingNonce
            }
            guard let tokenData = credential.identityToken,
                  let idToken = String(data: tokenData, encoding: .utf8)
            else {
                throw OAuthSignInError.missingIdentityToken
            }
            guard let codeData = credential.authorizationCode,
                  let authorizationCode = String(data: codeData, encoding: .utf8)
            else {
                throw OAuthSignInError.missingAuthorizationCode
            }

            return OAuthCredential(
                provider: .apple,
                idToken: idToken,
                authorizationCode: authorizationCode,
                nonce: nonce,
                name: self.formattedName(credential.fullName)
            )
        case let .failure(error):
            if let authorizationError = error as? ASAuthorizationError,
               authorizationError.code == .canceled {
                return nil
            }
            if let authorizationError = error as? ASAuthorizationError,
               authorizationError.code == .unknown {
                throw OAuthSignInError.appleAccountUnavailable
            }
            throw error
        }
    }

    static func sha256(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
    }

    private func formattedName(_ components: PersonNameComponents?) -> String? {
        guard let components else { return nil }
        let name = PersonNameComponentsFormatter()
            .string(from: components)
            .trimmingCharacters(in: .whitespaces)
        return name.isEmpty ? nil : name
    }
}

// MARK: - Authorization delegate

/// Bridges `ASAuthorizationController`'s delegate callbacks into a single
/// continuation. Retained by `AppleSignInService` for the length of the flow.
@MainActor
private final class AuthorizationDelegate: NSObject,
    ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    private var finish: ((Result<ASAuthorization, Error>) -> Void)?

    init(finish: @escaping (Result<ASAuthorization, Error>) -> Void) {
        self.finish = finish
    }

    func authorizationController(
        controller _: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        self.complete(with: .success(authorization))
    }

    func authorizationController(
        controller _: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        self.complete(with: .failure(error))
    }

    func presentationAnchor(for _: ASAuthorizationController) -> ASPresentationAnchor {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        return scene?.windows.first(where: \.isKeyWindow) ?? ASPresentationAnchor()
    }

    /// The delegate fires exactly one callback, but resuming a continuation twice
    /// traps, so the handler is cleared as it is called.
    private func complete(with result: Result<ASAuthorization, Error>) {
        let finish = self.finish
        self.finish = nil
        finish?(result)
    }
}

import Foundation

enum OAuthProvider: String, Encodable, Sendable {
    case apple
    case google
}

struct OAuthCredential: Sendable {
    let provider: OAuthProvider
    let idToken: String
    let authorizationCode: String?
    let nonce: String
    let name: String?
}

enum OAuthSignInError: LocalizedError {
    case missingAppleCredential
    case missingIdentityToken
    case missingAuthorizationCode
    case missingNonce
    case missingPresenter
    case googleNotConfigured
    case appleAccountUnavailable

    var errorDescription: String? {
        switch self {
        case .missingAppleCredential, .missingIdentityToken, .missingAuthorizationCode, .missingNonce:
            "The provider did not return valid sign-in credentials. Please try again."
        case .missingPresenter:
            "MainCourse could not open the sign-in screen. Please try again."
        case .googleNotConfigured:
            "Google Sign-In has not been configured for this build."
        case .appleAccountUnavailable:
            "Sign in with Apple is unavailable. Check your Apple ID in Settings and try again."
        }
    }
}

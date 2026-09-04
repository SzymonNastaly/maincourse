import Foundation

enum APIError: LocalizedError {
    case invalidURL
    case networkError(Error)
    case invalidResponse
    case unauthorized
    case forbidden
    case notFound
    case payloadTooLarge
    case unsupportedMediaType
    case unprocessableEntity(String?)
    case serverError(statusCode: Int)
    case decodingError(Error)
    case importLimitReached
    case invalidCredentials
    case oauthAuthenticationFailed
    case oauthUnavailable
    case accountLinkRequired
    case unknown

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            "Invalid URL configuration"
        case .networkError:
            "Unable to connect. Please check your internet connection."
        case .invalidResponse:
            "Received an invalid response from the server"
        case .unauthorized:
            "Your session has expired. Please sign in again."
        case .forbidden:
            "You don't have permission to perform this action"
        case .notFound:
            "The requested resource was not found"
        case .payloadTooLarge:
            "Image is too large. Please try a smaller photo."
        case .unsupportedMediaType:
            "Unsupported image format."
        case let .unprocessableEntity(message):
            message ?? "Could not process this image."
        case let .serverError(code):
            "Server error (\(code)). Please try again later."
        case .decodingError:
            "Unable to process the server response"
        case .importLimitReached:
            "You've reached your free limit of 15 imports this month. Upgrade to Pro for unlimited imports."
        case .invalidCredentials:
            "Invalid email or password"
        case .oauthAuthenticationFailed:
            "Could not sign in with that provider. Please try again."
        case .oauthUnavailable:
            "That sign-in provider is temporarily unavailable. Please try again later."
        case .accountLinkRequired:
            "An account already exists for this email. Sign in with your password instead."
        case .unknown:
            "An unexpected error occurred"
        }
    }

    var recoverySuggestion: String? {
        switch self {
        case .networkError:
            "Check your Wi-Fi or cellular connection and try again."
        case .unauthorized:
            "Please sign in with your credentials."
        case .invalidCredentials:
            "Double-check your email and password, then try again."
        case .oauthAuthenticationFailed:
            "Try the provider again or use email and password."
        case .oauthUnavailable:
            "Wait a moment and try again, or use email and password."
        case .accountLinkRequired:
            "Use the password reset link on the website if you have forgotten your password."
        case .serverError:
            "Wait a moment and try again. If the problem persists, contact support."
        default:
            nil
        }
    }
}

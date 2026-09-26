import Foundation

enum APIError: LocalizedError {
    case invalidURL
    case networkError(Error)
    case invalidResponse
    case unauthorized
    case forbidden
    case notFound
    case resourceGone
    case requestConflict
    case payloadTooLarge(APIProblem?)
    case unsupportedMediaType
    case unprocessableEntity(APIProblem)
    case serverError(statusCode: Int)
    case decodingError(Error)
    case importLimitReached(APIProblem)
    case invalidCredentials
    case oauthAuthenticationFailed
    case oauthUnavailable
    case accountLinkRequired
    case appleAccountCreationConfirmationRequired
    case unknown
    case remote(APIProblem)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            String(localized: "Invalid URL configuration")
        case .networkError:
            String(localized: "Unable to connect. Please check your internet connection.")
        case .invalidResponse:
            String(localized: "Received an invalid response from the server")
        case .unauthorized:
            self.message(.unauthorized)
        case .forbidden:
            self.message(.forbidden)
        case .notFound:
            self.message(.not_found)
        case .resourceGone:
            self.message(.recipe_save_gone)
        case .requestConflict:
            self.message(.recipe_save_conflict)
        case let .payloadTooLarge(problem):
            problem?.message() ?? String(localized: "Image is too large. Please try a smaller photo.")
        case .unsupportedMediaType:
            String(localized: "Unsupported image format.")
        case let .unprocessableEntity(problem):
            problem.message()
        case let .serverError(code):
            String(localized: "Server error (\(code)). Please try again later.")
        case .decodingError:
            String(localized: "Unable to process the server response")
        case let .importLimitReached(problem):
            problem.message()
        case .invalidCredentials:
            self.message(.invalid_credentials)
        case .oauthAuthenticationFailed:
            self.message(.oauth_failed)
        case .oauthUnavailable:
            self.message(.oauth_unavailable)
        case .accountLinkRequired:
            self.message(.account_link_required)
        case .appleAccountCreationConfirmationRequired:
            self.message(.apple_account_creation_confirmation_required)
        case .unknown:
            String(localized: "An unexpected error occurred")
        case let .remote(problem):
            problem.message()
        }
    }

    var recoverySuggestion: String? {
        switch self {
        case .networkError:
            String(localized: "Check your Wi-Fi or cellular connection and try again.")
        case .unauthorized:
            String(localized: "Please sign in with your credentials.")
        case .invalidCredentials:
            String(localized: "Double-check your email and password, then try again.")
        case .oauthAuthenticationFailed:
            String(localized: "Try the provider again or use email and password.")
        case .oauthUnavailable:
            String(localized: "Wait a moment and try again, or use email and password.")
        case .accountLinkRequired:
            String(localized: "Use the password reset link on the website if you have forgotten your password.")
        case .appleAccountCreationConfirmationRequired:
            String(
                localized: """
                Sign in the way you used before to keep your recipes, \
                or confirm creation of a separate cookbook.
                """
            )
        case .serverError:
            String(localized: "Wait a moment and try again. If the problem persists, contact support.")
        default:
            nil
        }
    }

    private func message(_ code: ApiErrorCode) -> String {
        code.message(count: nil, bundle: .main, locale: .autoupdatingCurrent)
    }
}

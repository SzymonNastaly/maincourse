import Foundation

enum Constants {
    enum API {
        #if DEBUG
        /// Local development - use your Mac's IP for device testing
        /// For simulator: localhost works fine
        static let host: URL = {
            guard let url = URL(string: "http://127.0.0.1:3000") else {
                preconditionFailure("Invalid API host URL")
            }
            return url
        }()

        static let baseURL: URL = {
            guard let url = URL(string: "http://127.0.0.1:3000/api/v1") else {
                preconditionFailure("Invalid API base URL")
            }
            return url
        }()
        #else
        /// Production API domain. Both this and the legacy cook.hauptgang.app are
        /// served until December 2026; this is the one new builds talk to.
        private static let productionHost = "app.getmaincourse.com"

        static let host: URL = {
            guard let url = URL(string: "https://\(productionHost)") else {
                preconditionFailure("Invalid API host URL")
            }
            return url
        }()

        static let baseURL: URL = {
            guard let url = URL(string: "https://\(productionHost)/api/v1") else {
                preconditionFailure("Invalid API base URL")
            }
            return url
        }()
        #endif

        static let sessionPath = "/session"
        static let healthCheckPath = "/up"

        static let healthCheckURL: URL = .init(string: healthCheckPath, relativeTo: host)!

        /// Resolves a relative path (e.g., "/rails/active_storage/...") to a full URL
        static func resolveURL(_ path: String?) -> URL? {
            guard let path, !path.isEmpty else { return nil }
            if path.hasPrefix("http://") || path.hasPrefix("https://") {
                return URL(string: path)
            }
            return URL(string: path, relativeTo: self.host)
        }
    }

    enum DeepLinks {
        /// Hosts whose /invite/{token} links open this app. The legacy host stays
        /// until the cook.hauptgang.app entitlement is dropped in December 2026.
        static let universalLinkHosts: Set<String> = [
            "app.getmaincourse.com",
            "cook.hauptgang.app"
        ]
    }

    enum OAuth {
        static var isGoogleConfigured: Bool {
            guard let clientId = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String,
                  let serverClientId = Bundle.main.object(forInfoDictionaryKey: "GIDServerClientID") as? String,
                  clientId.hasSuffix(".apps.googleusercontent.com"),
                  !clientId.hasPrefix("REPLACE_WITH_"),
                  !serverClientId.isEmpty,
                  !serverClientId.hasPrefix("REPLACE_WITH_")
            else {
                return false
            }

            let prefix = clientId.dropLast(".apps.googleusercontent.com".count)
            let expectedScheme = "com.googleusercontent.apps.\(prefix)"
            let urlTypes = Bundle.main.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]] ?? []
            return urlTypes
                .compactMap { $0["CFBundleURLSchemes"] as? [String] }
                .flatMap { $0 }
                .contains(expectedScheme)
        }
    }

    enum RevenueCat {
        #if DEBUG
        static let apiKey = "test_JMMvmVnASkOxcTiywZWGOyDZhMK"
        #else
        static let apiKey = "appl_cXUmnxvvORXplHaLebPtFzfKEhC"
        #endif

        static let entitlementID = "Hauptgang Pro"
    }

    enum Sentry {
        #if DEBUG
        static let dsn = ""
        #else
        static let dsn = "https://d992f788cc33b152950416b88608b4f7@o4511087849766912.ingest.de.sentry.io/4511087853895760"
        #endif
        static let environment: String = {
            #if DEBUG
            return "development"
            #else
            return "production"
            #endif
        }()
    }

    enum Keychain {
        static let service = "com.hauptgang.ios"
        static let tokenKey = "auth_token"
        static let tokenExpiryKey = "auth_token_expiry"
        static let userKey = "current_user"
        /// Shared access group for Keychain sharing between app and extensions.
        /// Read from Info.plist where $(AppIdentifierPrefix) is expanded at build time.
        /// Returns nil if not configured, which uses the app's default access group.
        static var accessGroup: String? {
            Bundle.main.object(forInfoDictionaryKey: "KeychainAccessGroup") as? String
        }
    }
}

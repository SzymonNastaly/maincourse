import Foundation
import os

/// Parses deep link URLs and extracts invitation tokens
@MainActor @Observable
final class DeepLinkRouter {
    private(set) var pendingInvitationToken: String?

    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "DeepLinkRouter")

    /// Parse an incoming URL and extract an invitation token if present
    func handle(_ url: URL) {
        if let token = Self.extractInvitationToken(from: url) {
            self.logger.info("Received invitation deep link with token")
            self.pendingInvitationToken = token
        } else {
            self.logger.warning("Unrecognized deep link: \(url.absoluteString)")
        }
    }

    /// Clear the pending invitation after it's been handled
    func clearPendingInvitation() {
        self.pendingInvitationToken = nil
    }

    /// Store a pending token for unauthenticated users (present after login)
    func storePendingToken(_ token: String) {
        UserDefaults.standard.set(token, forKey: "pendingInvitationToken")
        self.logger.info("Stored pending invitation token for post-login")
    }

    /// Retrieve and clear any stored pending token (called after login)
    func consumeStoredToken() -> String? {
        guard let token = UserDefaults.standard.string(forKey: "pendingInvitationToken") else {
            return nil
        }
        UserDefaults.standard.removeObject(forKey: "pendingInvitationToken")
        self.logger.info("Consumed stored pending invitation token")
        return token
    }

    // MARK: - URL Parsing

    /// Extract invitation token from a URL
    /// Supports:
    /// - https://app.getmaincourse.com/invite/{token}
    /// - hauptgang://invite/{token}
    nonisolated static func extractInvitationToken(from url: URL) -> String? {
        // Custom scheme: hauptgang://invite/{token}
        if url.scheme == "hauptgang" {
            if url.host == "invite", let token = url.pathComponents.dropFirst().first, !token.isEmpty {
                return token
            }
            // Also handle hauptgang://invite?token={token} style
            if url.host == "invite" {
                return URLComponents(url: url, resolvingAgainstBaseURL: false)?
                    .queryItems?.first(where: { $0.name == "token" })?.value
            }
            return nil
        }

        // Universal link: https://app.getmaincourse.com/invite/{token}
        guard url.scheme == "https" || url.scheme == "http" else { return nil }
        guard let host = url.host, Constants.DeepLinks.universalLinkHosts.contains(host.lowercased()) else { return nil }

        let components = url.pathComponents
        // pathComponents: ["/", "invite", "{token}"]
        guard components.count == 3, components[1] == "invite" else { return nil }
        let token = components[2]
        return token.isEmpty ? nil : token
    }
}

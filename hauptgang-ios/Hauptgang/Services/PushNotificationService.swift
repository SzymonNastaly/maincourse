import Foundation
import os
import UIKit
import UserNotifications

private let logger = Logger(subsystem: "app.hauptgang.ios", category: "PushNotifications")

/// Coordinates APNs registration and uploads device tokens to the Rails backend.
///
/// Flow:
/// 1. App / RootView calls `requestAuthorizationIfNeeded()` after the user is authenticated.
/// 2. On grant, `UIApplication.shared.registerForRemoteNotifications()` is called.
/// 3. The AppDelegate forwards the resulting `Data` token to `handleDeviceToken(_:)`.
/// 4. We POST `{token, environment}` to `/api/v1/device_tokens`, but only when the user
///    has an ApiToken. If the device token arrives before login, we cache it and upload
///    on the next `setAuthenticated(true)` call.
/// 5. On sign-out, `unregister()` deletes the token server-side and clears local cache.
actor PushNotificationService {
    static let shared = PushNotificationService()

    private let api: any APIClientProtocol
    private let defaults: UserDefaults

    private var pendingDeviceToken: String?
    private var isAuthenticated = false

    private static let lastUploadedTokenKey = "push.lastUploadedDeviceToken"
    private static let lastUploadedEnvironmentKey = "push.lastUploadedEnvironment"
    private static let lastUploadedTimeZoneKey = "push.lastUploadedTimeZone"

    private static var environment: String {
        #if DEBUG
        return "sandbox"
        #else
        return "production"
        #endif
    }

    init(api: any APIClientProtocol = APIClient.shared, defaults: UserDefaults = .standard) {
        self.api = api
        self.defaults = defaults
    }

    // MARK: - Public API

    /// Ask iOS for notification authorization. Idempotent — safe to call on every launch.
    /// On grant, kicks off remote-notification registration.
    func requestAuthorizationIfNeeded() async {
        let center = UNUserNotificationCenter.current()
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .badge, .sound])
            guard granted else {
                logger.info("Notification permission denied")
                return
            }
            await MainActor.run {
                UIApplication.shared.registerForRemoteNotifications()
            }
        } catch {
            logger.error("requestAuthorization failed: \(error.localizedDescription)")
        }
    }

    /// Mark the user as authenticated (or not). When transitioning to authenticated,
    /// any pending device token gets uploaded.
    func setAuthenticated(_ value: Bool) async {
        let wasAuthenticated = self.isAuthenticated
        self.isAuthenticated = value

        if value, !wasAuthenticated, let token = self.pendingDeviceToken {
            await self.uploadIfNeeded(token: token)
        }
    }

    /// Called from AppDelegate when APNs hands us a token.
    func handleDeviceToken(_ deviceToken: Data) async {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        self.pendingDeviceToken = hex

        if self.isAuthenticated {
            await self.uploadIfNeeded(token: hex)
        }
    }

    /// Called from AppDelegate when APNs registration fails.
    func handleRegistrationFailure(_ error: Error) {
        logger.error("APNs registration failed: \(error.localizedDescription)")
    }

    /// Best-effort: tell the server to drop the current device token. Called before sign-out.
    func unregister() async {
        defer {
            self.pendingDeviceToken = nil
            self.defaults.removeObject(forKey: Self.lastUploadedTokenKey)
            self.defaults.removeObject(forKey: Self.lastUploadedEnvironmentKey)
            self.defaults.removeObject(forKey: Self.lastUploadedTimeZoneKey)
        }

        guard let token = self.defaults.string(forKey: Self.lastUploadedTokenKey) else { return }

        do {
            try await self.api.requestVoid(
                endpoint: "device_tokens/\(token)",
                method: .delete,
                body: nil,
                queryItems: nil,
                authenticated: true
            )
        } catch {
            logger.error("Failed to delete device token: \(error.localizedDescription)")
        }
    }

    // MARK: - Private

    private func uploadIfNeeded(token: String) async {
        let environment = Self.environment
        let timeZone = TimeZone.current.identifier

        let cachedToken = self.defaults.string(forKey: Self.lastUploadedTokenKey)
        let cachedEnvironment = self.defaults.string(forKey: Self.lastUploadedEnvironmentKey)
        let cachedTimeZone = self.defaults.string(forKey: Self.lastUploadedTimeZoneKey)
        if cachedToken == token, cachedEnvironment == environment, cachedTimeZone == timeZone {
            return
        }

        let body = RegisterRequest(token: token, environment: environment, timeZone: timeZone)

        do {
            let _: RegisterResponse = try await self.api.request(
                endpoint: "device_tokens",
                method: .post,
                body: body,
                queryItems: nil,
                authenticated: true
            )
            self.defaults.set(token, forKey: Self.lastUploadedTokenKey)
            self.defaults.set(environment, forKey: Self.lastUploadedEnvironmentKey)
            self.defaults.set(timeZone, forKey: Self.lastUploadedTimeZoneKey)
            logger.info("Registered device token (environment: \(environment))")
        } catch {
            logger.error("Failed to register device token: \(error.localizedDescription)")
        }
    }
}

// MARK: - Wire formats

private struct RegisterRequest: Encodable {
    let token: String
    let environment: String
    let timeZone: String
}

private struct RegisterResponse: Decodable {
    let id: Int
    let token: String
    let environment: String
}

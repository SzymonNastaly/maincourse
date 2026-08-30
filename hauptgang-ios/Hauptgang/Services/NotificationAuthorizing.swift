import UIKit
import UserNotifications

/// The three pieces of `UNUserNotificationCenter` / `UIApplication` that
/// `PushNotificationService` needs. Behind a protocol so tests can drive the
/// authorization-status branches without a system dialog.
protocol NotificationAuthorizing: Sendable {
    func authorizationStatus() async -> UNAuthorizationStatus
    func requestAuthorization() async throws -> Bool
    func registerForRemoteNotifications() async
}

struct SystemNotificationAuthorizer: NotificationAuthorizing {
    /// Reads the status inside this nonisolated method on purpose: `UNNotificationSettings`
    /// is not `Sendable`, so only the status enum is allowed out.
    func authorizationStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    func requestAuthorization() async throws -> Bool {
        try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])
    }

    func registerForRemoteNotifications() async {
        await MainActor.run {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }
}

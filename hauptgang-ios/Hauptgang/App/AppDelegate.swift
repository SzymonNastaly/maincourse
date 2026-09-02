import UIKit
import UserNotifications

/// Bridges UIKit AppDelegate callbacks (APNs registration and notification taps) into
/// our SwiftUI app. Wired via `@UIApplicationDelegateAdaptor` in `HauptgangApp`.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _: UIApplication,
        didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(
        _: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Task {
            await PushNotificationService.shared.handleDeviceToken(deviceToken)
        }
    }

    func application(
        _: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        Task {
            await PushNotificationService.shared.handleRegistrationFailure(error)
        }
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    /// The user tapped a notification. Fires on cold launch too, after the app is up.
    ///
    /// `nonisolated` because `AppDelegate` is implicitly main-actor isolated (via
    /// `UIApplicationDelegate`) while this protocol requirement's parameters
    /// (`UNUserNotificationCenter`, `UNNotificationResponse`) are not `Sendable` —
    /// marking it `nonisolated` avoids a framework-synthesized hop that would need to
    /// send those non-Sendable values onto the main actor. We parse here, off the main
    /// actor, so only the `Sendable` `LifecycleNotificationPayload` crosses over.
    nonisolated func userNotificationCenter(
        _: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let userInfo = response.notification.request.content.userInfo
        guard let payload = LifecycleNotificationPayload.parse(userInfo) else { return }
        await NotificationRouter.shared.handle(payload)
    }

    // swiftlint:disable async_without_await
    /// Show lifecycle notifications even while the app is in the foreground — they are
    /// timed for a moment the user is deciding what to cook, and silently dropping one
    /// because the app happens to be open would lose the nudge entirely.
    nonisolated func userNotificationCenter(
        _: UNUserNotificationCenter,
        willPresent _: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
    // swiftlint:enable async_without_await
}

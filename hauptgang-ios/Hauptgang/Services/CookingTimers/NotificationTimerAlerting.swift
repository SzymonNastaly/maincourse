import Foundation
import os
import UserNotifications

private let logger = Logger(subsystem: "app.hauptgang.ios", category: "NotificationTimerAlerting")

/// Before iOS 26.1, or when AlarmKit is denied: one time-sensitive notification at
/// the end time. Pausing removes it; resuming schedules it again. The request
/// carries the timer, so a running timer can be rebuilt after the app was killed;
/// a paused one has no request and is not.
@MainActor
final class NotificationTimerAlerting: TimerAlerting {
    var onSystemChange: ((SystemTimerChange) -> Void)?

    private static let identifierPrefix = "recipe-timer-"
    private static let timerKey = "cookingTimer"

    private let tasks = SerialTasks()

    func start(_ timer: CookingTimer) {
        self.schedule(timer)
    }

    func pause(_ timer: CookingTimer) {
        self.cancel(timer.id)
    }

    func resume(_ timer: CookingTimer) {
        self.schedule(timer)
    }

    func reschedule(_ timer: CookingTimer) {
        if timer.isPaused {
            self.cancel(timer.id)
        } else {
            self.schedule(timer)
        }
    }

    func cancel(_ id: UUID) {
        let identifier = Self.identifier(id)
        self.tasks.enqueue {
            let center = UNUserNotificationCenter.current()
            center.removePendingNotificationRequests(withIdentifiers: [identifier])
            center.removeDeliveredNotifications(withIdentifiers: [identifier])
        }
    }

    func restore() async -> [CookingTimer] {
        await self.tasks.drain()
        let requests = await UNUserNotificationCenter.current().pendingNotificationRequests()
        return requests.compactMap { request in
            guard request.identifier.hasPrefix(Self.identifierPrefix),
                  let json = request.content.userInfo[Self.timerKey] as? String
            else { return nil }
            return try? JSONDecoder().decode(CookingTimer.self, from: Data(json.utf8))
        }
    }

    private func schedule(_ timer: CookingTimer) {
        let seconds = timer.remaining(at: Date())
        guard seconds > 0 else { return }
        let content = UNMutableNotificationContent()
        content.title = String(localized: "\(timer.label) is done")
        content.body = String(localized: "\(timer.recipeName) · Step \(timer.stepNumber)")
        content.sound = .default
        content.interruptionLevel = .timeSensitive
        content.threadIdentifier = "recipe-timers"
        if let data = try? JSONEncoder().encode(timer), let json = String(bytes: data, encoding: .utf8) {
            content.userInfo = [Self.timerKey: json]
        }
        let request = UNNotificationRequest(
            identifier: Self.identifier(timer.id),
            content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
        )
        self.tasks.enqueue {
            let center = UNUserNotificationCenter.current()
            do {
                if await center.notificationSettings().authorizationStatus == .notDetermined {
                    _ = try await center.requestAuthorization(options: [.alert, .sound])
                }
                try await center.add(request)
            } catch {
                logger.error("Timer notification failed: \(error.localizedDescription)")
            }
        }
    }

    private static func identifier(_ id: UUID) -> String {
        "\(self.identifierPrefix)\(id.uuidString)"
    }
}

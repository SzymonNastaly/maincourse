import Foundation
@testable import Hauptgang
import UserNotifications

/// Drives `PushNotificationService`'s authorization branches without a system dialog.
actor MockNotificationAuthorizer: NotificationAuthorizing {
    private var status: UNAuthorizationStatus
    private let grants: Bool
    private let throwsOnRequest: Bool

    private(set) var requestCount = 0
    private(set) var registerCount = 0

    init(status: UNAuthorizationStatus, grants: Bool = true, throwsOnRequest: Bool = false) {
        self.status = status
        self.grants = grants
        self.throwsOnRequest = throwsOnRequest
    }

    func authorizationStatus() async -> UNAuthorizationStatus {
        self.status
    }

    func requestAuthorization() async throws -> Bool {
        self.requestCount += 1
        if self.throwsOnRequest {
            throw NSError(domain: "MockNotificationAuthorizer", code: 1)
        }
        // Mirrors iOS: once the user answers, the status is settled for good.
        self.status = self.grants ? .authorized : .denied
        return self.grants
    }

    func registerForRemoteNotifications() async {
        self.registerCount += 1
    }
}

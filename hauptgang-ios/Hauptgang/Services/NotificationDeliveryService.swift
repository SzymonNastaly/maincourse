import Foundation
import os

/// Reports what happened to a delivered lifecycle notification.
protocol NotificationDeliveryServiceProtocol: Sendable {
    func markOpened(deliveryId: Int, actionTaken: String?) async
}

/// Posts to `POST /api/v1/notification_deliveries/:id/opened`.
///
/// Best-effort by design: this is analytics, and a failure here must never surface to
/// the user or block the navigation the tap requested. Nothing retries.
final class NotificationDeliveryService: NotificationDeliveryServiceProtocol, @unchecked Sendable {
    static let shared = NotificationDeliveryService()

    private let api: any APIClientProtocol
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "NotificationDeliveryService")

    init(api: any APIClientProtocol = APIClient.shared) {
        self.api = api
    }

    func markOpened(deliveryId: Int, actionTaken: String? = nil) async {
        do {
            try await self.api.requestVoid(
                endpoint: "notification_deliveries/\(deliveryId)/opened",
                method: .post,
                body: OpenedRequest(actionTaken: actionTaken),
                queryItems: nil,
                authenticated: true
            )
        } catch {
            self.logger.error("Failed to mark delivery \(deliveryId) opened: \(error.localizedDescription)")
        }
    }
}

private struct OpenedRequest: Encodable {
    let actionTaken: String?
}

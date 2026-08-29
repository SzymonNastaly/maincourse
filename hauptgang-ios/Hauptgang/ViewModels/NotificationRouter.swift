import Foundation
import os

/// Where a tapped lifecycle notification should take the user.
enum NotificationRoute: Equatable, Sendable {
    case recipe(id: Int, cookbookId: Int?)
    case shoppingList
}

/// Holds the destination of a tapped notification until the view layer is ready to
/// navigate. Mirrors `DeepLinkRouter`: the tap can arrive before the UI exists (cold
/// launch), so the route is parked rather than acted on immediately.
@MainActor @Observable
final class NotificationRouter {
    static let shared = NotificationRouter()

    private(set) var pendingRoute: NotificationRoute?

    private let deliveryService: any NotificationDeliveryServiceProtocol
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "NotificationRouter")

    init(deliveryService: any NotificationDeliveryServiceProtocol = NotificationDeliveryService.shared) {
        self.deliveryService = deliveryService
    }

    /// Called when the user taps a notification. Parks the destination and reports the
    /// open to the backend.
    func handle(_ userInfo: [AnyHashable: Any]) {
        guard let payload = LifecycleNotificationPayload.parse(userInfo) else {
            self.logger.debug("Ignoring a notification that is not a lifecycle campaign")
            return
        }

        self.handle(payload)
    }

    /// Same as `handle(_ userInfo:)`, but for a caller that has already parsed the
    /// payload — e.g. `AppDelegate`, which parses on a nonisolated delegate callback so
    /// only the `Sendable` payload (not the raw `[AnyHashable: Any]`) crosses onto the
    /// main actor.
    func handle(_ payload: LifecycleNotificationPayload) {
        self.logger.info("Tapped \(payload.campaign) notification (delivery \(payload.deliveryId))")

        if let recipeId = payload.recipeId {
            self.pendingRoute = .recipe(id: recipeId, cookbookId: payload.cookbookId)
        } else {
            self.pendingRoute = .shoppingList
        }

        let service = self.deliveryService
        let deliveryId = payload.deliveryId
        Task { await service.markOpened(deliveryId: deliveryId, actionTaken: nil) }
    }

    /// Take the pending route, if any. Clears it so a route is never navigated twice.
    func consumeRoute() -> NotificationRoute? {
        defer { pendingRoute = nil }
        return self.pendingRoute
    }
}

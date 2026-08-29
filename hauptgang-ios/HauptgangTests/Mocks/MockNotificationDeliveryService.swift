import Foundation
@testable import Hauptgang

final class MockNotificationDeliveryService: NotificationDeliveryServiceProtocol, @unchecked Sendable {
    private let lock = NSLock()
    private var _opened: [(deliveryId: Int, actionTaken: String?)] = []

    var opened: [(deliveryId: Int, actionTaken: String?)] {
        self.lock.withLock { self._opened }
    }

    func markOpened(deliveryId: Int, actionTaken: String?) async {
        self.lock.withLock { self._opened.append((deliveryId, actionTaken)) }
    }
}

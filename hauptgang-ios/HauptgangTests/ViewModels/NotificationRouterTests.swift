@testable import Hauptgang
import XCTest

@MainActor
final class NotificationRouterTests: XCTestCase {
    func testARecipeCampaignRoutesToThatRecipe() async {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle([
            "campaign": "import_follow_up", "delivery_id": 42, "recipe_id": 7, "cookbook_id": 3
        ])

        XCTAssertEqual(router.pendingRoute, .recipe(id: 7, cookbookId: 3))
    }

    func testTheStaleListCampaignRoutesToTheShoppingList() async {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle(["campaign": "stale_shopping_list", "delivery_id": 43, "cookbook_id": 3])

        XCTAssertEqual(router.pendingRoute, .shoppingList)
    }

    func testHandlingReportsTheDeliveryAsOpened() async throws {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle(["campaign": "resurface", "delivery_id": 44, "recipe_id": 9])

        // handle() fires the report off the main actor; give it a turn to land.
        try await Task.sleep(for: .milliseconds(50))
        XCTAssertEqual(deliveries.opened.map(\.deliveryId), [44])
    }

    func testANonLifecycleNotificationIsIgnored() async {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle(["aps": ["alert": "hi"]])

        XCTAssertNil(router.pendingRoute)
    }

    func testConsumingClearsTheRoute() async {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle(["campaign": "resurface", "delivery_id": 44, "recipe_id": 9])

        XCTAssertEqual(router.consumeRoute(), .recipe(id: 9, cookbookId: nil))
        XCTAssertNil(router.pendingRoute, "a route must not be delivered twice")
        XCTAssertNil(router.consumeRoute())
    }
}

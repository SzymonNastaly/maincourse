@testable import Hauptgang
import XCTest

final class LifecycleNotificationPayloadTests: XCTestCase {
    func testParsesAFullPayload() {
        let userInfo: [AnyHashable: Any] = [
            "campaign": "import_follow_up",
            "delivery_id": 42,
            "recipe_id": 7,
            "cookbook_id": 3
        ]

        let payload = LifecycleNotificationPayload.parse(userInfo)

        XCTAssertEqual(payload, LifecycleNotificationPayload(
            campaign: "import_follow_up", deliveryId: 42, recipeId: 7, cookbookId: 3
        ))
    }

    func testParsesAPayloadWithoutARecipe() {
        // stale_shopping_list carries no recipe_id — the key is omitted, not null.
        let userInfo: [AnyHashable: Any] = [
            "campaign": "stale_shopping_list",
            "delivery_id": 43,
            "cookbook_id": 3
        ]

        let payload = LifecycleNotificationPayload.parse(userInfo)

        XCTAssertEqual(payload?.campaign, "stale_shopping_list")
        XCTAssertEqual(payload?.deliveryId, 43)
        XCTAssertNil(payload?.recipeId)
        XCTAssertEqual(payload?.cookbookId, 3)
    }

    func testParsesNumbersDeliveredAsStrings() {
        // APNs JSON round-trips through NSNumber, but be forgiving: a value that arrives
        // as a string must not silently drop the whole notification.
        let userInfo: [AnyHashable: Any] = [
            "campaign": "resurface",
            "delivery_id": "44",
            "recipe_id": "9"
        ]

        let payload = LifecycleNotificationPayload.parse(userInfo)

        XCTAssertEqual(payload?.deliveryId, 44)
        XCTAssertEqual(payload?.recipeId, 9)
    }

    func testRejectsANonLifecycleNotification() {
        XCTAssertNil(LifecycleNotificationPayload.parse(["aps": ["alert": "hi"]]))
        XCTAssertNil(LifecycleNotificationPayload.parse(["campaign": "import_follow_up"]))
        XCTAssertNil(LifecycleNotificationPayload.parse(["delivery_id": 42]))
    }
}

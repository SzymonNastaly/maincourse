import Foundation

/// The custom keys `Notifications::Deliver` attaches to a lifecycle push, alongside `aps`.
///
/// Keys are omitted server-side when nil (`.compact`), so `recipeId` is absent for the
/// stale-shopping-list campaign. Parsing is total: anything that is not a lifecycle
/// notification returns nil rather than throwing.
struct LifecycleNotificationPayload: Equatable, Sendable {
    let campaign: String
    let deliveryId: Int
    let recipeId: Int?
    let cookbookId: Int?

    static func parse(_ userInfo: [AnyHashable: Any]) -> LifecycleNotificationPayload? {
        guard let campaign = userInfo["campaign"] as? String, !campaign.isEmpty else { return nil }
        guard let deliveryId = Self.int(userInfo["delivery_id"]) else { return nil }

        return LifecycleNotificationPayload(
            campaign: campaign,
            deliveryId: deliveryId,
            recipeId: Self.int(userInfo["recipe_id"]),
            cookbookId: Self.int(userInfo["cookbook_id"])
        )
    }

    private static func int(_ value: Any?) -> Int? {
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String { return Int(string) }
        return nil
    }
}

import Foundation

struct ShoppingListItemResponse: Codable, Identifiable {
    let id: Int
    let clientId: String
    let name: String
    let details: String?
    let checkedAt: Date?
    let sourceRecipeId: Int?
    let createdAt: Date
    let updatedAt: Date
    var category: String?
    var canonicalName: String?
    /// True while the server still wants to confirm `category` in the background.
    var categoryPending: Bool?
}

struct ShoppingListItemCreate: Codable {
    let clientId: String
    let name: String
    let details: String?
    let checkedAt: Date?
    let sourceRecipeId: Int?
    /// Hints the server keeps when it has nothing better.
    var category: String?
    var canonicalName: String?
}

struct BulkCreateShoppingListItemsRequest: Codable {
    let items: [ShoppingListItemCreate]
    let clearExisting: Bool
}

struct UpdateShoppingListItemRequest: Codable {
    let checked: Bool
    let checkedAt: Date?
    let createdAt: Date?
}

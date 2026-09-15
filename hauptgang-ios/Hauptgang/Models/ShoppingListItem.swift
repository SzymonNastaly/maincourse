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
}

struct ShoppingListItemCreate: Codable {
    let clientId: String
    let name: String
    let details: String?
    let checkedAt: Date?
    let sourceRecipeId: Int?
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

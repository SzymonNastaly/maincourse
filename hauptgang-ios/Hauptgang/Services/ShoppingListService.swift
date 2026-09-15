import Foundation
import os

protocol ShoppingListServiceProtocol: Sendable {
    func fetchItems() async throws -> [ShoppingListItemResponse]
    func createItems(
        _ items: [ShoppingListItemCreate],
        clearExisting: Bool
    ) async throws -> [ShoppingListItemResponse]
    func updateItem(id: Int, checked: Bool, checkedAt: Date?, createdAt: Date?) async throws -> ShoppingListItemResponse
    func deleteItem(id: Int) async throws
    func deleteAllItems() async throws
}

final class ShoppingListService: ShoppingListServiceProtocol, @unchecked Sendable {
    static let shared = ShoppingListService()

    private let api = APIClient.shared
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "ShoppingListService")

    private init() {}

    func fetchItems() async throws -> [ShoppingListItemResponse] {
        self.logger.info("Fetching shopping list items from API")

        let items: [ShoppingListItemResponse] = try await api.request(
            endpoint: "shopping_list_items",
            method: .get,
            authenticated: true
        )

        self.logger.info("Fetched \(items.count) shopping list items from API")
        return items
    }

    func createItems(
        _ items: [ShoppingListItemCreate],
        clearExisting: Bool = false
    ) async throws -> [ShoppingListItemResponse] {
        self.logger.info("Creating \(items.count) shopping list items")

        let request = BulkCreateShoppingListItemsRequest(items: items, clearExisting: clearExisting)
        let created: [ShoppingListItemResponse] = try await api.request(
            endpoint: "shopping_list_items",
            method: .post,
            body: request,
            authenticated: true
        )

        self.logger.info("Created \(created.count) shopping list items")
        return created
    }

    func updateItem(
        id: Int,
        checked: Bool,
        checkedAt: Date?,
        createdAt: Date? = nil
    ) async throws -> ShoppingListItemResponse {
        let request = UpdateShoppingListItemRequest(checked: checked, checkedAt: checkedAt, createdAt: createdAt)
        let item: ShoppingListItemResponse = try await api.request(
            endpoint: "shopping_list_items/\(id)",
            method: .patch,
            body: request,
            authenticated: true
        )

        self.logger.info("Updated shopping list item \(id)")
        return item
    }

    func deleteItem(id: Int) async throws {
        self.logger.info("Deleting shopping list item \(id)")

        try await self.api.requestVoid(
            endpoint: "shopping_list_items/\(id)",
            method: .delete,
            authenticated: true
        )
    }

    func deleteAllItems() async throws {
        self.logger.info("Deleting all shopping list items")

        try await self.api.requestVoid(
            endpoint: "shopping_list_items/destroy_all",
            method: .delete,
            authenticated: true
        )
    }
}

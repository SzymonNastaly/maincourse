import Foundation
@testable import Hauptgang

final class MockShoppingListService: ShoppingListServiceProtocol, @unchecked Sendable {
    var fetchResult: [ShoppingListItemResponse] = []
    var createResult: [ShoppingListItemResponse] = []
    var createHandler: (([ShoppingListItemCreate], Bool) async throws -> [ShoppingListItemResponse])?
    var updateResult: ShoppingListItemResponse?
    var deleteError: Error?

    var fetchCallCount = 0
    var createCallCount = 0
    var updateCallCount = 0
    var deleteCallCount = 0

    var lastCreatedPayload: [ShoppingListItemCreate] = []
    var lastCreateClearedExisting = false
    var lastUpdatedId: Int?
    var lastDeletedId: Int?

    var shouldThrow = false
    var errorToThrow: Error = APIError.networkError(URLError(.notConnectedToInternet))

    func fetchItems() async throws -> [ShoppingListItemResponse] {
        self.fetchCallCount += 1
        if self.shouldThrow {
            throw self.errorToThrow
        }
        return self.fetchResult
    }

    func createItems(
        _ items: [ShoppingListItemCreate],
        clearExisting: Bool
    ) async throws -> [ShoppingListItemResponse] {
        self.createCallCount += 1
        self.lastCreatedPayload = items
        self.lastCreateClearedExisting = clearExisting
        if let createHandler {
            return try await createHandler(items, clearExisting)
        }
        if self.shouldThrow {
            throw self.errorToThrow
        }
        return self.createResult
    }

    func updateItem(
        id: Int,
        checked _: Bool,
        checkedAt _: Date?,
        createdAt _: Date?
    ) async throws -> ShoppingListItemResponse {
        self.updateCallCount += 1
        self.lastUpdatedId = id
        if self.shouldThrow {
            throw self.errorToThrow
        }
        guard let result = self.updateResult else { throw MockShoppingListServiceError.notConfigured }
        return result
    }

    func deleteItem(id: Int) async throws {
        self.deleteCallCount += 1
        self.lastDeletedId = id
        if let error = self.deleteError {
            throw error
        }
        if self.shouldThrow {
            throw self.errorToThrow
        }
    }

    func deleteAllItems() async throws {
        if self.shouldThrow {
            throw self.errorToThrow
        }
    }
}

enum MockShoppingListServiceError: Error {
    case notConfigured
}

import Foundation
@testable import Hauptgang
import SwiftData

@MainActor
final class MockShoppingListRepository: ShoppingListRepositoryProtocol {
    var configuredCalled = false
    var items: [PersistedShoppingListItem] = []
    var addedLocalItems: [[ShoppingListItemCreate]] = []
    var updatedItems: [(clientId: String, checkedAt: Date?)] = []
    var deletedClientIds: [String] = []
    var deleteStaleItemsCalled = false
    var clearAllCalled = false

    var shouldThrowOnSave = false
    var shouldThrowOnGet = false

    func configure(modelContext _: ModelContext) {
        self.configuredCalled = true
    }

    func getAllItems() throws -> [PersistedShoppingListItem] {
        if self.shouldThrowOnGet {
            throw MockShoppingListRepoError.testError
        }
        return self.items
    }

    /// Simulates the real repository: prunes orphaned synced items when told to, then upserts.
    func saveItems(_ serverItems: [ShoppingListItemResponse], pruneOrphans: Bool) throws {
        if self.shouldThrowOnSave {
            throw MockShoppingListRepoError.testError
        }

        if pruneOrphans {
            let serverClientIds = Set(serverItems.map(\.clientId))
            self.items.removeAll { $0.syncState == .synced && !serverClientIds.contains($0.clientId) }
        }

        for response in serverItems {
            if let local = self.items.first(where: { $0.clientId == response.clientId }) {
                local.serverId = response.id
                local.name = response.name
                local.details = response.details
                local.createdAt = response.createdAt
                local.updatedAt = response.updatedAt
                if local.syncState != .pendingUpdate {
                    local.checkedAt = response.checkedAt
                }
                local.syncState = .synced
            } else {
                let newItem = PersistedShoppingListItem(
                    clientId: response.clientId,
                    name: response.name,
                    details: response.details,
                    checkedAt: response.checkedAt,
                    sourceRecipeId: response.sourceRecipeId,
                    createdAt: response.createdAt,
                    updatedAt: response.updatedAt,
                    serverId: response.id,
                    syncState: .synced
                )
                self.items.append(newItem)
            }
        }
    }

    func addLocalItems(_ items: [ShoppingListItemCreate]) throws {
        if self.shouldThrowOnSave {
            throw MockShoppingListRepoError.testError
        }
        self.addedLocalItems.append(items)
        for item in items {
            let persisted = PersistedShoppingListItem(
                clientId: item.clientId,
                name: item.name,
                details: item.details,
                checkedAt: item.checkedAt,
                sourceRecipeId: item.sourceRecipeId,
                syncState: .pendingCreate
            )
            self.items.append(persisted)
        }
    }

    func updateItem(clientId: String, checkedAt: Date?) throws {
        if self.shouldThrowOnSave {
            throw MockShoppingListRepoError.testError
        }
        self.updatedItems.append((clientId: clientId, checkedAt: checkedAt))
    }

    func deleteItem(clientId: String) throws {
        self.deletedClientIds.append(clientId)
        self.items.removeAll { $0.clientId == clientId }
    }

    func updateItemFromServer(clientId: String, response: ShoppingListItemResponse) throws {
        if self.shouldThrowOnSave {
            throw MockShoppingListRepoError.testError
        }
        guard let local = self.items.first(where: { $0.clientId == clientId }) else { return }
        local.serverId = response.id
        local.name = response.name
        local.details = response.details
        local.checkedAt = response.checkedAt
        local.createdAt = response.createdAt
        local.updatedAt = response.updatedAt
        local.syncState = .synced
    }

    func deleteStaleItems() throws {
        self.deleteStaleItemsCalled = true
    }

    func getPendingCreates() throws -> [PersistedShoppingListItem] {
        if self.shouldThrowOnGet {
            throw MockShoppingListRepoError.testError
        }
        return self.items.filter { $0.syncState == .pendingCreate }
    }

    func getPendingUpdates() throws -> [PersistedShoppingListItem] {
        if self.shouldThrowOnGet {
            throw MockShoppingListRepoError.testError
        }
        return self.items.filter { $0.syncState == .pendingUpdate }
    }

    func clearAll() throws {
        self.clearAllCalled = true
        self.items = []
    }
}

enum MockShoppingListRepoError: Error {
    case testError
}

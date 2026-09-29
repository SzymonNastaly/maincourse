import Foundation
import SwiftData

enum ShoppingListSyncState: String, Codable {
    case pendingCreate = "pending_create"
    case pendingUpdate = "pending_update"
    case synced
}

@Model
final class PersistedShoppingListItem {
    /// Composite unique key: "\(cookbookId)|\(clientId)" — allows the same clientId
    /// to exist in different cookbooks (server uniqueness is per-cookbook).
    @Attribute(.unique) var scopedClientId: String
    var clientId: String
    var cookbookId: Int
    var serverId: Int?
    var name: String
    var details: String?
    var checkedAt: Date?
    var sourceRecipeId: Int?
    /// Server category value (see `ShoppingCategory`); nil until known.
    var category: String?
    var canonicalName: String?
    var createdAt: Date
    var updatedAt: Date
    var syncStateRaw: String

    var syncState: ShoppingListSyncState {
        get { ShoppingListSyncState(rawValue: self.syncStateRaw) ?? .synced }
        set { self.syncStateRaw = newValue.rawValue }
    }

    var isChecked: Bool {
        self.checkedAt != nil
    }

    var isStale: Bool {
        guard let checkedAt else { return false }
        return checkedAt < Date().addingTimeInterval(-3600)
    }

    init(
        clientId: String,
        cookbookId: Int = 0,
        name: String,
        details: String? = nil,
        checkedAt: Date? = nil,
        sourceRecipeId: Int? = nil,
        category: String? = nil,
        canonicalName: String? = nil,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        serverId: Int? = nil,
        syncState: ShoppingListSyncState = .pendingCreate
    ) {
        self.scopedClientId = "\(cookbookId)|\(clientId)"
        self.clientId = clientId
        self.cookbookId = cookbookId
        self.name = name
        self.details = details
        self.checkedAt = checkedAt
        self.sourceRecipeId = sourceRecipeId
        self.category = category
        self.canonicalName = canonicalName
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.serverId = serverId
        self.syncStateRaw = syncState.rawValue
    }

    convenience init(from response: ShoppingListItemResponse, cookbookId: Int = 0) {
        self.init(
            clientId: response.clientId,
            cookbookId: cookbookId,
            name: response.name,
            details: response.details,
            checkedAt: response.checkedAt,
            sourceRecipeId: response.sourceRecipeId,
            category: response.category,
            canonicalName: response.canonicalName,
            createdAt: response.createdAt,
            updatedAt: response.updatedAt,
            serverId: response.id,
            syncState: .synced
        )
    }

    func update(from response: ShoppingListItemResponse) {
        self.serverId = response.id
        self.name = response.name
        self.details = response.details
        self.checkedAt = response.checkedAt
        self.sourceRecipeId = response.sourceRecipeId
        self.createdAt = response.createdAt
        self.updatedAt = response.updatedAt
        self.applyCategory(from: response)
        self.syncState = .synced
    }

    /// Adopts the server's category, keeping the local guess while the server has
    /// none yet (an older server, or an item it could not place).
    func applyCategory(from response: ShoppingListItemResponse) {
        guard let category = response.category else { return }
        self.category = category
        self.canonicalName = response.canonicalName
    }
}

import Foundation
import os
import SwiftData

enum ShoppingListRepositoryError: Error, LocalizedError {
    case notConfigured

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            String(localized: "Repository not configured with model context")
        }
    }
}

@MainActor
protocol ShoppingListRepositoryProtocol {
    func configure(modelContext: ModelContext)
    func getAllItems() throws -> [PersistedShoppingListItem]
    func saveItems(_ items: [ShoppingListItemResponse], pruneOrphans: Bool) throws
    func addLocalItems(_ items: [ShoppingListItemCreate]) throws
    func updateItem(clientId: String, checkedAt: Date?) throws
    func deleteItem(clientId: String) throws
    func updateItemFromServer(clientId: String, response: ShoppingListItemResponse) throws
    func deleteStaleItems() throws
    func getPendingCreates() throws -> [PersistedShoppingListItem]
    func getPendingUpdates() throws -> [PersistedShoppingListItem]
    func replaceAll(with items: [ShoppingListItemResponse]) throws
    func clearAll() throws
}

@MainActor
final class ShoppingListRepository: ShoppingListRepositoryProtocol {
    private var modelContext: ModelContext?
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "ShoppingListRepository")

    func configure(modelContext: ModelContext) {
        self.modelContext = modelContext
        self.logger.info("ShoppingListRepository configured with model context")
    }

    func getAllItems() throws -> [PersistedShoppingListItem] {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        let descriptor = FetchDescriptor<PersistedShoppingListItem>()
        let items = try modelContext.fetch(descriptor)

        return items.sorted { lhs, rhs in
            switch (lhs.checkedAt, rhs.checkedAt) {
            case (nil, nil):
                lhs.createdAt < rhs.createdAt
            case (nil, _):
                true
            case (_, nil):
                false
            case let (left?, right?):
                left < right
            }
        }
    }

    func saveItems(_ items: [ShoppingListItemResponse], pruneOrphans: Bool = true) throws {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        if pruneOrphans {
            let serverClientIds = Set(items.map(\.clientId))

            let allDescriptor = FetchDescriptor<PersistedShoppingListItem>()
            let localItems = try modelContext.fetch(allDescriptor)

            for local in localItems where local.syncState == .synced && !serverClientIds.contains(local.clientId) {
                modelContext.delete(local)
            }
        }

        for response in items {
            if let local = try fetchItem(clientId: response.clientId) {
                switch local.syncState {
                case .pendingCreate:
                    local.serverId = response.id
                    local.createdAt = response.createdAt
                    local.updatedAt = response.updatedAt
                    if local.checkedAt == nil {
                        local.checkedAt = response.checkedAt
                    }
                    local.syncState = .synced
                case .pendingUpdate:
                    local.serverId = response.id
                    local.name = response.name
                    local.details = response.details
                    local.checkedAt = response.checkedAt
                    local.createdAt = response.createdAt
                    local.updatedAt = response.updatedAt
                    local.syncState = .synced
                case .synced:
                    local.update(from: response)
                }
            } else {
                let newItem = PersistedShoppingListItem(from: response)
                modelContext.insert(newItem)
            }
        }

        try modelContext.save()
    }

    func updateItemFromServer(clientId: String, response: ShoppingListItemResponse) throws {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        guard let local = try fetchItem(clientId: clientId) else { return }

        local.serverId = response.id
        local.name = response.name
        local.details = response.details
        local.checkedAt = response.checkedAt
        local.createdAt = response.createdAt
        local.updatedAt = response.updatedAt
        local.syncState = .synced

        try modelContext.save()
    }

    func addLocalItems(_ items: [ShoppingListItemCreate]) throws {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        for item in items {
            let local = PersistedShoppingListItem(
                clientId: item.clientId,
                name: item.name,
                details: item.details,
                checkedAt: item.checkedAt,
                sourceRecipeId: item.sourceRecipeId,
                syncState: .pendingCreate
            )
            modelContext.insert(local)
        }

        try modelContext.save()
    }

    func updateItem(clientId: String, checkedAt: Date?) throws {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        guard let item = try fetchItem(clientId: clientId) else { return }

        item.checkedAt = checkedAt
        if checkedAt == nil {
            item.createdAt = Date()
        }
        item.updatedAt = Date()
        if item.syncState != .pendingCreate {
            item.syncState = .pendingUpdate
        }

        try modelContext.save()
    }

    func deleteItem(clientId: String) throws {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        guard let item = try fetchItem(clientId: clientId) else { return }
        modelContext.delete(item)
        try modelContext.save()
    }

    func deleteStaleItems() throws {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        let descriptor = FetchDescriptor<PersistedShoppingListItem>()
        let items = try modelContext.fetch(descriptor)

        for item in items where item.syncState == .synced && item.isStale {
            modelContext.delete(item)
        }

        try modelContext.save()
    }

    func getPendingCreates() throws -> [PersistedShoppingListItem] {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        let descriptor = FetchDescriptor<PersistedShoppingListItem>(
            predicate: #Predicate { $0.syncStateRaw == "pending_create" }
        )
        return try modelContext.fetch(descriptor)
    }

    func getPendingUpdates() throws -> [PersistedShoppingListItem] {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        let descriptor = FetchDescriptor<PersistedShoppingListItem>(
            predicate: #Predicate { $0.syncStateRaw == "pending_update" }
        )
        return try modelContext.fetch(descriptor)
    }

    func replaceAll(with items: [ShoppingListItemResponse]) throws {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        try modelContext.transaction {
            let descriptor = FetchDescriptor<PersistedShoppingListItem>()
            for item in try modelContext.fetch(descriptor) {
                modelContext.delete(item)
            }
            for response in items {
                modelContext.insert(PersistedShoppingListItem(from: response))
            }
            try modelContext.save()
        }
    }

    func clearAll() throws {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        let descriptor = FetchDescriptor<PersistedShoppingListItem>()
        let items = try modelContext.fetch(descriptor)
        for item in items {
            modelContext.delete(item)
        }
        try modelContext.save()
    }

    private func fetchItem(clientId: String) throws -> PersistedShoppingListItem? {
        guard let modelContext else {
            throw ShoppingListRepositoryError.notConfigured
        }

        let descriptor = FetchDescriptor<PersistedShoppingListItem>(
            predicate: #Predicate { $0.clientId == clientId }
        )

        return try modelContext.fetch(descriptor).first
    }
}

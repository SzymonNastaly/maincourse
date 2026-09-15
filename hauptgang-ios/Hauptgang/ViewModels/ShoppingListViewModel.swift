import Foundation
import os
import SwiftData
import SwiftUI

@MainActor @Observable
final class ShoppingListViewModel {
    private static let recipeAdditionReviewAge: TimeInterval = 36 * 60 * 60

    private(set) var items: [PersistedShoppingListItem] = []
    private(set) var isSyncing = false
    private(set) var hasCompletedShoppingListRefresh = false
    var didReceiveForbidden = false
    var uncheckedItems: [PersistedShoppingListItem] {
        self.items.filter { !$0.isChecked }
    }

    var checkedItems: [PersistedShoppingListItem] {
        self.items.filter(\.isChecked)
    }

    func needsReviewBeforeAddingRecipeIngredients(now: Date = Date()) -> Bool {
        let cutoff = now.addingTimeInterval(-Self.recipeAdditionReviewAge)
        return self.items.contains { $0.createdAt < cutoff }
    }

    private let repository: ShoppingListRepositoryProtocol
    private let service: ShoppingListServiceProtocol
    private let networkGate = ShoppingListNetworkGate.shared
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "ShoppingListViewModel")

    init(
        repository: ShoppingListRepositoryProtocol? = nil,
        service: ShoppingListServiceProtocol = ShoppingListService.shared
    ) {
        self.repository = repository ?? ShoppingListRepository()
        self.service = service
    }

    func configure(modelContext: ModelContext) {
        self.repository.configure(modelContext: modelContext)
        self.loadCachedItems()
    }

    func refresh() async {
        guard !self.isSyncing else { return }

        self.isSyncing = true
        self.hasCompletedShoppingListRefresh = false
        defer {
            self.isSyncing = false
            self.hasCompletedShoppingListRefresh = true
        }

        await self.syncPendingChanges()

        do {
            try await self.networkGate.withLock {
                let apiItems = try await service.fetchItems()
                try self.repository.saveItems(apiItems, pruneOrphans: true)
                try self.repository.deleteStaleItems()
                self.loadCachedItems()
            }
        } catch {
            self.logger.error("Failed to refresh shopping list: \(error.localizedDescription)")
            if let apiError = error as? APIError, case .forbidden = apiError {
                self.didReceiveForbidden = true
            }
        }
    }

    func addIngredientsFromRecipe(_ items: [ShoppingListDraftItem], sourceRecipeId: Int?) {
        let newItems = self.recipeIngredientCreates(items, sourceRecipeId: sourceRecipeId)
        guard !newItems.isEmpty else { return }

        do {
            try self.repository.addLocalItems(newItems)
            self.loadCachedItems()
            Task { await self.syncPendingChanges() }
        } catch {
            self.logger.error("Failed to add ingredients: \(error.localizedDescription)")
        }
    }

    func replaceListWithIngredientsFromRecipe(
        _ items: [ShoppingListDraftItem],
        sourceRecipeId: Int?
    ) async -> Bool {
        let newItems = self.recipeIngredientCreates(items, sourceRecipeId: sourceRecipeId)
        guard !newItems.isEmpty else { return false }

        do {
            return try await self.networkGate.withLock {
                let created = try await self.service.createItems(newItems, clearExisting: true)
                do {
                    try self.repository.replaceAll(with: created)
                    self.loadCachedItems()
                } catch {
                    self.logger
                        .error(
                            "Server replaced shopping list but local cache update failed: \(error.localizedDescription)"
                        )
                }
                return true
            }
        } catch {
            self.logger.error("Failed to replace shopping list: \(error.localizedDescription)")
            return false
        }
    }

    func addCustomItem(_ text: String) {
        let name = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }

        let newItem = ShoppingListItemCreate(
            clientId: UUID().uuidString,
            name: name,
            details: nil,
            checkedAt: nil,
            sourceRecipeId: nil
        )

        do {
            try self.repository.addLocalItems([newItem])
            self.loadCachedItems()
            Task { await self.syncPendingChanges() }
        } catch {
            self.logger.error("Failed to add custom item: \(error.localizedDescription)")
        }
    }

    func toggleItem(_ item: PersistedShoppingListItem) {
        let isChecking = !item.isChecked
        let newCheckedAt = isChecking ? Date() : nil
        let clientId = item.clientId

        do {
            try self.repository.updateItem(clientId: clientId, checkedAt: newCheckedAt)

            if isChecking {
                withAnimation(.snappy(duration: 0.35)) {
                    self.loadCachedItems()
                }
            } else {
                withAnimation(.snappy(duration: 0.35)) {
                    self.loadCachedItems()
                }
            }

            Task { await self.syncPendingChanges() }
        } catch {
            self.logger.error("Failed to update item: \(error.localizedDescription)")
        }
    }

    func deleteItem(_ item: PersistedShoppingListItem) {
        let serverId = item.serverId

        do {
            try self.repository.deleteItem(clientId: item.clientId)
            self.loadCachedItems()
        } catch {
            self.logger.error("Failed to delete item locally: \(error.localizedDescription)")
        }

        guard let serverId else { return }
        Task {
            do {
                try await self.networkGate.withLock {
                    try await self.service.deleteItem(id: serverId)
                }
            } catch {
                self.logger.error("Failed to delete item from server: \(error.localizedDescription)")
            }
        }
    }

    /// Cancel in-flight work and clear data for a cookbook switch
    func resetForCookbookSwitch() {
        self.items = []
        self.isSyncing = false
        self.hasCompletedShoppingListRefresh = false
    }

    func removeAllItems() async {
        do {
            try await self.networkGate.withLock {
                try await self.service.deleteAllItems()
                try self.repository.clearAll()
                self.items = []
            }
        } catch {
            self.logger.error("Failed to delete all shopping list items: \(error.localizedDescription)")
        }
    }

    func clearData() {
        do {
            try self.repository.clearAll()
            self.items = []
        } catch {
            self.logger.error("Failed to clear shopping list data: \(error.localizedDescription)")
        }
    }

    private func syncPendingChanges() async {
        do {
            try await self.networkGate.withLock {
                let pendingCreates = try repository.getPendingCreates()
                if !pendingCreates.isEmpty {
                    let payload = pendingCreates.map {
                        ShoppingListItemCreate(
                            clientId: $0.clientId,
                            name: $0.name,
                            details: $0.details,
                            checkedAt: $0.checkedAt,
                            sourceRecipeId: $0.sourceRecipeId
                        )
                    }

                    let created = try await service.createItems(payload, clearExisting: false)
                    try self.repository.saveItems(created, pruneOrphans: false)
                }

                let pendingUpdates = try repository.getPendingUpdates()
                for item in pendingUpdates {
                    guard let serverId = item.serverId else { continue }
                    do {
                        let updated = try await service.updateItem(
                            id: serverId,
                            checked: item.isChecked,
                            checkedAt: item.checkedAt,
                            createdAt: item.createdAt
                        )
                        try self.repository.updateItemFromServer(clientId: item.clientId, response: updated)
                    } catch APIError.notFound {
                        // Item was deleted on server (e.g., stale cleanup) — remove local copy
                        self.logger.info("Item \(serverId) not found on server, removing local copy")
                        try? self.repository.deleteItem(clientId: item.clientId)
                    }
                }
            }
        } catch {
            self.logger.error("Failed to sync pending changes: \(error.localizedDescription)")
        }
    }

    private func loadCachedItems() {
        do {
            self.items = try self.repository.getAllItems()
        } catch {
            self.logger.error("Failed to load cached shopping list items: \(error.localizedDescription)")
        }
    }

    private func recipeIngredientCreates(
        _ items: [ShoppingListDraftItem],
        sourceRecipeId: Int?
    ) -> [ShoppingListItemCreate] {
        items.compactMap { item in
            let name = item.name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty else { return nil }
            let trimmedDetails = item.details?.trimmingCharacters(in: .whitespacesAndNewlines)
            let details = (trimmedDetails?.isEmpty == false) ? trimmedDetails : nil

            return ShoppingListItemCreate(
                clientId: UUID().uuidString,
                name: name,
                details: details,
                checkedAt: nil,
                sourceRecipeId: sourceRecipeId
            )
        }
    }
}

@MainActor
private final class ShoppingListNetworkGate {
    static let shared = ShoppingListNetworkGate()

    private var isLocked = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func withLock<Value>(_ operation: () async throws -> Value) async rethrows -> Value {
        await self.acquire()
        defer { self.release() }
        return try await operation()
    }

    private func acquire() async {
        if !self.isLocked {
            self.isLocked = true
            return
        }

        await withCheckedContinuation { continuation in
            self.waiters.append(continuation)
        }
    }

    private func release() {
        if self.waiters.isEmpty {
            self.isLocked = false
        } else {
            self.waiters.removeFirst().resume()
        }
    }
}

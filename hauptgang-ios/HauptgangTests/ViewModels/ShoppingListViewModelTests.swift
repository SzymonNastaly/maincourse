import Foundation
@testable import Hauptgang
import SwiftData
import Testing

@MainActor
struct ShoppingListViewModelTests {
    private func makeResponse(
        id: Int = 1,
        clientId: String = UUID().uuidString,
        name: String = "Milk",
        details: String? = nil,
        checkedAt: Date? = nil,
        sourceRecipeId: Int? = nil,
        createdAt: Date = Date()
    ) -> ShoppingListItemResponse {
        ShoppingListItemResponse(
            id: id,
            clientId: clientId,
            name: name,
            details: details,
            checkedAt: checkedAt,
            sourceRecipeId: sourceRecipeId,
            createdAt: createdAt,
            updatedAt: Date()
        )
    }

    private func makePersisted(
        clientId: String = UUID().uuidString,
        name: String = "Eggs",
        checkedAt: Date? = nil,
        serverId: Int? = 1,
        syncState: ShoppingListSyncState = .synced,
        createdAt: Date = Date()
    ) -> PersistedShoppingListItem {
        PersistedShoppingListItem(
            clientId: clientId,
            name: name,
            checkedAt: checkedAt,
            createdAt: createdAt,
            serverId: serverId,
            syncState: syncState
        )
    }

    private func makeVM(
        repo: MockShoppingListRepository = MockShoppingListRepository(),
        service: MockShoppingListService = MockShoppingListService()
    ) -> (ShoppingListViewModel, MockShoppingListRepository, MockShoppingListService) {
        let vm = ShoppingListViewModel(repository: repo, service: service)
        return (vm, repo, service)
    }

    // MARK: - Refresh

    @Test func refresh_fetchesAndSavesItems() async {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()
        service.fetchResult = [self.makeResponse(id: 1, clientId: "a", name: "Butter")]

        let vm = ShoppingListViewModel(repository: repo, service: service)
        await vm.refresh()

        #expect(service.fetchCallCount == 1)
        #expect(repo.items.count == 1)
        #expect(repo.items.first?.name == "Butter")
        #expect(repo.deleteStaleItemsCalled == true)
        #expect(vm.isSyncing == false)
        #expect(vm.hasCompletedShoppingListRefresh)
    }

    @Test func refresh_prunesOrphanedSyncedItems() async {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()

        // Local has an item that the server no longer returns
        let orphan = self.makePersisted(clientId: "orphan-1", name: "Stale Milk", serverId: 1, syncState: .synced)
        repo.items = [orphan]

        // Server returns a different item
        service.fetchResult = [self.makeResponse(id: 2, clientId: "fresh-1", name: "Fresh Bread")]

        let vm = ShoppingListViewModel(repository: repo, service: service)
        await vm.refresh()

        let names = repo.items.map(\.name)
        #expect(!names.contains("Stale Milk"))
        #expect(names.contains("Fresh Bread"))
    }

    @Test func refresh_networkError_resetsSyncing() async {
        let (vm, _, service) = self.makeVM()
        service.shouldThrow = true
        service.errorToThrow = APIError.networkError(URLError(.notConnectedToInternet))

        await vm.refresh()

        #expect(vm.isSyncing == false)
        #expect(vm.hasCompletedShoppingListRefresh)
    }

    @Test func refresh_setsForbiddenOnForbiddenError() async {
        let (vm, _, service) = self.makeVM()
        service.shouldThrow = true
        service.errorToThrow = APIError.forbidden

        await vm.refresh()

        #expect(vm.didReceiveForbidden == true)
    }

    @Test func refresh_doesNotRunConcurrently() async {
        let (vm, _, service) = self.makeVM()
        service.fetchResult = []

        await vm.refresh()
        #expect(service.fetchCallCount == 1)
    }

    // MARK: - Adding items must not delete existing ones

    @Test func addCustomItem_preservesExistingSyncedItems() async {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()

        // User already has synced items in their list
        let milk = self.makePersisted(clientId: "existing-1", name: "Milk", serverId: 1, syncState: .synced)
        let eggs = self.makePersisted(clientId: "existing-2", name: "Eggs", serverId: 2, syncState: .synced)
        repo.items = [milk, eggs]

        // Server returns only the newly created item (not the full list)
        service.createResult = [self.makeResponse(id: 10, clientId: "placeholder", name: "Bread")]

        let vm = ShoppingListViewModel(repository: repo, service: service)
        vm.addCustomItem("Bread")

        // Wait for background sync to complete
        try? await Task.sleep(for: .milliseconds(150))

        // Existing synced items must not have been pruned
        let names = Set(repo.items.map(\.name))
        #expect(names.contains("Milk"))
        #expect(names.contains("Eggs"))
        #expect(names.contains("Bread"))
    }

    @Test func addIngredientsFromRecipe_preservesExistingSyncedItems() async {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()

        // User already has synced items
        let milk = self.makePersisted(clientId: "existing-1", name: "Milk", serverId: 1, syncState: .synced)
        repo.items = [milk]

        // Server returns only the newly created items
        service.createResult = [
            self.makeResponse(id: 10, clientId: "placeholder-1", name: "Flour"),
            self.makeResponse(id: 11, clientId: "placeholder-2", name: "Sugar")
        ]

        let vm = ShoppingListViewModel(repository: repo, service: service)
        vm.addIngredientsFromRecipe(
            [
                ShoppingListDraftItem(name: "Flour"),
                ShoppingListDraftItem(name: "Sugar")
            ],
            sourceRecipeId: 42
        )

        try? await Task.sleep(for: .milliseconds(150))

        // Original item must still be there alongside the new ones
        let names = Set(repo.items.map(\.name))
        #expect(names.contains("Milk"))
        #expect(names.contains("Flour"))
        #expect(names.contains("Sugar"))
    }

    // MARK: - Sync pending updates

    @Test func syncPendingUpdates_updatesFromServer() async {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()

        let pending = self.makePersisted(clientId: "upd-1", name: "Eggs", serverId: 42, syncState: .pendingUpdate)
        pending.checkedAt = Date()
        repo.items = [pending]

        let updatedResponse = self.makeResponse(id: 42, clientId: "upd-1", name: "Eggs", checkedAt: Date())
        service.updateResult = updatedResponse
        service.fetchResult = [updatedResponse]

        let vm = ShoppingListViewModel(repository: repo, service: service)
        await vm.refresh()

        #expect(service.updateCallCount == 1)
        let item = repo.items.first { $0.clientId == "upd-1" }
        #expect(item?.syncState == .synced)
        #expect(item?.serverId == 42)
    }

    @Test func syncPendingUpdates_deletesLocalOnNotFound() async {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()

        let pending = self.makePersisted(
            clientId: "gone-1",
            name: "Stale item",
            serverId: 99,
            syncState: .pendingUpdate
        )
        pending.checkedAt = Date()
        repo.items = [pending]

        service.shouldThrow = true
        service.errorToThrow = APIError.notFound
        service.fetchResult = []

        let vm = ShoppingListViewModel(repository: repo, service: service)
        await vm.refresh()

        #expect(repo.items.first { $0.clientId == "gone-1" } == nil)
    }

    // MARK: - Add items

    @Test func addCustomItem_addsLocallyAndSyncs() async {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()

        service.createResult = [self.makeResponse(id: 10, clientId: "placeholder", name: "Sugar")]

        let vm = ShoppingListViewModel(repository: repo, service: service)
        vm.addCustomItem("  Sugar  ")

        // Item is added locally right away
        #expect(repo.items.count == 1)
        #expect(repo.items.first?.name == "Sugar")
        #expect(repo.items.first?.syncState == .pendingCreate)

        // Wait for background sync
        try? await Task.sleep(for: .milliseconds(100))
        #expect(service.createCallCount == 1)
    }

    @Test func addCustomItem_ignoresEmptyString() {
        let (vm, repo, _) = self.makeVM()

        vm.addCustomItem("   ")

        #expect(repo.items.isEmpty)
    }

    @Test func addIngredientsFromRecipe_addsMultipleItems() {
        let (vm, repo, service) = self.makeVM()
        service.createResult = []

        vm.addIngredientsFromRecipe(
            [
                ShoppingListDraftItem(name: "Flour"),
                ShoppingListDraftItem(name: "  Sugar  "),
                ShoppingListDraftItem(name: "")
            ],
            sourceRecipeId: 42
        )

        #expect(repo.items.count == 2)
        let names = repo.items.map(\.name)
        #expect(names.contains("Flour"))
        #expect(names.contains("Sugar"))
        #expect(repo.items.first?.sourceRecipeId == 42)
    }

    @Test func addIngredientsFromRecipe_ignoresAllEmpty() {
        let (vm, repo, _) = self.makeVM()

        vm.addIngredientsFromRecipe(
            [
                ShoppingListDraftItem(name: ""),
                ShoppingListDraftItem(name: "  ")
            ],
            sourceRecipeId: nil
        )

        #expect(repo.items.isEmpty)
    }

    @Test func addIngredientsFromRecipe_propagatesDetails() {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()
        service.createResult = []

        let vm = ShoppingListViewModel(repository: repo, service: service)
        vm.addIngredientsFromRecipe(
            [
                ShoppingListDraftItem(name: "Tomato", details: "200g, halved"),
                ShoppingListDraftItem(name: "Salt", details: "  "),
                ShoppingListDraftItem(name: "Eggs", details: nil)
            ],
            sourceRecipeId: 7
        )

        let tomato = repo.items.first { $0.name == "Tomato" }
        let salt = repo.items.first { $0.name == "Salt" }
        let eggs = repo.items.first { $0.name == "Eggs" }
        #expect(tomato?.details == "200g, halved")
        #expect(salt?.details == nil)
        #expect(eggs?.details == nil)
    }

    @Test func recipeAdditionNeedsReviewOnlyForItemsOlderThan36Hours() async {
        let now = Date(timeIntervalSince1970: 2_000_000_000)
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()
        let vm = ShoppingListViewModel(repository: repo, service: service)

        service.fetchResult = [
            self.makeResponse(clientId: "boundary", createdAt: now.addingTimeInterval(-36 * 60 * 60))
        ]
        await vm.refresh()
        #expect(!vm.needsReviewBeforeAddingRecipeIngredients(now: now))

        service.fetchResult = [
            self.makeResponse(clientId: "old", createdAt: now.addingTimeInterval(-(36 * 60 * 60) - 1))
        ]
        await vm.refresh()
        #expect(vm.needsReviewBeforeAddingRecipeIngredients(now: now))
    }

    @Test func replaceListWithIngredientsClearsOnlyAfterServerAcknowledgement() async {
        let repo = MockShoppingListRepository()
        repo.items = [self.makePersisted(clientId: "old", name: "Old milk")]
        let service = MockShoppingListService()
        service.createResult = [self.makeResponse(id: 9, clientId: "new", name: "Flour")]
        let vm = ShoppingListViewModel(repository: repo, service: service)

        let succeeded = await vm.replaceListWithIngredientsFromRecipe(
            [ShoppingListDraftItem(name: "Flour")],
            sourceRecipeId: 42
        )

        #expect(succeeded)
        #expect(service.lastCreateClearedExisting)
        #expect(repo.replaceAllCalled)
        #expect(repo.items.map(\.name) == ["Flour"])
    }

    @Test func replaceListWithIngredientsPreservesOldListWhenServerFails() async {
        let repo = MockShoppingListRepository()
        repo.items = [self.makePersisted(clientId: "old", name: "Old milk")]
        let service = MockShoppingListService()
        service.shouldThrow = true
        let vm = ShoppingListViewModel(repository: repo, service: service)

        let succeeded = await vm.replaceListWithIngredientsFromRecipe(
            [ShoppingListDraftItem(name: "Flour")],
            sourceRecipeId: 42
        )

        #expect(!succeeded)
        #expect(!repo.replaceAllCalled)
        #expect(repo.items.map(\.name) == ["Old milk"])
    }

    @Test func replacementCacheFailureDoesNotReportTheAcknowledgedServerActionAsFailed() async {
        let repo = MockShoppingListRepository()
        repo.items = [self.makePersisted(clientId: "old", name: "Old milk")]
        repo.shouldThrowOnSave = true
        let service = MockShoppingListService()
        service.createResult = [self.makeResponse(id: 9, clientId: "new", name: "Flour")]
        let vm = ShoppingListViewModel(repository: repo, service: service)

        let succeeded = await vm.replaceListWithIngredientsFromRecipe(
            [ShoppingListDraftItem(name: "Flour")],
            sourceRecipeId: 42
        )

        #expect(succeeded)
        #expect(repo.replaceAllCalled)
        #expect(repo.items.map(\.name) == ["Old milk"])
    }

    @Test func replacementWaitsForAnInFlightPendingCreateSync() async {
        let repo = MockShoppingListRepository()
        repo.items = [
            self.makePersisted(clientId: "pending", name: "Old milk", serverId: nil, syncState: .pendingCreate)
        ]
        let service = MockShoppingListService()
        let gate = ShoppingListCreateCallGate()
        let synced = self.makeResponse(id: 1, clientId: "pending", name: "Old milk")
        let replacement = self.makeResponse(id: 2, clientId: "new", name: "Flour")
        service.createHandler = { _, clearExisting in
            await gate.handle(clearExisting: clearExisting, response: clearExisting ? [replacement] : [synced])
        }
        let vm = ShoppingListViewModel(repository: repo, service: service)

        let refresh = Task { await vm.refresh() }
        await gate.waitForFirstCall()
        #expect(!vm.hasCompletedShoppingListRefresh)
        let replace = Task {
            await vm.replaceListWithIngredientsFromRecipe(
                [ShoppingListDraftItem(name: "Flour")],
                sourceRecipeId: 42
            )
        }
        await Task.yield()

        #expect(await gate.recordedClearFlags() == [false])
        await gate.releaseFirstCall()
        await refresh.value
        #expect(vm.hasCompletedShoppingListRefresh)
        #expect(await replace.value)
        #expect(await gate.recordedClearFlags() == [false, true])
    }

    // MARK: - Toggle item

    @Test func toggleItem_checksUncheckedItem() {
        let repo = MockShoppingListRepository()
        let item = self.makePersisted(clientId: "t-1", name: "Milk", checkedAt: nil)
        repo.items = [item]

        let vm = ShoppingListViewModel(repository: repo, service: MockShoppingListService())
        vm.toggleItem(item)

        #expect(repo.updatedItems.count == 1)
        #expect(repo.updatedItems.first?.clientId == "t-1")
        #expect(repo.updatedItems.first?.checkedAt != nil)
    }

    @Test func toggleItem_unchecksCheckedItem() {
        let repo = MockShoppingListRepository()
        let item = self.makePersisted(clientId: "t-2", name: "Milk", checkedAt: Date())
        repo.items = [item]

        let vm = ShoppingListViewModel(repository: repo, service: MockShoppingListService())
        vm.toggleItem(item)

        #expect(repo.updatedItems.count == 1)
        #expect(repo.updatedItems.first?.checkedAt == nil)
    }

    // MARK: - Delete item

    @Test func deleteItem_deletesLocallyAndFromServer() async {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()
        let item = self.makePersisted(clientId: "d-1", name: "Old item", serverId: 77)
        repo.items = [item]

        let vm = ShoppingListViewModel(repository: repo, service: service)
        vm.deleteItem(item)

        #expect(repo.items.first { $0.clientId == "d-1" } == nil)

        try? await Task.sleep(for: .milliseconds(100))
        #expect(service.deleteCallCount == 1)
        #expect(service.lastDeletedId == 77)
    }

    @Test func deleteItem_skipsServerDeleteWhenNoServerId() async {
        let repo = MockShoppingListRepository()
        let service = MockShoppingListService()
        let item = self.makePersisted(clientId: "d-2", name: "Local only", serverId: nil)
        repo.items = [item]

        let vm = ShoppingListViewModel(repository: repo, service: service)
        vm.deleteItem(item)

        #expect(repo.items.first { $0.clientId == "d-2" } == nil)

        try? await Task.sleep(for: .milliseconds(100))
        #expect(service.deleteCallCount == 0)
    }

    // MARK: - Reset and clear

    @Test func resetForCookbookSwitch_clearsState() {
        let (vm, _, _) = self.makeVM()

        vm.resetForCookbookSwitch()

        #expect(vm.items.isEmpty)
        #expect(vm.isSyncing == false)
    }

    @Test func clearData_clearsRepoAndItems() {
        let repo = MockShoppingListRepository()
        repo.items = [self.makePersisted()]

        let vm = ShoppingListViewModel(repository: repo, service: MockShoppingListService())
        vm.clearData()

        #expect(repo.clearAllCalled == true)
        #expect(vm.items.isEmpty)
    }

    // MARK: - Checked/unchecked filtering

    @Test func uncheckedAndCheckedItems_filterCorrectly() async {
        let repo = MockShoppingListRepository()
        let checkedDate = Date()
        let unchecked = self.makePersisted(clientId: "u-1", name: "Apples", checkedAt: nil)
        let checked = self.makePersisted(clientId: "c-1", name: "Bananas", checkedAt: checkedDate)
        repo.items = [unchecked, checked]

        let service = MockShoppingListService()
        service.fetchResult = [
            self.makeResponse(id: 1, clientId: "u-1", name: "Apples"),
            self.makeResponse(id: 2, clientId: "c-1", name: "Bananas", checkedAt: checkedDate)
        ]
        let vm = ShoppingListViewModel(repository: repo, service: service)
        await vm.refresh()

        #expect(vm.uncheckedItems.count == 1)
        #expect(vm.uncheckedItems.first?.name == "Apples")
        #expect(vm.checkedItems.count == 1)
        #expect(vm.checkedItems.first?.name == "Bananas")
    }
}

private actor ShoppingListCreateCallGate {
    private var clearFlags: [Bool] = []
    private var firstCallContinuation: CheckedContinuation<Void, Never>?
    private var firstCallWaiters: [CheckedContinuation<Void, Never>] = []

    func handle(
        clearExisting: Bool,
        response: [ShoppingListItemResponse]
    ) async -> [ShoppingListItemResponse] {
        self.clearFlags.append(clearExisting)
        self.firstCallWaiters.forEach { $0.resume() }
        self.firstCallWaiters = []

        if self.clearFlags.count == 1 {
            await withCheckedContinuation { continuation in
                self.firstCallContinuation = continuation
            }
        }
        return response
    }

    func waitForFirstCall() async {
        guard self.clearFlags.isEmpty else { return }
        await withCheckedContinuation { continuation in
            self.firstCallWaiters.append(continuation)
        }
    }

    func releaseFirstCall() {
        self.firstCallContinuation?.resume()
        self.firstCallContinuation = nil
    }

    func recordedClearFlags() -> [Bool] {
        self.clearFlags
    }
}

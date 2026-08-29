import Foundation
@testable import Hauptgang
import SwiftData
import Testing

@MainActor
struct MealPlanViewModelTests {
    private let cookbookId = 1

    private func makeMealPlanDay(
        date: String? = nil,
        entries: [MealPlanEntry] = [],
        selectedEntryId: Int? = nil
    ) -> MealPlanDay {
        MealPlanDay(
            date: date ?? MealPlanViewModel.dateString(for: Date()),
            selectedEntryId: selectedEntryId,
            selectedByUserId: nil,
            selectedAt: nil,
            entries: entries
        )
    }

    private func makeEntry(
        id: Int = 1,
        recipeId: Int = 100,
        recipeName: String = "Test Recipe",
        voteCount: Int = 0,
        votedByCurrentUser: Bool = false
    ) -> MealPlanEntry {
        MealPlanEntry(
            id: id,
            recipe: MealPlanRecipeSummary(id: recipeId, name: recipeName, coverImageUrl: nil),
            proposedBy: nil,
            voteCount: voteCount,
            votedByCurrentUser: votedByCurrentUser
        )
    }

    private func makePersistedEntry(
        cookbookId: Int = 1,
        date: String? = nil,
        serverId: Int? = 1,
        recipeId: Int = 100,
        recipeName: String = "Test Recipe",
        voteCount: Int = 0,
        votedByCurrentUser: Bool = false,
        syncState: MealPlanEntrySyncState = .synced
    ) -> PersistedMealPlanEntry {
        PersistedMealPlanEntry(
            cookbookId: cookbookId,
            date: date ?? MealPlanViewModel.dateString(for: Date()),
            serverId: serverId,
            recipeId: recipeId,
            recipeName: recipeName,
            voteCount: voteCount,
            votedByCurrentUser: votedByCurrentUser,
            syncState: syncState
        )
    }

    private func makeVM(
        repository: MockMealPlanRepository = MockMealPlanRepository(),
        service: MockMealPlanService = MockMealPlanService(),
        networkMonitor: MockNetworkStatusProvider = MockNetworkStatusProvider()
    ) -> (MealPlanViewModel, MockMealPlanRepository, MockMealPlanService) {
        let vm = MealPlanViewModel(
            repository: repository,
            service: service,
            networkMonitor: networkMonitor
        )
        return (vm, repository, service)
    }

    // MARK: - Refresh

    @Test func refresh_fetchesMealPlansAndSavesToRepo() async {
        let (vm, repo, service) = self.makeVM()
        let entry = self.makeEntry()
        service.fetchResult = [self.makeMealPlanDay(entries: [entry])]

        await vm.refresh(cookbookId: self.cookbookId)

        #expect(service.fetchCallCount == 1)
        #expect(repo.savedDays.count == 1)
        #expect(repo.savedDays.first?.count == 1)
        #expect(vm.isSyncing == false)
    }

    @Test func refresh_limitsVisibleDatesToFixedWindow() async throws {
        let (vm, _, service) = self.makeVM()
        service.fetchResult = []

        await vm.refresh(cookbookId: self.cookbookId)

        let calendar = Calendar.current
        let expectedStart = try MealPlanViewModel.dateString(for: #require(calendar.date(
            byAdding: .day,
            value: -1,
            to: Date()
        )))
        let expectedEnd = try MealPlanViewModel.dateString(for: #require(calendar.date(
            byAdding: .day,
            value: 8,
            to: Date()
        )))

        #expect(vm.visibleDates.count == 10)
        #expect(vm.visibleDates.first == expectedStart)
        #expect(vm.visibleDates.last == expectedEnd)
    }

    @Test func refresh_networkError_resetsSyncing() async {
        let (vm, _, service) = self.makeVM()
        service.shouldThrow = true
        service.errorToThrow = APIError.networkError(URLError(.notConnectedToInternet))

        await vm.refresh(cookbookId: self.cookbookId)

        #expect(vm.isSyncing == false)
    }

    @Test func refresh_offline_loadsCachedMealPlanWithoutFetching() async {
        let repo = MockMealPlanRepository()
        let service = MockMealPlanService()
        let networkMonitor = MockNetworkStatusProvider()
        networkMonitor.isOffline = true

        let today = MealPlanViewModel.dateString(for: Date())
        repo.entries[today] = [self.makePersistedEntry(
            cookbookId: self.cookbookId,
            date: today,
            recipeName: "Cached Meal"
        )]

        let vm = MealPlanViewModel(
            repository: repo,
            service: service,
            networkMonitor: networkMonitor
        )

        await vm.refresh(cookbookId: self.cookbookId)

        #expect(service.fetchCallCount == 0)
        #expect(vm.visibleDates.contains(today))
        #expect(vm.entriesByDate[today]?.first?.recipeName == "Cached Meal")
    }

    @Test func refresh_networkError_keepsCachedMealPlanVisible() async {
        let repo = MockMealPlanRepository()
        let service = MockMealPlanService()
        let today = MealPlanViewModel.dateString(for: Date())
        repo.entries[today] = [self.makePersistedEntry(
            cookbookId: self.cookbookId,
            date: today,
            recipeName: "Cached Meal"
        )]
        service.shouldThrow = true
        service.errorToThrow = APIError.networkError(URLError(.notConnectedToInternet))

        let vm = MealPlanViewModel(
            repository: repo,
            service: service,
            networkMonitor: MockNetworkStatusProvider()
        )

        await vm.refresh(cookbookId: self.cookbookId)

        #expect(service.fetchCallCount == 1)
        #expect(vm.visibleDates.contains(today))
        #expect(vm.entriesByDate[today]?.first?.recipeName == "Cached Meal")
    }

    @Test func refresh_setsForbiddenOnForbiddenError() async {
        let (vm, _, service) = self.makeVM()
        service.shouldThrow = true
        service.errorToThrow = APIError.forbidden

        await vm.refresh(cookbookId: self.cookbookId)

        #expect(vm.didReceiveForbidden == true)
    }

    @Test func refresh_doesNotRunConcurrently() async {
        let (vm, _, service) = self.makeVM()
        service.fetchResult = []

        await vm.refresh(cookbookId: self.cookbookId)
        #expect(service.fetchCallCount == 1)
    }

    @Test func refresh_syncsPendingEntriesFirst() async {
        let repo = MockMealPlanRepository()
        let service = MockMealPlanService()
        let pendingEntry = self.makePersistedEntry(syncState: .pendingCreate)
        repo.pendingEntries = [pendingEntry]
        service.addEntryResult = self.makeMealPlanDay(entries: [self.makeEntry()])
        service.fetchResult = []

        let vm = MealPlanViewModel(
            repository: repo,
            service: service,
            networkMonitor: MockNetworkStatusProvider()
        )
        await vm.refresh(cookbookId: self.cookbookId)

        #expect(service.addEntryCallCount == 1)
    }

    // MARK: - Add Entry

    @Test func addEntry_addsLocalEntryAndSyncs() async {
        let repo = MockMealPlanRepository()
        let service = MockMealPlanService()
        let entry = self.makeEntry()
        service.addEntryResult = self.makeMealPlanDay(entries: [entry])

        let vm = MealPlanViewModel(
            repository: repo,
            service: service,
            networkMonitor: MockNetworkStatusProvider()
        )
        let recipe = self.makeTestRecipe()

        vm.addEntry(cookbookId: self.cookbookId, date: MealPlanViewModel.dateString(for: Date()), recipe: recipe)

        #expect(repo.addedLocalEntries.count == 1)
        #expect(repo.addedLocalEntries.first?.recipeId == recipe.id)

        // Wait for background sync
        try? await Task.sleep(for: .milliseconds(100))
        #expect(service.addEntryCallCount == 1)
    }

    @Test func addEntry_offline_isReadOnly() async {
        let repo = MockMealPlanRepository()
        let service = MockMealPlanService()
        let networkMonitor = MockNetworkStatusProvider()
        networkMonitor.isOffline = true

        let vm = MealPlanViewModel(
            repository: repo,
            service: service,
            networkMonitor: networkMonitor
        )
        let recipe = self.makeTestRecipe()

        vm.addEntry(cookbookId: self.cookbookId, date: MealPlanViewModel.dateString(for: Date()), recipe: recipe)

        #expect(repo.addedLocalEntries.isEmpty)
        try? await Task.sleep(for: .milliseconds(50))
        #expect(service.addEntryCallCount == 0)
    }

    // MARK: - Delete Entry

    @Test func deleteEntry_deletesPendingEntryLocally() {
        let (vm, repo, service) = self.makeVM()
        let entry = self.makePersistedEntry(serverId: nil, syncState: .pendingCreate)

        vm.deleteEntry(entry, cookbookId: self.cookbookId)

        #expect(repo.deletedPendingEntries.count == 1)
        #expect(service.deleteEntryCallCount == 0)
    }

    @Test func deleteEntry_deletesSyncedEntryFromServer() async {
        let (vm, repo, service) = self.makeVM()
        let entry = self.makePersistedEntry(serverId: 42)

        vm.deleteEntry(entry, cookbookId: self.cookbookId)

        #expect(repo.deletedPendingEntries.isEmpty)

        try? await Task.sleep(for: .milliseconds(100))
        #expect(service.deleteEntryCallCount == 1)
        #expect(service.lastDeletedEntryId == 42)
        #expect(repo.deletedPendingEntries.count == 1)
    }

    @Test func addEntry_removesPendingEntryOnNotFound() async {
        let repo = MockMealPlanRepository()
        let service = MockMealPlanService()
        service.shouldThrow = true
        service.errorToThrow = APIError.notFound

        let vm = MealPlanViewModel(
            repository: repo,
            service: service,
            networkMonitor: MockNetworkStatusProvider()
        )
        let recipe = self.makeTestRecipe()
        let date = MealPlanViewModel.dateString(for: Date())

        vm.addEntry(cookbookId: self.cookbookId, date: date, recipe: recipe)

        try? await Task.sleep(for: .milliseconds(100))
        #expect(repo.deletedPendingEntries.count == 1)
        #expect(repo.deletedPendingEntries.first?.recipeId == recipe.id)
    }

    // MARK: - Toggle Vote

    @Test func toggleVote_optimisticallyUpdatesUI() {
        let (vm, _, _) = self.makeVM()
        let entry = self.makePersistedEntry(serverId: 10, voteCount: 0, votedByCurrentUser: false)

        vm.toggleVote(entry: entry, cookbookId: self.cookbookId)

        #expect(entry.votedByCurrentUser == true)
        #expect(entry.voteCount == 1)
    }

    @Test func toggleVote_revertsOnError() async {
        let service = MockMealPlanService()
        service.shouldThrow = true
        let (vm, _, _) = self.makeVM(service: service)
        let entry = self.makePersistedEntry(serverId: 10, voteCount: 1, votedByCurrentUser: true)

        vm.toggleVote(entry: entry, cookbookId: self.cookbookId)

        // Optimistic update
        #expect(entry.votedByCurrentUser == false)
        #expect(entry.voteCount == 0)

        // Wait for error handler to revert
        try? await Task.sleep(for: .milliseconds(100))
        #expect(entry.votedByCurrentUser == true)
        #expect(entry.voteCount == 1)
    }

    @Test func toggleVote_ignoresEntryWithoutServerId() async {
        let (vm, _, service) = self.makeVM()
        let entry = self.makePersistedEntry(serverId: nil)

        vm.toggleVote(entry: entry, cookbookId: self.cookbookId)

        try? await Task.sleep(for: .milliseconds(50))
        #expect(service.voteCallCount == 0)
        #expect(service.unvoteCallCount == 0)
    }

    @Test func toggleVote_offline_isReadOnly() async {
        let service = MockMealPlanService()
        let networkMonitor = MockNetworkStatusProvider()
        networkMonitor.isOffline = true
        let vm = MealPlanViewModel(
            repository: MockMealPlanRepository(),
            service: service,
            networkMonitor: networkMonitor
        )
        let entry = self.makePersistedEntry(serverId: 10, voteCount: 1, votedByCurrentUser: true)

        vm.toggleVote(entry: entry, cookbookId: self.cookbookId)

        try? await Task.sleep(for: .milliseconds(50))
        #expect(entry.votedByCurrentUser == true)
        #expect(entry.voteCount == 1)
        #expect(service.voteCallCount == 0)
        #expect(service.unvoteCallCount == 0)
    }

    // MARK: - Reset

    @Test func resetForCookbookSwitch_clearsState() {
        let (vm, _, _) = self.makeVM()

        vm.resetForCookbookSwitch()

        #expect(vm.visibleDates.isEmpty)
        #expect(vm.entriesByDate.isEmpty)
        #expect(vm.isSyncing == false)
    }

    @Test func clearData_clearsRepoAndState() {
        let repo = MockMealPlanRepository()
        let vm = MealPlanViewModel(
            repository: repo,
            service: MockMealPlanService(),
            networkMonitor: MockNetworkStatusProvider()
        )

        vm.clearData()

        #expect(vm.visibleDates.isEmpty)
        #expect(vm.entriesByDate.isEmpty)
    }

    // MARK: - Date Helpers

    @Test func dateString_formatsCorrectly() {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")

        let today = Date()
        let expected = formatter.string(from: today)

        #expect(MealPlanViewModel.dateString(for: today) == expected)
    }

    // MARK: - Helpers

    private func makeTestRecipe() -> PersistedRecipe {
        PersistedRecipe(
            id: 100,
            cookbookId: self.cookbookId,
            name: "Test Recipe",
            favorite: false,
            updatedAt: Date(),
            lastFetchedAt: Date()
        )
    }
}

@MainActor
private final class MockNetworkStatusProvider: NetworkStatusProviding {
    var isOffline = false
}

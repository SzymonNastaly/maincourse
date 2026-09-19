@testable import Hauptgang
import SwiftData
import Testing

@MainActor
struct AuthenticatedSessionViewModelTests {
    private func makeUser(id: Int = 100) -> User {
        User(id: id, email: "test@example.com")
    }

    private func makePersonalCookbook(id: Int = 1) -> Cookbook {
        Cookbook(id: id, name: "My Recipes", personal: true, recipeCount: 0, members: [
            CookbookMember(id: 100, email: "test@example.com", role: "owner")
        ])
    }

    private func makeSharedCookbook(id: Int = 2) -> Cookbook {
        Cookbook(id: id, name: "Family Recipes", personal: false, recipeCount: 0, members: [
            CookbookMember(id: 100, email: "test@example.com", role: "owner")
        ])
    }

    private func makeModelContext() -> ModelContext {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        // swiftlint:disable:next force_try
        let container = try! ModelContainer(for: PersistedRecipe.self, configurations: config)
        return ModelContext(container)
    }

    private func makeSession(
        cookbookService: MockCookbookService,
        recipeService: MockRecipeService = MockRecipeService(),
        recipeRepository: MockRecipeRepository = MockRecipeRepository(),
        searchIndex: MockRecipeSearchIndex = MockRecipeSearchIndex()
    ) -> AuthenticatedSessionViewModel {
        AuthenticatedSessionViewModel(
            cookbookViewModel: CookbookViewModel(service: cookbookService),
            recipeViewModel: RecipeViewModel(
                recipeService: recipeService,
                repository: recipeRepository,
                searchIndex: searchIndex
            ),
            shoppingListViewModel: ShoppingListViewModel()
        )
    }

    @Test func failedCookbookStartupCanRecoverWithoutAnUnconfiguredRepository() async {
        let cookbooks = MockCookbookService()
        cookbooks.shouldThrowError = true
        let repository = MockRecipeRepository()
        let session = self.makeSession(cookbookService: cookbooks, recipeRepository: repository)
        await session.start(user: self.makeUser(id: 8941), modelContext: self.makeModelContext())
        #expect(repository.configuredCalled)
        cookbooks.shouldThrowError = false
        cookbooks.cookbooksToReturn = [self.makePersonalCookbook()]
        await session.refreshActiveCookbook()
        #expect(session.recipeViewModel.hasLoadedEmptyCookbook)
    }

    // MARK: - Startup

    @Test func start_freshSignup_resolvesActiveCookbookAndReachesReady() async {
        await CookbookContext.shared.reset()

        let cookbookService = MockCookbookService()
        cookbookService.cookbooksToReturn = [self.makePersonalCookbook(), self.makeSharedCookbook()]

        let recipeService = MockRecipeService()
        recipeService.fetchRecipesResult = .success([])

        let session = self.makeSession(
            cookbookService: cookbookService,
            recipeService: recipeService
        )

        await session.start(user: self.makeUser(), modelContext: self.makeModelContext())

        #expect(session.canDismissStartupSplash == true)
        if case let .ready(userId, cookbookId) = session.startupState {
            #expect(userId == 100)
            // Default selection prefers shared cookbook (id 2)
            #expect(cookbookId == 2)
        } else {
            Issue.record("Expected .ready, got \(session.startupState)")
        }
        #expect(session.cookbookViewModel.activeCookbook?.id == 2)
        #expect(session.recipeViewModel.hasResolvedContent(for: 2) == true)
    }

    @Test func start_cookbookLoadFailureWithNoCache_failsButCanDismissSplash() async {
        await CookbookContext.shared.reset()

        let cookbookService = MockCookbookService()
        cookbookService.shouldThrowError = true

        let session = self.makeSession(cookbookService: cookbookService)

        await session.start(user: self.makeUser(), modelContext: self.makeModelContext())

        #expect(session.canDismissStartupSplash == true)
        if case let .failed(userId, _) = session.startupState {
            #expect(userId == 100)
        } else {
            Issue.record("Expected .failed, got \(session.startupState)")
        }
    }

    @Test func start_recipeLoadFailure_splashDismissesWithDegradedRecipeState() async {
        await CookbookContext.shared.reset()

        let cookbookService = MockCookbookService()
        cookbookService.cookbooksToReturn = [self.makePersonalCookbook()]

        let recipeService = MockRecipeService()
        recipeService.fetchRecipesResult = .failure(MockRecipeError.networkError)

        let session = self.makeSession(
            cookbookService: cookbookService,
            recipeService: recipeService
        )

        await session.start(user: self.makeUser(), modelContext: self.makeModelContext())

        // Splash always dismisses; recipe content state shows the failure.
        #expect(session.canDismissStartupSplash == true)
        if case .ready = session.startupState {
            // Session reaches .ready even when initial recipe content failed; this is the
            // intentional non-blocking policy ("degraded recipe state" rather than blocking
            // the whole authenticated UI).
        } else {
            Issue.record("Expected .ready with degraded recipe state, got \(session.startupState)")
        }
        if case let .failed(cookbookId, _) = session.recipeViewModel.contentState {
            #expect(cookbookId == 1)
        } else {
            Issue.record("Expected recipe content state .failed, got \(session.recipeViewModel.contentState)")
        }
    }

    // MARK: - Cookbook switching

    @Test func switchCookbook_resetsRecipeStateAndReloadsForNewCookbook() async {
        await CookbookContext.shared.reset()

        let personal = self.makePersonalCookbook()
        let shared = self.makeSharedCookbook()
        let cookbookService = MockCookbookService()
        cookbookService.cookbooksToReturn = [personal, shared]

        let recipeService = MockRecipeService()
        recipeService.fetchRecipesResult = .success([])

        let searchIndex = MockRecipeSearchIndex()
        let session = self.makeSession(
            cookbookService: cookbookService,
            recipeService: recipeService,
            searchIndex: searchIndex
        )

        await session.start(user: self.makeUser(), modelContext: self.makeModelContext())
        // Default lands on shared cookbook id 2; switch to personal.
        await session.switchCookbook(personal)

        #expect(session.cookbookViewModel.activeCookbook?.id == 1)
        #expect(session.recipeViewModel.currentCookbookId == 1)
        #expect(session.recipeViewModel.hasResolvedContent(for: 1) == true)
        let configured = await searchIndex.configuredCookbookId
        #expect(configured == 1)
    }

    // MARK: - Logout

    @Test func reset_clearsSessionState() async {
        await CookbookContext.shared.reset()

        let cookbookService = MockCookbookService()
        cookbookService.cookbooksToReturn = [self.makePersonalCookbook()]

        let recipeService = MockRecipeService()
        recipeService.fetchRecipesResult = .success([])

        let session = self.makeSession(
            cookbookService: cookbookService,
            recipeService: recipeService
        )

        await session.start(user: self.makeUser(), modelContext: self.makeModelContext())
        #expect(session.startupState != .idle)

        await session.reset()

        #expect(session.startupState == .idle)
        #expect(session.currentUser == nil)
        #expect(session.cookbookViewModel.activeCookbook == nil)
        #expect(session.recipeViewModel.recipes.isEmpty)
    }

    // MARK: - Cancellation

    @Test func reset_afterStart_doesNotLeaveStaleReadyState() async {
        await CookbookContext.shared.reset()

        let cookbookService = MockCookbookService()
        cookbookService.cookbooksToReturn = [self.makePersonalCookbook()]

        let recipeService = MockRecipeService()
        recipeService.fetchRecipesResult = .success([])

        let session = self.makeSession(
            cookbookService: cookbookService,
            recipeService: recipeService
        )

        await session.start(user: self.makeUser(), modelContext: self.makeModelContext())
        await session.reset()

        #expect(session.startupState == .idle)
    }
}

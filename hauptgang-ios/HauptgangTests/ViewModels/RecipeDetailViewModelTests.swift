@testable import Hauptgang
import XCTest

@MainActor
final class RecipeDetailViewModelTests: XCTestCase {
    private var sut: RecipeDetailViewModel!
    private var mockRecipeService: MockRecipeService!
    private var mockRepository: MockRecipeRepository!

    override func setUp() {
        super.setUp()
        self.mockRecipeService = MockRecipeService()
        self.mockRepository = MockRecipeRepository()
        self.sut = RecipeDetailViewModel(
            recipeService: self.mockRecipeService,
            repository: self.mockRepository
        )
    }

    override func tearDown() {
        self.sut = nil
        self.mockRecipeService = nil
        self.mockRepository = nil
        super.tearDown()
    }

    // MARK: - Loading from API Tests

    func testLoadRecipe_success_updatesRecipe() async {
        let expectedRecipe = RecipeDetail.mock(id: 42, name: "Spaghetti Carbonara")
        self.mockRecipeService.fetchRecipeDetailResult = .success(expectedRecipe)

        await self.sut.loadRecipe(id: 42)

        XCTAssertEqual(self.sut.recipe?.id, 42)
        XCTAssertEqual(self.sut.recipe?.name, "Spaghetti Carbonara")
        XCTAssertFalse(self.sut.isLoading)
        XCTAssertNil(self.sut.errorMessage)
    }

    func testLoadRecipe_success_savesToRepository() async {
        let expectedRecipe = RecipeDetail.mock(id: 42, name: "Test Recipe")
        self.mockRecipeService.fetchRecipeDetailResult = .success(expectedRecipe)

        await self.sut.loadRecipe(id: 42)

        XCTAssertEqual(self.mockRepository.savedRecipeDetail?.id, 42)
        XCTAssertEqual(self.mockRepository.savedRecipeDetail?.name, "Test Recipe")
    }

    func testLoadRecipe_callsServiceWithCorrectId() async {
        await self.sut.loadRecipe(id: 123)

        XCTAssertTrue(self.mockRecipeService.fetchRecipeDetailCalled)
        XCTAssertEqual(self.mockRecipeService.fetchRecipeDetailCalledWithId, 123)
    }

    // MARK: - Loading State Tests

    func testLoadRecipe_noCache_setsIsLoading() async {
        XCTAssertFalse(self.sut.isLoading)

        await self.sut.loadRecipe(id: 1)

        XCTAssertFalse(self.sut.isLoading)
    }

    // MARK: - Cache Tests

    func testLoadRecipe_withCache_showsCachedDataImmediately() async {
        let cachedRecipe = self.createMockPersistedRecipe(id: 42, name: "Cached Recipe")
        self.mockRepository.cachedRecipe = cachedRecipe

        await self.sut.loadRecipe(id: 42)

        XCTAssertNotNil(self.sut.recipe)
    }

    func testLoadRecipe_withCache_refreshesFromAPI() async {
        let cachedRecipe = self.createMockPersistedRecipe(id: 42, name: "Cached Recipe")
        self.mockRepository.cachedRecipe = cachedRecipe

        let freshRecipe = RecipeDetail.mock(id: 42, name: "Updated Recipe")
        self.mockRecipeService.fetchRecipeDetailResult = .success(freshRecipe)

        await self.sut.loadRecipe(id: 42)

        XCTAssertEqual(self.sut.recipe?.name, "Updated Recipe")
    }

    // MARK: - Offline Fallback Tests

    func testLoadRecipe_apiFailsWithCache_keepsCachedRecipe() async {
        let cachedRecipe = self.createMockPersistedRecipe(id: 42, name: "Cached Recipe")
        self.mockRepository.cachedRecipe = cachedRecipe
        self.mockRecipeService.fetchRecipeDetailResult = .failure(MockRecipeError.networkError)

        await self.sut.loadRecipe(id: 42)

        XCTAssertNotNil(self.sut.recipe)
        XCTAssertNil(self.sut.errorMessage)
    }

    func testLoadRecipe_apiFailsNoCache_showsError() async {
        self.mockRecipeService.fetchRecipeDetailResult = .failure(MockRecipeError.networkError)

        await self.sut.loadRecipe(id: 42)

        XCTAssertNil(self.sut.recipe)
        XCTAssertNotNil(self.sut.errorMessage)
        XCTAssertEqual(self.sut.errorMessage, "Failed to load recipe. Tap to retry.")
    }

    // MARK: - Error Handling Tests

    func testLoadRecipe_clearsErrorOnNewLoad() async {
        self.mockRecipeService.fetchRecipeDetailResult = .failure(MockRecipeError.networkError)
        await self.sut.loadRecipe(id: 42)
        XCTAssertNotNil(self.sut.errorMessage)

        self.mockRecipeService.fetchRecipeDetailResult = .success(RecipeDetail.mock(id: 42))
        await self.sut.loadRecipe(id: 42)

        XCTAssertNil(self.sut.errorMessage)
    }

    // MARK: - Persistence Error Tests

    func testLoadRecipe_persistenceFailure_stillShowsAPIData() async {
        let expectedRecipe = RecipeDetail.mock(id: 42, name: "API Recipe")
        self.mockRecipeService.fetchRecipeDetailResult = .success(expectedRecipe)
        self.mockRepository.shouldThrowOnSave = true

        await self.sut.loadRecipe(id: 42)

        XCTAssertEqual(self.sut.recipe?.name, "API Recipe")
        XCTAssertNil(self.sut.errorMessage)
    }

    // MARK: - Loading Flag Cleanup Tests

    func testLoadRecipe_alwaysResetsLoadingFlag() async {
        self.mockRecipeService.fetchRecipeDetailResult = .failure(MockRecipeError.networkError)

        await self.sut.loadRecipe(id: 42)

        XCTAssertFalse(self.sut.isLoading)
    }

    func testLoadRecipe_withCache_keepsLoadingFlagReset() async {
        let cachedRecipe = self.createMockPersistedRecipe(id: 42, name: "Cached")
        self.mockRepository.cachedRecipe = cachedRecipe
        self.mockRecipeService.fetchRecipeDetailResult = .failure(MockRecipeError.networkError)

        await self.sut.loadRecipe(id: 42)

        XCTAssertFalse(self.sut.isLoading)
    }

    // MARK: - View Tracking Tests

    func testOpeningARecipeRecordsAView() async throws {
        let suiteName = "RecipeDetailViewModelTests.\(UUID().uuidString)"
        nonisolated(unsafe) let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let sink = MockRecipeViewSink()
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        let service = MockRecipeService()
        service.fetchRecipeDetailResult = .success(RecipeDetail.mock(id: 7))
        let viewModel = RecipeDetailViewModel(
            recipeService: service,
            repository: MockRecipeRepository(),
            viewTracker: tracker
        )

        await viewModel.loadRecipe(id: 7)
        await tracker.flush()

        XCTAssertEqual(sink.batches.first?.map(\.recipeId), [7])
    }

    func testAFailedLoadWithNoCachedRecipeDoesNotRecordAView() async throws {
        let suiteName = "RecipeDetailViewModelTests.\(UUID().uuidString)"
        nonisolated(unsafe) let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let sink = MockRecipeViewSink()
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        let service = MockRecipeService()
        service.fetchRecipeDetailResult = .failure(MockRecipeError.networkError)
        let repository = MockRecipeRepository()
        repository.cachedRecipe = nil
        let viewModel = RecipeDetailViewModel(
            recipeService: service,
            repository: repository,
            viewTracker: tracker
        )

        await viewModel.loadRecipe(id: 7)
        await tracker.flush()

        XCTAssertTrue(sink.batches.isEmpty, "a recipe that failed to load with nothing cached was not read")
    }

    func testAFailedLoadThatFallsBackToACachedRecipeRecordsAView() async throws {
        let suiteName = "RecipeDetailViewModelTests.\(UUID().uuidString)"
        nonisolated(unsafe) let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let sink = MockRecipeViewSink()
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        let cachedRecipe = self.createMockPersistedRecipe(id: 7, name: "Cached Recipe")
        let repository = MockRecipeRepository()
        repository.cachedRecipe = cachedRecipe

        let service = MockRecipeService()
        service.fetchRecipeDetailResult = .failure(MockRecipeError.networkError)
        let viewModel = RecipeDetailViewModel(
            recipeService: service,
            repository: repository,
            viewTracker: tracker
        )

        await viewModel.loadRecipe(id: 7)
        await tracker.flush()

        XCTAssertEqual(sink.batches.count, 1, "the user read the cached recipe, so a view should be recorded")
        XCTAssertEqual(sink.batches.first?.first?.recipeId, 7)
    }

    // MARK: - Helpers

    private func createMockPersistedRecipe(id: Int, name: String) -> PersistedRecipe {
        let recipe = PersistedRecipe(from: RecipeListItem.mock(id: id, name: name))
        recipe.detailLastFetchedAt = Date()
        recipe.ingredientsJson = "[\"Ingredient 1\"]"
        recipe.instructionsJson = "[\"Step 1\"]"
        recipe.tagsJson = "[]"
        recipe.createdAt = Date()
        return recipe
    }
}

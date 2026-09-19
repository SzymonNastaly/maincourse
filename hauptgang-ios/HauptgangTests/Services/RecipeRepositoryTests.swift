@testable import Hauptgang
import SwiftData
import XCTest

@MainActor
final class RecipeRepositoryTests: XCTestCase {
    private var sut: RecipeRepository!
    private var modelContext: ModelContext!

    override func setUp() {
        super.setUp()
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try! ModelContainer(for: PersistedRecipe.self, configurations: config)
        self.modelContext = ModelContext(container)
        self.sut = RecipeRepository()
        self.sut.configure(modelContext: self.modelContext)
    }

    override func tearDown() {
        self.sut = nil
        self.modelContext = nil
        super.tearDown()
    }

    // MARK: - Empty Response Guard

    func testSaveRecipes_emptyResponse_preservesExistingRecipes() throws {
        // Seed two existing recipes
        let existing = [
            RecipeListItem.mock(id: 1, name: "Recipe 1"),
            RecipeListItem.mock(id: 2, name: "Recipe 2")
        ]
        _ = try self.sut.saveRecipes(existing, cookbookId: 0)

        // Sync with an empty response (simulates offline / failed fetch)
        _ = try self.sut.saveRecipes([], cookbookId: 0)

        let cached = try self.sut.getAllRecipes(cookbookId: 0)
        XCTAssertEqual(cached.count, 2, "Empty API response should not delete cached recipes")
    }

    func testSaveRecipes_emptyResponse_returnsNoDeletions() throws {
        let existing = [RecipeListItem.mock(id: 1, name: "Recipe 1")]
        _ = try self.sut.saveRecipes(existing, cookbookId: 0)

        let deletedIds = try self.sut.saveRecipes([], cookbookId: 0)

        XCTAssertTrue(deletedIds.isEmpty, "Empty API response should report no deletions")
    }

    // MARK: - Stale Recipe Pruning

    func testSaveRecipes_removesStaleRecipes() throws {
        let initial = [
            RecipeListItem.mock(id: 1, name: "Keep"),
            RecipeListItem.mock(id: 2, name: "Stale")
        ]
        _ = try self.sut.saveRecipes(initial, cookbookId: 0)

        // Second sync only includes recipe 1
        let deletedIds = try self.sut.saveRecipes(
            [RecipeListItem.mock(id: 1, name: "Keep")],
            cookbookId: 0
        )

        let cached = try self.sut.getAllRecipes(cookbookId: 0)
        XCTAssertEqual(cached.count, 1)
        XCTAssertEqual(cached.first?.id, 1)
        XCTAssertEqual(deletedIds, [2])
    }

    func testSaveRecipes_staleRemoval_isScopedToCookbook() throws {
        // Save recipe in cookbook 1
        _ = try self.sut.saveRecipes(
            [RecipeListItem.mock(id: 1, name: "Cookbook 1 Recipe")],
            cookbookId: 1
        )

        // Save recipe in cookbook 2
        _ = try self.sut.saveRecipes(
            [RecipeListItem.mock(id: 2, name: "Cookbook 2 Recipe")],
            cookbookId: 2
        )

        // Sync cookbook 1 with a different recipe — should not touch cookbook 2
        _ = try self.sut.saveRecipes(
            [RecipeListItem.mock(id: 3, name: "New Cookbook 1 Recipe")],
            cookbookId: 1
        )

        let cookbook2 = try self.sut.getAllRecipes(cookbookId: 2)
        XCTAssertEqual(cookbook2.count, 1, "Pruning cookbook 1 should not affect cookbook 2")
        XCTAssertEqual(cookbook2.first?.id, 2)
    }

    // MARK: - Starter Recipe Marker

    func testSaveRecipes_preservesStarterMarkerFromListResponse() throws {
        let recipe = RecipeListItem(
            id: 91,
            name: "Starter orzo",
            favorite: false,
            starterRecipeKey: "tomato-orzo-v1",
            updatedAt: Date()
        )

        _ = try self.sut.saveRecipes([recipe], cookbookId: 4)

        let cached = try XCTUnwrap(self.sut.getRecipe(id: 91))
        XCTAssertEqual(cached.starterRecipeKey, "tomato-orzo-v1")
    }

    func testSaveRecipeDetailRoundTripsStarterMarkerThroughCacheProjection() throws {
        let detail = RecipeDetail(
            id: 92,
            name: "Starter orzo",
            favorite: false,
            servings: 2,
            ingredients: ["160 g orzo"],
            instructions: ["Simmer"],
            starterRecipeKey: "tomato-orzo-v1",
            createdAt: Date(timeIntervalSince1970: 1_700_000_000),
            updatedAt: Date(timeIntervalSince1970: 1_700_000_100)
        )

        try self.sut.saveRecipeDetail(detail, cookbookId: 4)

        let cached = try XCTUnwrap(self.sut.getRecipe(id: 92))
        XCTAssertEqual(cached.starterRecipeKey, "tomato-orzo-v1")
        XCTAssertEqual(cached.toRecipeDetail().starterRecipeKey, "tomato-orzo-v1")
    }
}

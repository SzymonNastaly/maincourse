@testable import Hauptgang
import SwiftData
import XCTest

@MainActor
final class ShoppingListRepositoryTests: XCTestCase {
    private var sut: ShoppingListRepository!
    private var modelContext: ModelContext!

    override func setUp() {
        super.setUp()
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        let container = try! ModelContainer(
            for: PersistedShoppingListItem.self, PersistedRecipe.self,
            configurations: config
        )
        self.modelContext = ModelContext(container)
        self.sut = ShoppingListRepository()
        self.sut.configure(modelContext: self.modelContext)
    }

    override func tearDown() {
        self.sut = nil
        self.modelContext = nil
        super.tearDown()
    }

    // MARK: - Category merge

    func testPendingCreateKeepsLocalCategoryWhenServerHasNone() throws {
        try self.sut.addLocalItems([self.create(clientId: "a", name: "Milch", category: "dairy_eggs")])

        try self.sut.saveItems([self.response(clientId: "a", name: "Milch", category: nil)], pruneOrphans: false)

        let item = try XCTUnwrap(self.sut.getAllItems().first)
        XCTAssertEqual(item.category, "dairy_eggs")
        XCTAssertEqual(item.canonicalName, "milk")
        XCTAssertEqual(item.syncState, .synced)
    }

    func testPendingCreateAdoptsServerCategory() throws {
        try self.sut.addLocalItems([self.create(clientId: "a", name: "Dragon fruit", category: nil)])

        try self.sut.saveItems(
            [self.response(clientId: "a", name: "Dragon fruit", category: "produce", canonicalName: "dragon fruit")],
            pruneOrphans: false
        )

        let item = try XCTUnwrap(self.sut.getAllItems().first)
        XCTAssertEqual(item.category, "produce")
        XCTAssertEqual(item.canonicalName, "dragon fruit")
    }

    func testSyncedItemAdoptsLaterServerCategory() throws {
        try self.sut.saveItems([self.response(clientId: "a", name: "Soap", category: nil)], pruneOrphans: true)
        XCTAssertNil(try self.sut.getAllItems().first?.category)

        try self.sut.saveItems([self.response(clientId: "a", name: "Soap", category: "household")], pruneOrphans: true)

        XCTAssertEqual(try self.sut.getAllItems().first?.category, "household")
    }

    func testPendingUpdateAdoptsServerCategory() throws {
        try self.sut.saveItems([self.response(clientId: "a", name: "Soap", category: nil)], pruneOrphans: true)
        try self.sut.updateItem(clientId: "a", checkedAt: Date())

        try self.sut.updateItemFromServer(
            clientId: "a",
            response: self.response(clientId: "a", name: "Soap", category: "household")
        )

        let item = try XCTUnwrap(self.sut.getAllItems().first)
        XCTAssertEqual(item.category, "household")
        XCTAssertEqual(item.syncState, .synced)
    }

    // MARK: - Category hint

    func testCategoryHintReadsCachedRecipeIngredients() throws {
        let recipe = PersistedRecipe(id: 1, name: "Pancakes", updatedAt: Date())
        recipe.structuredIngredients = [
            StructuredIngredient(
                id: 1, position: 0, name: "Milch", canonicalName: "milk", category: "dairy_eggs", raw: "250 ml Milch"
            )
        ]
        self.modelContext.insert(recipe)
        self.modelContext.insert(PersistedRecipe(id: 2, name: "Not fetched yet", updatedAt: Date()))
        try self.modelContext.save()

        XCTAssertEqual(
            try self.sut.categoryHint(forName: "milk"),
            ShoppingCategoryHint(category: "dairy_eggs", canonicalName: "milk")
        )
        XCTAssertNil(try self.sut.categoryHint(forName: "Dish soap"))
    }

    // MARK: - Helpers

    private func create(clientId: String, name: String, category: String?) -> ShoppingListItemCreate {
        ShoppingListItemCreate(
            clientId: clientId,
            name: name,
            details: nil,
            checkedAt: nil,
            sourceRecipeId: nil,
            category: category,
            canonicalName: category == nil ? nil : "milk"
        )
    }

    private func response(
        clientId: String,
        name: String,
        category: String?,
        canonicalName: String? = nil
    ) -> ShoppingListItemResponse {
        ShoppingListItemResponse(
            id: 1,
            clientId: clientId,
            name: name,
            details: nil,
            checkedAt: nil,
            sourceRecipeId: nil,
            createdAt: Date(),
            updatedAt: Date(),
            category: category,
            canonicalName: canonicalName ?? category.map { _ in name.lowercased() },
            categoryPending: category == nil
        )
    }
}

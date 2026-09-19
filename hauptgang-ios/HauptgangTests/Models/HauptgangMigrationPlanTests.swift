@testable import Hauptgang
import SwiftData
import XCTest

@MainActor
final class HauptgangMigrationPlanTests: XCTestCase {
    func testV6ToV7RetainsRecipeDetailsAndPendingShoppingEdit() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("hauptgang-v6-v7-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let storeURL = directory.appendingPathComponent("migration.store")

        try self.seedV6Store(at: storeURL)

        let schema = Schema(versionedSchema: HauptgangSchemaV7.self)
        let configuration = ModelConfiguration(
            "MigrationTest",
            schema: schema,
            url: storeURL,
            cloudKitDatabase: .none
        )
        let container = try ModelContainer(
            for: schema,
            migrationPlan: HauptgangMigrationPlan.self,
            configurations: [configuration]
        )
        let context = ModelContext(container)

        let recipe = try XCTUnwrap(context.fetch(FetchDescriptor<PersistedRecipe>()).first)
        XCTAssertEqual(recipe.name, "Cached orzo")
        XCTAssertEqual(recipe.servings, 2)
        XCTAssertEqual(recipe.notes, "Keep these cached details")
        XCTAssertEqual(recipe.ingredientsJson, "[\"160 g orzo\"]")
        XCTAssertEqual(recipe.instructionsJson, "[\"Simmer\"]")
        XCTAssertEqual(recipe.structuredIngredientsJson, "[{\"name\":\"orzo\"}]")
        XCTAssertNotNil(recipe.detailLastFetchedAt)
        XCTAssertNil(recipe.starterRecipeKey)

        let shoppingItem = try XCTUnwrap(
            context.fetch(FetchDescriptor<PersistedShoppingListItem>()).first
        )
        XCTAssertEqual(shoppingItem.name, "Olive oil")
        XCTAssertEqual(shoppingItem.details, "2 tbsp")
        XCTAssertEqual(shoppingItem.syncState, .pendingUpdate)
    }

    private func seedV6Store(at storeURL: URL) throws {
        let schema = Schema(versionedSchema: HauptgangSchemaV6.self)
        let configuration = ModelConfiguration(
            "MigrationTest",
            schema: schema,
            url: storeURL,
            cloudKitDatabase: .none
        )
        let container = try ModelContainer(for: schema, configurations: [configuration])
        let context = ModelContext(container)

        let recipe = HauptgangSchemaV6.PersistedRecipe()
        recipe.id = 7
        recipe.cookbookId = 3
        recipe.name = "Cached orzo"
        recipe.servings = 2
        recipe.notes = "Keep these cached details"
        recipe.ingredientsJson = "[\"160 g orzo\"]"
        recipe.instructionsJson = "[\"Simmer\"]"
        recipe.structuredIngredientsJson = "[{\"name\":\"orzo\"}]"
        recipe.detailLastFetchedAt = Date(timeIntervalSince1970: 1_700_000_000)
        context.insert(recipe)

        let shoppingItem = HauptgangSchemaV6.PersistedShoppingListItem()
        shoppingItem.scopedClientId = "3|olive-oil"
        shoppingItem.clientId = "olive-oil"
        shoppingItem.cookbookId = 3
        shoppingItem.name = "Olive oil"
        shoppingItem.details = "2 tbsp"
        shoppingItem.createdAt = Date(timeIntervalSince1970: 1_700_000_000)
        shoppingItem.updatedAt = Date(timeIntervalSince1970: 1_700_000_100)
        shoppingItem.syncStateRaw = ShoppingListSyncState.pendingUpdate.rawValue
        context.insert(shoppingItem)

        try context.save()
    }
}

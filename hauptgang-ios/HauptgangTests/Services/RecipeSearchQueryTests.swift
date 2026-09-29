import GRDB
@testable import Hauptgang
import XCTest

final class RecipeSearchQueryTests: XCTestCase {
    func testSearchableText_foldsCaseAccentsEszettAndStrokedL() {
        XCTAssertEqual(RecipeSearchQuery.searchableText("ŁOSOŚ, Weißkohl & Crème"), "losos, weisskohl & creme")
    }

    func testExpandedTokenVariants_findsRegionalGermanNames() {
        XCTAssertEqual(self.variants(for: "Erdäpfel"), [["erdapfel"], ["kartoffel"]])
        XCTAssertEqual(self.variants(for: "Möhre"), [["mohre"], ["karotte"], ["mohrrube"], ["ruebli"]])
    }

    func testExpandedTokenVariants_mergesEnglishAndGermanNames() {
        XCTAssertEqual(self.variants(for: "aubergine"), [["aubergine"], ["eggplant"], ["melanzani"]])
    }

    func testExpandedTokenVariants_matchesInflectedForms() {
        XCTAssertTrue(self.variants(for: "Tomaten").contains(["paradeiser"]))
        XCTAssertTrue(self.variants(for: "ziemniaków").contains(["kartofl"]))
        XCTAssertTrue(self.variants(for: "shrimps").contains(["prawn"]))
    }

    func testExpandedTokenVariants_shortNamesNeedAnExactMatch() {
        XCTAssertEqual(self.variants(for: "rahmspinat"), [["rahmspinat"]])
        XCTAssertEqual(self.variants(for: "pilze"), [["pilze"]])
    }

    func testFTSIndex_matchesAcrossEszettStrokedLAndRegionalNames() throws {
        let dbQueue = try DatabaseQueue()
        try dbQueue.write { db in
            try RecipeSearchStore.createTables(in: db)
            try RecipeSearchStore.upsertPersisted([
                self.recipe(id: 1, name: "Weißkohlsalat", ingredients: ["1 Weißkohl"]),
                self.recipe(id: 2, name: "Bułka z masłem", ingredients: ["2 bułki"]),
                self.recipe(id: 3, name: "Erdäpfelsalat", ingredients: ["1 kg Erdäpfel"])
            ], in: db)
        }

        XCTAssertEqual(try self.search("weißkohl", in: dbQueue), [1])
        XCTAssertEqual(try self.search("weisskohl", in: dbQueue), [1])
        XCTAssertEqual(try self.search("bulka", in: dbQueue), [2])
        XCTAssertEqual(try self.search("Kartoffel", in: dbQueue), [3])
    }

    private func variants(for query: String) -> [[String]] {
        RecipeSearchQuery.expandedTokenVariants(from: query).first ?? []
    }

    private func recipe(id: Int, name: String, ingredients: [String]) -> SearchIndexDetailInput {
        SearchIndexDetailInput(id: id, name: name, ingredients: ingredients, instructions: [], updatedAt: Date())
    }

    private func search(_ query: String, in dbQueue: DatabaseQueue) throws -> [Int] {
        let match = try XCTUnwrap(RecipeSearchQuery.buildFTSQuery(from: query))
        return try dbQueue.read { db in
            try Int.fetchAll(
                db,
                sql: "SELECT rowid FROM recipes_fts WHERE recipes_fts MATCH ? ORDER BY rowid",
                arguments: [match]
            )
        }
    }
}

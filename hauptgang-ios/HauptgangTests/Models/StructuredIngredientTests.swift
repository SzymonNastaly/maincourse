import Foundation
@testable import Hauptgang
import XCTest

final class StructuredIngredientTests: XCTestCase {
    // MARK: - Decoding (snake_case → camelCase via APIClient strategy)

    private func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }

    func testDecode_decimalsAsStrings() throws {
        let json = """
        {
          "id": 7,
          "position": 0,
          "amount": "0.5",
          "amount_max": "200.0",
          "unit": "g",
          "name": "Flour",
          "note": null,
          "canonical_name": "flour",
          "canonical_unit": "gram",
          "category": "pantry",
          "enrichment_version": 1,
          "raw": "0.5–200 g flour"
        }
        """.data(using: .utf8)!

        let ingredient = try makeDecoder().decode(StructuredIngredient.self, from: json)

        XCTAssertEqual(ingredient.id, 7)
        XCTAssertEqual(ingredient.amount, Decimal(string: "0.5"))
        XCTAssertEqual(ingredient.amountMax, Decimal(string: "200.0"))
        XCTAssertEqual(ingredient.unit, "g")
        XCTAssertEqual(ingredient.name, "Flour")
        XCTAssertEqual(ingredient.canonicalName, "flour")
        XCTAssertEqual(ingredient.canonicalUnit, "gram")
        XCTAssertEqual(ingredient.category, "pantry")
        XCTAssertEqual(ingredient.enrichmentVersion, 1)
        XCTAssertEqual(ingredient.raw, "0.5–200 g flour")
        XCTAssertTrue(ingredient.hasStructuredFields)
    }

    func testDecode_nullDecimalsBecomeNil() throws {
        let json = """
        {
          "id": 1,
          "position": 0,
          "amount": null,
          "amount_max": null,
          "unit": null,
          "name": "salt to taste",
          "note": null,
          "raw": "salt to taste"
        }
        """.data(using: .utf8)!

        let ingredient = try makeDecoder().decode(StructuredIngredient.self, from: json)

        XCTAssertNil(ingredient.amount)
        XCTAssertNil(ingredient.amountMax)
        XCTAssertNil(ingredient.unit)
        XCTAssertFalse(ingredient.hasStructuredFields)
    }

    func testDecode_unitOnlyIsConsideredStructured() throws {
        let json = """
        {
          "id": 1,
          "position": 0,
          "amount": null,
          "amount_max": null,
          "unit": "pinch",
          "name": "salt",
          "note": null,
          "raw": "pinch of salt"
        }
        """.data(using: .utf8)!

        let ingredient = try makeDecoder().decode(StructuredIngredient.self, from: json)
        XCTAssertTrue(ingredient.hasStructuredFields)
    }

    // MARK: - Round trip

    func testEncodeDecodeRoundTrip_preservesDecimals() throws {
        let ingredient = StructuredIngredient(
            id: 1,
            position: 0,
            amount: Decimal(string: "0.5"),
            amountMax: Decimal(string: "200.0"),
            unit: "g",
            name: "Sugar",
            note: "fine",
            canonicalName: "sugar",
            canonicalUnit: "gram",
            category: "pantry",
            enrichmentVersion: 1,
            raw: "0.5–200 g sugar, fine"
        )

        // Use a non-snake-case decoder to mimic PersistedRecipe's storage path.
        let data = try JSONEncoder().encode(ingredient)
        let roundTripped = try JSONDecoder().decode(StructuredIngredient.self, from: data)

        XCTAssertEqual(roundTripped, ingredient)
    }

    // MARK: - resolvedIngredients fallback

    func testResolvedIngredients_usesStructuredWhenPresent() {
        let recipe = self.makeRecipe(
            ingredients: ["fallback row"],
            structured: [
                StructuredIngredient(id: 10, position: 1, name: "salt", raw: "raw salt"),
                StructuredIngredient(id: 11, position: 0, name: "sugar", raw: "raw sugar")
            ]
        )

        let resolved = recipe.resolvedIngredients

        XCTAssertEqual(resolved.count, 2)
        XCTAssertEqual(resolved[0].name, "sugar", "should be sorted by position")
        XCTAssertEqual(resolved[1].name, "salt")
    }

    func testResolvedIngredients_fallsBackToStringsWhenStructuredMissing() {
        let recipe = self.makeRecipe(
            ingredients: ["200g flour", "1 tsp salt"],
            structured: nil
        )

        let resolved = recipe.resolvedIngredients

        XCTAssertEqual(resolved.count, 2)
        XCTAssertEqual(resolved[0].raw, "200g flour")
        XCTAssertEqual(resolved[0].name, "200g flour")
        XCTAssertLessThan(resolved[0].id, 0, "synthetic ids should be negative to avoid collisions")
        XCTAssertFalse(resolved[0].hasStructuredFields)
    }

    func testResolvedIngredients_fallsBackWhenStructuredIsEmptyArray() {
        let recipe = self.makeRecipe(ingredients: ["just one"], structured: [])
        XCTAssertEqual(recipe.resolvedIngredients.first?.raw, "just one")
    }

    // MARK: - Helpers

    private func makeRecipe(
        ingredients: [String],
        structured: [StructuredIngredient]?
    ) -> RecipeDetail {
        RecipeDetail(
            id: 1,
            name: "Test",
            favorite: false,
            ingredients: ingredients,
            structuredIngredients: structured,
            instructions: [],
            createdAt: Date(),
            updatedAt: Date()
        )
    }
}

import Foundation
@testable import Hauptgang
import XCTest

final class ShoppingCategoryTests: XCTestCase {
    func testServerValuesMapAndUnknownFallsBackToOther() {
        XCTAssertEqual(ShoppingCategory(serverValue: "dairy_eggs"), .dairyEggs)
        XCTAssertEqual(ShoppingCategory(serverValue: "oils_spices_condiments"), .oilsSpicesCondiments)
        XCTAssertEqual(ShoppingCategory(serverValue: "deli"), .other)
        XCTAssertEqual(ShoppingCategory(serverValue: nil), .other)
    }

    func testOrderMatchesServer() {
        XCTAssertEqual(
            ShoppingCategory.allCases.map(\.rawValue),
            [
                "produce", "bakery", "meat_seafood", "dairy_eggs", "pantry",
                "oils_spices_condiments", "frozen", "beverages", "household", "other"
            ]
        )
    }

    func testHintMatchesParsedOrCanonicalNameIgnoringCase() {
        let ingredients = [
            self.ingredient(name: "Milch", canonicalName: "milk", category: "dairy_eggs"),
            self.ingredient(name: "Butter", canonicalName: "butter", category: "dairy_eggs")
        ]

        XCTAssertEqual(
            ShoppingCategoryHint.lookup(name: "  milch ", in: ingredients),
            ShoppingCategoryHint(category: "dairy_eggs", canonicalName: "milk")
        )
        XCTAssertEqual(ShoppingCategoryHint.lookup(name: "Milk", in: ingredients)?.category, "dairy_eggs")
        XCTAssertNil(ShoppingCategoryHint.lookup(name: "Zucker", in: ingredients))
        XCTAssertNil(ShoppingCategoryHint.lookup(name: " ", in: ingredients))
    }

    func testHintPicksMostCommonValidCategory() {
        let ingredients = [
            self.ingredient(name: "Chili", canonicalName: "chili", category: "produce"),
            self.ingredient(name: "Chili", canonicalName: "chili powder", category: "oils_spices_condiments"),
            self.ingredient(name: "Chili", canonicalName: "chili powder", category: "oils_spices_condiments"),
            self.ingredient(name: "Chili", canonicalName: "chili", category: "not_an_aisle"),
            self.ingredient(name: "Chili", canonicalName: "chili", category: "not_an_aisle"),
            self.ingredient(name: "Chili", canonicalName: "chili", category: "not_an_aisle")
        ]

        XCTAssertEqual(
            ShoppingCategoryHint.lookup(name: "chili", in: ingredients),
            ShoppingCategoryHint(category: "oils_spices_condiments", canonicalName: "chili powder")
        )
    }

    private func ingredient(name: String, canonicalName: String, category: String) -> StructuredIngredient {
        StructuredIngredient(
            id: 1,
            position: 0,
            name: name,
            canonicalName: canonicalName,
            category: category,
            raw: name
        )
    }
}

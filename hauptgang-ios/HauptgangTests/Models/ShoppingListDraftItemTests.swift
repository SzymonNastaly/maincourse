import Foundation
@testable import Hauptgang
import XCTest

final class ShoppingListDraftItemTests: XCTestCase {
    func testStapleDraftStartsExcludedAndCanBeIncluded() {
        let ingredient = StructuredIngredient(
            id: 1,
            position: 0,
            name: "Salz",
            canonicalName: "salt",
            shoppingDefaultIncluded: false,
            raw: "Salz nach Geschmack"
        )

        var draft = ShoppingListDraftItem(ingredient: ingredient, scale: 1)

        XCTAssertTrue(draft.isChecked)
        draft.isChecked = false
        XCTAssertFalse(draft.isChecked)
    }

    func testLegacyIngredientStartsIncludedWithRawFallback() {
        let ingredient = StructuredIngredient(id: 1, position: 0, raw: "salt to taste")

        let draft = ShoppingListDraftItem(ingredient: ingredient, scale: 1)

        XCTAssertEqual(draft.name, "salt to taste")
        XCTAssertNil(draft.details)
        XCTAssertFalse(draft.isChecked)
    }

    func testStructuredIngredientScalesQuantityAndKeepsNote() {
        let ingredient = StructuredIngredient(
            id: 1,
            position: 0,
            amount: 2,
            unit: "tbsp",
            name: "olive oil",
            note: "divided",
            raw: "2 tbsp olive oil, divided"
        )

        let draft = ShoppingListDraftItem(ingredient: ingredient, scale: 2)

        XCTAssertEqual(draft.name, "olive oil")
        XCTAssertEqual(draft.details, "4 tbsp, divided")
        XCTAssertFalse(draft.isChecked)
    }
}

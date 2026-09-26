import Foundation
@testable import Hauptgang
import XCTest

final class IngredientFormatterTests: XCTestCase {
    func testFormatQuantity_withAmountAndUnit() {
        let result = IngredientFormatter.formatQuantity(
            amount: Decimal(string: "200"),
            amountMax: nil,
            unit: "g"
        )
        XCTAssertEqual(result, "200 g")
    }

    func testFormatQuantity_stripsTrailingZeros() {
        let result = IngredientFormatter.formatQuantity(
            amount: Decimal(string: "200.0"),
            amountMax: nil,
            unit: "g"
        )
        XCTAssertEqual(result, "200 g")
    }

    func testFormatQuantity_unicodeFractions() {
        XCTAssertEqual(
            IngredientFormatter.formatQuantity(
                amount: Decimal(string: "0.5"),
                amountMax: nil,
                unit: "tsp"
            ),
            "½ tsp"
        )
        XCTAssertEqual(
            IngredientFormatter.formatQuantity(
                amount: Decimal(string: "0.25"),
                amountMax: nil,
                unit: "cup"
            ),
            "¼ cup"
        )
    }

    func testFormatQuantity_range() {
        let result = IngredientFormatter.formatQuantity(
            amount: Decimal(string: "200"),
            amountMax: Decimal(string: "250"),
            unit: "g"
        )
        XCTAssertEqual(result, "200\u{2013}250 g")
    }

    func testFormatQuantity_unitOnly() {
        let result = IngredientFormatter.formatQuantity(
            amount: nil,
            amountMax: nil,
            unit: "pinch"
        )
        XCTAssertEqual(result, "pinch")
    }

    func testFormatQuantity_emptyWhenAllNil() {
        let result = IngredientFormatter.formatQuantity(
            amount: nil,
            amountMax: nil,
            unit: nil
        )
        XCTAssertEqual(result, "")
    }

    func testFormatQuantity_scaledByFactor() {
        // 200 g doubled
        let doubled = IngredientFormatter.formatQuantity(
            amount: Decimal(string: "200"),
            amountMax: nil,
            unit: "g",
            scale: 2
        )
        XCTAssertEqual(doubled, "400 g")

        // Range scales both bounds
        let range = IngredientFormatter.formatQuantity(
            amount: Decimal(string: "200"),
            amountMax: Decimal(string: "250"),
            unit: "g",
            scale: 2
        )
        XCTAssertEqual(range, "400\u{2013}500 g")
    }

    func testFormatQuantity_thirdTspScaledStaysReadable() {
        // 1/3 tsp -> "⅓ tsp" base, "⅔ tsp" doubled
        let base = IngredientFormatter.formatQuantity(
            amount: Decimal(string: "0.3333"),
            amountMax: nil,
            unit: "tsp"
        )
        XCTAssertEqual(base, "⅓ tsp")

        let doubled = IngredientFormatter.formatQuantity(
            amount: Decimal(string: "0.3333"),
            amountMax: nil,
            unit: "tsp",
            scale: 2
        )
        XCTAssertEqual(doubled, "⅔ tsp")
    }

    func testFormatAmount_basic() throws {
        let locale = Locale(identifier: "en_US")
        XCTAssertEqual(
            try IngredientFormatter.formatAmount(XCTUnwrap(Decimal(string: "200.00")), locale: locale),
            "200"
        )
        XCTAssertEqual(try IngredientFormatter.formatAmount(XCTUnwrap(Decimal(string: "1.5")), locale: locale), "1.5")
        XCTAssertEqual(try IngredientFormatter.formatAmount(XCTUnwrap(Decimal(string: "0.5")), locale: locale), "½")
    }

    func testLocalizedDecimalRangePreservesFractionsAndUnits() throws {
        let locale = Locale(identifier: "pl_PL")
        XCTAssertEqual(IngredientFormatter.formatQuantity(
            amount: Decimal(string: "1.5"), amountMax: Decimal(string: "2.75"), unit: "kg", locale: locale
        ), "1,5–2,75 kg")
        XCTAssertEqual(try IngredientFormatter.formatAmount(XCTUnwrap(Decimal(string: "0.5")), locale: locale), "½")
        XCTAssertEqual(
            try IngredientFormatter.formatAmount(XCTUnwrap(Decimal(string: "1.234")), locale: locale),
            "1,23"
        )
    }
}

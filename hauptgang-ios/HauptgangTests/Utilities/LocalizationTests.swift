import Foundation
@testable import Hauptgang
import XCTest

final class LocalizationTests: XCTestCase {
    func testRecipeCountUsesCompiledEnglishPlurals() {
        let locale = Locale(identifier: "en_US")
        XCTAssertEqual(RecipeDisplayFormatter.recipeCount(0, locale: locale), "0 recipes")
        XCTAssertEqual(RecipeDisplayFormatter.recipeCount(1, locale: locale), "1 recipe")
        XCTAssertEqual(RecipeDisplayFormatter.recipeCount(2, locale: locale), "2 recipes")
    }

    func testDurationStaysInMinutesAcrossLocales() {
        for (identifier, expected) in [("en_US", "90m"), ("pl_PL", "90min"), ("de_DE", "90min")] {
            let locale = Locale(identifier: identifier)
            XCTAssertEqual(RecipeDisplayFormatter.minutes(90, locale: locale), expected)
        }
    }

    func testAppPermissionAndExtensionNameArePackaged() throws {
        XCTAssertEqual(Bundle.main.localizedString(
            forKey: "NSCameraUsageDescription", value: "missing", table: "InfoPlist"
        ), "MainCourse uses your camera to photograph recipes for import.")

        let plugins = try XCTUnwrap(Bundle.main.builtInPlugInsURL)
        let bundle = try XCTUnwrap(Bundle(url: plugins.appendingPathComponent("ImportRecipeExtension.appex")))
        XCTAssertEqual(bundle.localizedString(
            forKey: "CFBundleDisplayName", value: "missing", table: "InfoPlist"
        ), "Import to MainCourse")
        XCTAssertEqual(bundle.localizedString(
            forKey: "Import Started!", value: "missing", table: "Localizable"
        ), "Import Started!")
        XCTAssertEqual(bundle.localizedString(
            forKey: "Invalid email or password", value: "missing", table: "Localizable"
        ), "Invalid email or password", "Shared API errors must also be in the extension bundle")
    }

    func testServerErrorFormatsStatusCode() {
        XCTAssertEqual(
            APIError.serverError(statusCode: 503).errorDescription,
            "Server error (503). Please try again later."
        )
    }
}

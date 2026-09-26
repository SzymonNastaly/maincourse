import Foundation
@testable import Hauptgang
import XCTest

final class APIProblemTests: XCTestCase {
    func testKnownCodeIgnoresProseAndValidationKeepsInterpolationData() throws {
        let problem = try self.decode("""
        {"error_code":"validation_failed","errors":["DO NOT DISPLAY"],"error_details":[
          {"field":"name","code":"too_long","params":{"count":50}},
          {"field":"email_address","code":"taken","params":{}}
        ]}
        """)
        XCTAssertEqual(problem.message(), "The maximum character count for Name is 50.\nEmail is already in use.")
    }

    func testUnknownCodesAndFieldsHaveLocalFallbacks() throws {
        let unknown = try self.decode(#"{"error_code":"future_code","error":"DO NOT DISPLAY"}"#)
        XCTAssertEqual(unknown.message(), "Could not complete this request. Check your information and try again.")
        let validation = try self.decode("""
        {"error_code":"validation_failed","error_details":[
          {"field":"internal_secret_field","code":"future_rule","params":{}}
        ]}
        """)
        XCTAssertEqual(validation.message(), "This value is invalid. Please check it and try again.")
    }

    func testPolishValidationAndShareExtensionErrorsUseCatalogLookup() throws {
        let fixture = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "Localization", withExtension: "bundle"))
        let polish = try XCTUnwrap(Bundle(url: fixture.appendingPathComponent("pl.lproj")))
        let problem = try self.decode("""
        {"error_code":"validation_failed","error_details":[
          {"field":"name","code":"too_long","params":{"count":50}}
        ]}
        """)
        XCTAssertEqual(
            problem.message(bundle: polish, locale: Locale(identifier: "pl_PL")),
            "Maksymalna liczba znaków dla pola Nazwa: 50."
        )
        let image = try self.decode(#"{"error_code":"image_required"}"#)
        XCTAssertEqual(
            image.message(bundle: polish, locale: Locale(identifier: "pl_PL")),
            "Najpierw wybierz zdjęcie przepisu."
        )

        let plugins = try XCTUnwrap(Bundle.main.builtInPlugInsURL)
        let share = try XCTUnwrap(Bundle(url: plugins.appendingPathComponent("ImportRecipeExtension.appex")))
        XCTAssertEqual(image.message(bundle: share), "Choose a recipe photo first.")
        XCTAssertEqual(
            image.message(bundle: .main, locale: Locale(identifier: "zz_ZZ")),
            "Choose a recipe photo first."
        )
    }

    func testImportLimitUsesServerCountAndImportFailuresUseCodes() throws {
        let problem = try self.decode(#"{"error_code":"import_limit_reached","error_params":{"count":23}}"#)
        XCTAssertEqual(
            problem.message(),
            "You've reached your monthly recipe import limit (23). Upgrade to Pro for unlimited imports."
        )
        XCTAssertEqual(ImportFailure.message(code: "no_recipe_in_photo"), "No recipe found in that photo.")
        XCTAssertEqual(ImportFailure.message(code: nil), ImportFailure.message(code: "future_error"))
    }

    private func decode(_ json: String) throws -> APIProblem {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(APIProblem.self, from: Data(json.utf8))
    }
}

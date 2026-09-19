@testable import Hauptgang
import XCTest

final class RecipeStarterMarkerTests: XCTestCase {
    func testStarterMarkerDecodesFromAPIKey() throws {
        let payload = """
        {
          "id": 42,
          "name": "Starter recipe",
          "favorite": false,
          "starter_recipe_key": "tomato-orzo-v1",
          "updated_at": "2026-09-19T12:00:00Z"
        }
        """

        let recipe = try self.decoder().decode(RecipeListItem.self, from: Data(payload.utf8))

        XCTAssertEqual(recipe.starterRecipeKey, "tomato-orzo-v1")
    }

    func testOlderListPayloadWithoutStarterMarkerDecodesAsNil() throws {
        let payload = """
        {
          "id": 42,
          "name": "Existing recipe",
          "favorite": false,
          "updated_at": "2026-09-19T12:00:00Z"
        }
        """

        let recipe = try self.decoder().decode(RecipeListItem.self, from: Data(payload.utf8))

        XCTAssertNil(recipe.starterRecipeKey)
    }

    func testOlderDetailPayloadWithoutStarterMarkerDecodesAsNil() throws {
        let payload = """
        {
          "id": 42,
          "name": "Existing recipe",
          "favorite": false,
          "ingredients": ["1 onion"],
          "instructions": ["Cook it"],
          "tags": [],
          "created_at": "2026-09-18T12:00:00Z",
          "updated_at": "2026-09-19T12:00:00Z"
        }
        """

        let recipe = try self.decoder().decode(RecipeDetail.self, from: Data(payload.utf8))

        XCTAssertNil(recipe.starterRecipeKey)
    }

    private func decoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}

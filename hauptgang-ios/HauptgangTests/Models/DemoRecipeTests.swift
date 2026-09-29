import Foundation
@testable import Hauptgang
import Testing
import UIKit

struct DemoRecipeTests {
    @Test func bundledExampleHasMatchingCookableContentAndImage() throws {
        let url = try #require(Bundle.main.url(forResource: "tomato-orzo-v1", withExtension: "json"))
        let object = try #require(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let ingredients = try #require(object["ingredients"] as? [[String: Any]])
        let instructions = try #require(object["instructions"] as? [String])
        #expect(ingredients.count >= 5)
        #expect(instructions.count >= 3)
        #expect((object["servings"] as? Int) == 2)
        let orzo = try #require(ingredients.first { ($0["name"] as? String) == "orzo" })
        #expect((orzo["amount"] as? Int) == 160)
        #expect((orzo["unit"] as? String) == "g")
        let imageName = try #require(object["image_name"] as? String)
        let imageURL = try #require(Bundle.main.url(forResource: imageName, withExtension: nil))
        let image = try #require(UIImage(contentsOfFile: imageURL.path))
        #expect(image.size.width >= 800)
        #expect(UIImage(named: imageName) != nil)
    }

    @Test func eachAppLanguagePreviewsAndSavesItsOwnBundledVersion() throws {
        #expect(DemoRecipe.key(forLanguage: "en") == "tomato-orzo-v1")
        #expect(DemoRecipe.key(forLanguage: "fr") == "tomato-orzo-v1")
        #expect(DemoRecipe.key(forLanguage: nil) == "tomato-orzo-v1")
        let english = try DemoRecipe.load(key: "tomato-orzo-v1")
        for (language, name) in [
            ("de", "Orzo mit Tomaten und Kichererbsen aus einer Pfanne"),
            ("pl", "Orzo z pomidorami i ciecierzycą z jednej patelni")
        ] {
            let key = DemoRecipe.key(forLanguage: language)
            let sample = try DemoRecipe.load(key: key)
            #expect(sample.key == key)
            #expect(sample.name == name)
            #expect(sample.imageName == english.imageName)
            #expect(sample.ingredients.map(\.amount) == english.ingredients.map(\.amount))
        }
    }

    @Test func previewRetainsStructuredAmountsForPortionScaling() throws {
        let sample = try DemoRecipe.load()
        #expect(sample.key == "tomato-orzo-v1")
        #expect(sample.recipeDetail.ingredients.count == sample.recipeDetail.resolvedIngredients.count)
        let orzo = try #require(sample.recipeDetail.resolvedIngredients.first { $0.name == "orzo" })
        #expect(IngredientFormatter
            .formatQuantity(amount: orzo.amount, amountMax: nil, unit: orzo.unit, scale: 2) == "320 g")
    }
}

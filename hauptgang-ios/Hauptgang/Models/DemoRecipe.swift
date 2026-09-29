import Foundation

/// The preview and the server's saved copy read this same bundled source.
struct DemoRecipe: Decodable, Sendable {
    struct Ingredient: Decodable, Sendable {
        let raw: String
        let amount: Decimal?
        let unit: String?
        let name: String
        let note: String?
    }

    let key: String
    let name: String
    let creator: String
    let caption: String
    let prepTime: Int
    let cookTime: Int
    let servings: Int
    let imageName: String
    let ingredients: [Ingredient]
    let instructions: [String]

    /// Each app language previews, and saves, its own immutable version. English is
    /// the fallback and the only version older apps know.
    static func key(forLanguage language: String?) -> String {
        switch language {
        case "de": "tomato-orzo-de-v1"
        case "pl": "tomato-orzo-pl-v1"
        default: "tomato-orzo-v1"
        }
    }

    static func load(bundle: Bundle = .main) throws -> DemoRecipe {
        try self.load(key: self.key(forLanguage: Bundle.main.preferredLocalizations.first), bundle: bundle)
    }

    static func load(key: String, bundle: Bundle = .main) throws -> DemoRecipe {
        guard let url = bundle.url(forResource: key, withExtension: "json") else {
            throw CocoaError(.fileNoSuchFile)
        }
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(Self.self, from: Data(contentsOf: url))
    }

    var recipeDetail: RecipeDetail {
        RecipeDetail(
            id: -1, name: self.name, prepTime: self.prepTime, cookTime: self.cookTime,
            favorite: false, servings: self.servings, ingredients: self.ingredients.map(\.raw),
            structuredIngredients: self.ingredients.enumerated().map { index, ingredient in
                StructuredIngredient(
                    id: -(index + 1), position: index, amount: ingredient.amount,
                    unit: ingredient.unit, name: ingredient.name, note: ingredient.note, raw: ingredient.raw
                )
            },
            instructions: self.instructions, createdAt: .distantPast, updatedAt: .distantPast
        )
    }
}

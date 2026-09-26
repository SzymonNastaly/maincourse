import Foundation

// MARK: - Recipe List Item

/// Represents a recipe in list views - from GET /api/v1/recipes
struct RecipeListItem: Codable, Identifiable {
    let id: Int
    let name: String
    let prepTime: Int?
    let cookTime: Int?
    let favorite: Bool
    // swiftlint:disable:next todo
    // TODO: Remove legacy coverImageUrl fallback once the backend no longer serves
    // the old cover_image_url field for older app builds.
    let coverImageUrl: String?
    let coverImages: RecipeCoverImages?
    let importStatus: String?
    let errorMessage: String?
    let importErrorCode: String?
    let starterRecipeKey: String?
    let updatedAt: Date

    init(
        id: Int,
        name: String,
        prepTime: Int? = nil,
        cookTime: Int? = nil,
        favorite: Bool,
        coverImageUrl: String? = nil,
        coverImages: RecipeCoverImages? = nil,
        importStatus: String? = nil,
        errorMessage: String? = nil,
        importErrorCode: String? = nil,
        starterRecipeKey: String? = nil,
        updatedAt: Date
    ) {
        self.id = id
        self.name = name
        self.prepTime = prepTime
        self.cookTime = cookTime
        self.favorite = favorite
        self.coverImageUrl = coverImageUrl
        self.coverImages = coverImages
        self.importStatus = importStatus
        self.errorMessage = errorMessage
        self.importErrorCode = importErrorCode
        self.starterRecipeKey = starterRecipeKey
        self.updatedAt = updatedAt
    }

    var thumbnailCoverImageUrl: String? {
        self.coverImages?.thumbnailURL(fallback: self.coverImageUrl)
    }

    var cardCoverImageUrl: String? {
        self.coverImages?.cardURL(fallback: self.coverImageUrl)
    }

    var heroCoverImageUrl: String? {
        self.coverImages?.heroURL(fallback: self.coverImageUrl)
    }
}

// MARK: - Recipe Detail

/// Full recipe details - from GET /api/v1/recipes/:id
struct RecipeDetail: Codable, Identifiable {
    let id: Int
    let name: String
    let prepTime: Int?
    let cookTime: Int?
    let favorite: Bool
    // swiftlint:disable:next todo
    // TODO: Remove legacy coverImageUrl fallback once the backend no longer serves
    // the old cover_image_url field for older app builds.
    let coverImageUrl: String?
    let coverImages: RecipeCoverImages?
    let servings: Int?
    let ingredients: [String]
    /// Structured ingredient data; nil for older cached responses without it.
    /// Use `resolvedIngredients` to get a non-empty list with raw-string fallback.
    let structuredIngredients: [StructuredIngredient]?
    let instructions: [String]
    let notes: String?
    let sourceUrl: String?
    let starterRecipeKey: String?
    let tags: [RecipeTag]
    let createdAt: Date
    let updatedAt: Date

    init(
        id: Int,
        name: String,
        prepTime: Int? = nil,
        cookTime: Int? = nil,
        favorite: Bool,
        coverImageUrl: String? = nil,
        coverImages: RecipeCoverImages? = nil,
        servings: Int? = nil,
        ingredients: [String],
        structuredIngredients: [StructuredIngredient]? = nil,
        instructions: [String],
        notes: String? = nil,
        sourceUrl: String? = nil,
        starterRecipeKey: String? = nil,
        tags: [RecipeTag] = [],
        createdAt: Date,
        updatedAt: Date
    ) {
        self.id = id
        self.name = name
        self.prepTime = prepTime
        self.cookTime = cookTime
        self.favorite = favorite
        self.coverImageUrl = coverImageUrl
        self.coverImages = coverImages
        self.servings = servings
        self.ingredients = ingredients
        self.structuredIngredients = structuredIngredients
        self.instructions = instructions
        self.notes = notes
        self.sourceUrl = sourceUrl
        self.starterRecipeKey = starterRecipeKey
        self.tags = tags
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    var thumbnailCoverImageUrl: String? {
        self.coverImages?.thumbnailURL(fallback: self.coverImageUrl)
    }

    var cardCoverImageUrl: String? {
        self.coverImages?.cardURL(fallback: self.coverImageUrl)
    }

    var heroCoverImageUrl: String? {
        self.coverImages?.heroURL(fallback: self.coverImageUrl)
    }

    /// Returns structured ingredients when the server provided them, falling
    /// back to a synthetic list built from the legacy `ingredients` strings.
    /// Synthetic rows use negative ids so they never collide with server ids.
    var resolvedIngredients: [StructuredIngredient] {
        if let structured = self.structuredIngredients, !structured.isEmpty {
            return structured.sorted { $0.position < $1.position }
        }
        return self.ingredients.enumerated().map { index, raw in
            StructuredIngredient(
                id: -(index + 1),
                position: index,
                amount: nil,
                amountMax: nil,
                unit: nil,
                name: raw,
                note: nil,
                raw: raw
            )
        }
    }
}

// MARK: - Recipe Tag

struct RecipeTag: Codable, Identifiable {
    let id: Int
    let name: String
}

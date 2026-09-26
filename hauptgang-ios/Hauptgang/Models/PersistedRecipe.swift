import Foundation
import SwiftData

/// SwiftData model for offline recipe storage
@Model
final class PersistedRecipe {
    /// Unique identifier from the API - ensures no duplicates
    @Attribute(.unique) var id: Int

    /// The cookbook this recipe belongs to
    var cookbookId: Int

    var name: String
    var prepTime: Int?
    var cookTime: Int?
    var favorite: Bool
    /// Legacy single-url field kept as a fallback while the store migrates to semantic variants.
    /// Remove this field after older app builds and older persisted stores no longer
    /// depend on the legacy cover_image_url compatibility path.
    var coverImageUrl: String?
    var coverImageThumbUrl: String?
    var coverImageCardUrl: String?
    var coverImageHeroUrl: String?
    var importStatus: String?
    var errorMessage: String?
    var importErrorCode: String?
    var starterRecipeKey: String?
    var updatedAt: Date

    /// Tracks when this record was last synced from the API
    var lastFetchedAt: Date

    // MARK: - Detail Fields (nil until detail is fetched)

    var servings: Int?
    var notes: String?
    var sourceUrl: String?
    var createdAt: Date?

    /// Tracks when details were cached (nil if only list data is cached)
    var detailLastFetchedAt: Date?

    // MARK: - Arrays stored as JSON strings

    var ingredientsJson: String?
    var instructionsJson: String?
    var tagsJson: String?
    /// Optional cached structured ingredients (V5+). Older rows leave this nil
    /// until the next detail sync populates it.
    var structuredIngredientsJson: String?

    // MARK: - Computed Properties for JSON Arrays

    var ingredients: [String] {
        get {
            guard let json = ingredientsJson,
                  let data = json.data(using: .utf8) else { return [] }
            return (try? JSONDecoder().decode([String].self, from: data)) ?? []
        }
        set {
            self.ingredientsJson = try? String(data: JSONEncoder().encode(newValue), encoding: .utf8)
        }
    }

    var instructions: [String] {
        get {
            guard let json = instructionsJson,
                  let data = json.data(using: .utf8) else { return [] }
            return (try? JSONDecoder().decode([String].self, from: data)) ?? []
        }
        set {
            self.instructionsJson = try? String(data: JSONEncoder().encode(newValue), encoding: .utf8)
        }
    }

    var tags: [RecipeTag] {
        get {
            guard let json = tagsJson,
                  let data = json.data(using: .utf8) else { return [] }
            return (try? JSONDecoder().decode([RecipeTag].self, from: data)) ?? []
        }
        set {
            self.tagsJson = try? String(data: JSONEncoder().encode(newValue), encoding: .utf8)
        }
    }

    var structuredIngredients: [StructuredIngredient] {
        get {
            guard let json = structuredIngredientsJson,
                  let data = json.data(using: .utf8) else { return [] }
            return (try? JSONDecoder().decode([StructuredIngredient].self, from: data)) ?? []
        }
        set {
            self.structuredIngredientsJson = try? String(
                data: JSONEncoder().encode(newValue),
                encoding: .utf8
            )
        }
    }

    /// Whether full recipe details have been cached
    var hasDetailsCached: Bool {
        self.detailLastFetchedAt != nil
    }

    var thumbnailCoverImageUrl: String? {
        self.coverImageThumbUrl
    }

    var cardCoverImageUrl: String? {
        self.coverImageCardUrl
    }

    var heroCoverImageUrl: String? {
        self.coverImageHeroUrl
    }

    // MARK: - Initializers

    init(
        id: Int,
        cookbookId: Int = 0,
        name: String,
        prepTime: Int? = nil,
        cookTime: Int? = nil,
        favorite: Bool = false,
        coverImageUrl: String? = nil,
        coverImageThumbUrl: String? = nil,
        coverImageCardUrl: String? = nil,
        coverImageHeroUrl: String? = nil,
        importStatus: String? = nil,
        errorMessage: String? = nil,
        importErrorCode: String? = nil,
        starterRecipeKey: String? = nil,
        updatedAt: Date,
        lastFetchedAt: Date = Date()
    ) {
        self.id = id
        self.cookbookId = cookbookId
        self.name = name
        self.prepTime = prepTime
        self.cookTime = cookTime
        self.favorite = favorite
        self.coverImageUrl = coverImageUrl
        self.coverImageThumbUrl = coverImageThumbUrl
        self.coverImageCardUrl = coverImageCardUrl
        self.coverImageHeroUrl = coverImageHeroUrl
        self.importStatus = importStatus
        self.errorMessage = errorMessage
        self.importErrorCode = importErrorCode
        self.starterRecipeKey = starterRecipeKey
        self.updatedAt = updatedAt
        self.lastFetchedAt = lastFetchedAt
    }

    /// Convenience initializer from API list response
    convenience init(from listItem: RecipeListItem, cookbookId: Int = 0) {
        self.init(
            id: listItem.id,
            cookbookId: cookbookId,
            name: listItem.name,
            prepTime: listItem.prepTime,
            cookTime: listItem.cookTime,
            favorite: listItem.favorite,
            coverImageUrl: listItem.coverImageUrl,
            coverImageThumbUrl: listItem.thumbnailCoverImageUrl,
            coverImageCardUrl: listItem.cardCoverImageUrl,
            coverImageHeroUrl: listItem.heroCoverImageUrl,
            importStatus: listItem.importStatus,
            errorMessage: listItem.errorMessage,
            importErrorCode: listItem.importErrorCode,
            starterRecipeKey: listItem.starterRecipeKey,
            updatedAt: listItem.updatedAt
        )
    }

    /// Convenience initializer from API detail response
    convenience init(from detail: RecipeDetail, cookbookId: Int = 0) {
        self.init(
            id: detail.id,
            cookbookId: cookbookId,
            name: detail.name,
            prepTime: detail.prepTime,
            cookTime: detail.cookTime,
            favorite: detail.favorite,
            coverImageUrl: detail.coverImageUrl,
            coverImageThumbUrl: detail.thumbnailCoverImageUrl,
            coverImageCardUrl: detail.cardCoverImageUrl,
            coverImageHeroUrl: detail.heroCoverImageUrl,
            starterRecipeKey: detail.starterRecipeKey,
            updatedAt: detail.updatedAt
        )
        self.updateDetails(from: detail)
    }

    // MARK: - Update Methods

    /// Update this model from a newer API list response
    func update(from listItem: RecipeListItem, cookbookId: Int? = nil) {
        if let cookbookId {
            self.cookbookId = cookbookId
        }
        self.name = listItem.name
        self.prepTime = listItem.prepTime
        self.cookTime = listItem.cookTime
        self.favorite = listItem.favorite
        self.coverImageUrl = listItem.coverImageUrl
        self.coverImageThumbUrl = listItem.thumbnailCoverImageUrl
        self.coverImageCardUrl = listItem.cardCoverImageUrl
        self.coverImageHeroUrl = listItem.heroCoverImageUrl
        self.importStatus = listItem.importStatus
        self.errorMessage = listItem.errorMessage
        self.importErrorCode = listItem.importErrorCode
        self.starterRecipeKey = listItem.starterRecipeKey
        self.updatedAt = listItem.updatedAt
        self.lastFetchedAt = Date()
    }

    /// Update this model with full detail data from API
    func update(from detail: RecipeDetail, cookbookId: Int? = nil) {
        if let cookbookId {
            self.cookbookId = cookbookId
        }
        self.name = detail.name
        self.prepTime = detail.prepTime
        self.cookTime = detail.cookTime
        self.favorite = detail.favorite
        self.coverImageUrl = detail.coverImageUrl
        self.coverImageThumbUrl = detail.thumbnailCoverImageUrl
        self.coverImageCardUrl = detail.cardCoverImageUrl
        self.coverImageHeroUrl = detail.heroCoverImageUrl
        self.starterRecipeKey = detail.starterRecipeKey
        self.updatedAt = detail.updatedAt
        self.lastFetchedAt = Date()
        self.updateDetails(from: detail)
    }

    /// Helper to update detail-specific fields
    private func updateDetails(from detail: RecipeDetail) {
        self.servings = detail.servings
        self.notes = detail.notes
        self.sourceUrl = detail.sourceUrl
        self.createdAt = detail.createdAt
        self.ingredients = detail.ingredients
        if let structured = detail.structuredIngredients {
            self.structuredIngredients = structured
        } else {
            self.structuredIngredientsJson = nil
        }
        self.instructions = detail.instructions
        self.tags = detail.tags
        self.detailLastFetchedAt = Date()
    }

    // MARK: - Conversion

    /// Convert to RecipeDetail for use in views.
    /// Returns partial data (empty ingredients/instructions) when only list data is cached.
    func toRecipeDetail() -> RecipeDetail {
        RecipeDetail(
            id: self.id,
            name: self.name,
            prepTime: self.prepTime,
            cookTime: self.cookTime,
            favorite: self.favorite,
            coverImageUrl: self.heroCoverImageUrl,
            coverImages: RecipeCoverImages(
                thumb: self.thumbnailCoverImageUrl,
                card: self.cardCoverImageUrl,
                hero: self.heroCoverImageUrl
            ),
            servings: self.servings,
            ingredients: self.ingredients,
            structuredIngredients: self.structuredIngredientsJson != nil ? self.structuredIngredients : nil,
            instructions: self.instructions,
            notes: self.notes,
            sourceUrl: self.sourceUrl,
            starterRecipeKey: self.starterRecipeKey,
            tags: self.tags,
            createdAt: self.createdAt ?? self.updatedAt,
            updatedAt: self.updatedAt
        )
    }
}

import Foundation
import SwiftData

/// Frozen V7 snapshot: starter marker, before import error codes were cached.
enum HauptgangSchemaV7: VersionedSchema {
    static let versionIdentifier = Schema.Version(7, 0, 0)

    static var models: [any PersistentModel.Type] {
        [
            PersistedRecipe.self,
            HauptgangSchemaV6.PersistedShoppingListItem.self,
            HauptgangSchemaV6.PersistedMealPlanDay.self,
            HauptgangSchemaV6.PersistedMealPlanEntry.self
        ]
    }

    @Model
    final class PersistedRecipe {
        @Attribute(.unique) var id: Int
        var cookbookId: Int
        var name: String
        var prepTime: Int?
        var cookTime: Int?
        var favorite: Bool
        var coverImageUrl: String?
        var coverImageThumbUrl: String?
        var coverImageCardUrl: String?
        var coverImageHeroUrl: String?
        var importStatus: String?
        var errorMessage: String?
        var starterRecipeKey: String?
        var updatedAt: Date
        var lastFetchedAt: Date
        var servings: Int?
        var notes: String?
        var sourceUrl: String?
        var createdAt: Date?
        var detailLastFetchedAt: Date?
        var ingredientsJson: String?
        var instructionsJson: String?
        var tagsJson: String?
        var structuredIngredientsJson: String?

        init() {
            self.id = 0
            self.cookbookId = 0
            self.name = ""
            self.favorite = false
            self.updatedAt = Date()
            self.lastFetchedAt = Date()
        }
    }
}

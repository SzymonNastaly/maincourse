import Foundation
import SwiftData

/// Schema V6 — adds optional `details` to `PersistedShoppingListItem` so each
/// shopping list row carries a secondary line (e.g. quantity / preparation).
/// Frozen so later live-model changes do not mutate this historical version.
enum HauptgangSchemaV6: VersionedSchema {
    static let versionIdentifier = Schema.Version(6, 0, 0)

    static var models: [any PersistentModel.Type] {
        [
            PersistedRecipe.self,
            PersistedShoppingListItem.self,
            PersistedMealPlanDay.self,
            PersistedMealPlanEntry.self
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

    @Model
    final class PersistedShoppingListItem {
        @Attribute(.unique) var scopedClientId: String
        var clientId: String
        var cookbookId: Int
        var serverId: Int?
        var name: String
        var details: String?
        var checkedAt: Date?
        var sourceRecipeId: Int?
        var createdAt: Date
        var updatedAt: Date
        var syncStateRaw: String

        init() {
            self.scopedClientId = ""
            self.clientId = ""
            self.cookbookId = 0
            self.name = ""
            self.createdAt = Date()
            self.updatedAt = Date()
            self.syncStateRaw = "synced"
        }
    }

    @Model
    final class PersistedMealPlanDay {
        @Attribute(.unique) var scopedDate: String
        var cookbookId: Int
        var date: String
        var selectedEntryId: Int?
        var selectedByUserId: Int?
        var selectedAt: Date?

        init() {
            self.scopedDate = ""
            self.cookbookId = 0
            self.date = ""
        }
    }

    @Model
    final class PersistedMealPlanEntry {
        @Attribute(.unique) var scopedId: String
        var cookbookId: Int
        var date: String
        var serverId: Int?
        var recipeId: Int
        var recipeName: String
        var recipeCoverImageUrl: String?
        var proposedByEmail: String?
        var voteCount: Int
        var votedByCurrentUser: Bool
        var syncStateRaw: String

        init() {
            self.scopedId = ""
            self.cookbookId = 0
            self.date = ""
            self.recipeId = 0
            self.recipeName = ""
            self.voteCount = 0
            self.votedByCurrentUser = false
            self.syncStateRaw = "synced"
        }
    }
}

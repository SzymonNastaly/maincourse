import SwiftData

/// Schema V7 — adds the optional starter-recipe marker to the live recipe cache.
enum HauptgangSchemaV7: VersionedSchema {
    static let versionIdentifier = Schema.Version(7, 0, 0)

    static var models: [any PersistentModel.Type] {
        [
            Hauptgang.PersistedRecipe.self,
            Hauptgang.PersistedShoppingListItem.self,
            Hauptgang.PersistedMealPlanDay.self,
            Hauptgang.PersistedMealPlanEntry.self
        ]
    }
}

import SwiftData

/// V10 adds the optional `instructionTimersJson` cache to recipes.
enum HauptgangSchemaV10: VersionedSchema {
    static let versionIdentifier = Schema.Version(10, 0, 0)

    static var models: [any PersistentModel.Type] {
        [
            Hauptgang.PersistedRecipe.self,
            Hauptgang.PersistedShoppingListItem.self,
            Hauptgang.PersistedMealPlanDay.self,
            Hauptgang.PersistedMealPlanEntry.self
        ]
    }
}

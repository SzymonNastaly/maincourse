import SwiftData

/// V8 adds the optional import error code; localized prose is never persisted.
enum HauptgangSchemaV8: VersionedSchema {
    static let versionIdentifier = Schema.Version(8, 0, 0)

    static var models: [any PersistentModel.Type] {
        [
            Hauptgang.PersistedRecipe.self,
            Hauptgang.PersistedShoppingListItem.self,
            Hauptgang.PersistedMealPlanDay.self,
            Hauptgang.PersistedMealPlanEntry.self
        ]
    }
}

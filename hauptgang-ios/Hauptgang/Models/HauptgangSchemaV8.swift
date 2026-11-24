import SwiftData

/// V8 adds the optional import error code; localized prose is never persisted.
/// Its shopping list item is frozen at the V6 shape (before aisle categories) and
/// its recipe at the V9 snapshot (before instruction timers).
enum HauptgangSchemaV8: VersionedSchema {
    static let versionIdentifier = Schema.Version(8, 0, 0)

    static var models: [any PersistentModel.Type] {
        [
            HauptgangSchemaV9.PersistedRecipe.self,
            HauptgangSchemaV6.PersistedShoppingListItem.self,
            Hauptgang.PersistedMealPlanDay.self,
            Hauptgang.PersistedMealPlanEntry.self
        ]
    }
}

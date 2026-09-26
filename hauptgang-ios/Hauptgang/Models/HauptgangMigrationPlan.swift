import Foundation
import SwiftData

enum HauptgangMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] {
        [
            HauptgangSchemaV1.self,
            HauptgangSchemaV2.self,
            HauptgangSchemaV3.self,
            HauptgangSchemaV4.self,
            HauptgangSchemaV5.self,
            HauptgangSchemaV6.self,
            HauptgangSchemaV7.self,
            HauptgangSchemaV8.self
        ]
    }

    static var stages: [MigrationStage] {
        [
            migrateV1toV2,
            migrateV2toV3,
            migrateV3toV4,
            migrateV4toV5,
            migrateV5toV6,
            migrateV6toV7,
            migrateV7toV8
        ]
    }

    /// V1 → V2: Wipe local cache — data re-syncs from the server.
    static let migrateV1toV2 = MigrationStage.custom(
        fromVersion: HauptgangSchemaV1.self,
        toVersion: HauptgangSchemaV2.self,
        willMigrate: { context in
            try context.delete(model: HauptgangSchemaV1.PersistedRecipe.self)
            try context.delete(model: HauptgangSchemaV1.PersistedShoppingListItem.self)
            try context.save()
        },
        didMigrate: nil
    )

    /// V2 → V3: Lightweight — just adds new meal plan tables, no data migration needed.
    static let migrateV2toV3 = MigrationStage.lightweight(
        fromVersion: HauptgangSchemaV2.self,
        toVersion: HauptgangSchemaV3.self
    )

    /// V3 → V4: Wipe recipe cache — data re-syncs from the server with proper variant URLs.
    static let migrateV3toV4 = MigrationStage.custom(
        fromVersion: HauptgangSchemaV3.self,
        toVersion: HauptgangSchemaV4.self,
        willMigrate: { context in
            try context.delete(model: HauptgangSchemaV3.PersistedRecipe.self)
            try context.save()
        },
        didMigrate: nil
    )

    /// V4 → V5: Lightweight — adds an optional `structuredIngredientsJson` column.
    /// Existing rows simply have `nil` until the next detail sync populates them.
    static let migrateV4toV5 = MigrationStage.lightweight(
        fromVersion: HauptgangSchemaV4.self,
        toVersion: HauptgangSchemaV5.self
    )

    /// V5 → V6: Lightweight — adds an optional `details` column to shopping list items.
    /// Existing rows have `nil` until the next sync (or local edit) populates them.
    static let migrateV5toV6 = MigrationStage.lightweight(
        fromVersion: HauptgangSchemaV5.self,
        toVersion: HauptgangSchemaV6.self
    )

    /// V6 → V7: Lightweight — adds an optional starter-recipe marker.
    /// Existing cached recipes keep their detail data and receive `nil`.
    static let migrateV6toV7 = MigrationStage.lightweight(
        fromVersion: HauptgangSchemaV6.self,
        toVersion: HauptgangSchemaV7.self
    )

    static let migrateV7toV8 = MigrationStage.lightweight(
        fromVersion: HauptgangSchemaV7.self,
        toVersion: HauptgangSchemaV8.self
    )
}

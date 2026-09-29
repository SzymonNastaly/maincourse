import Foundation
import GRDB

enum RecipeSearchStore {
    static func prepareSchema(in db: Database, schemaVersion: String) throws -> Bool {
        try db.create(table: "search_metadata", ifNotExists: true) { table in
            table.column("key", .text).primaryKey()
            table.column("value", .text).notNull()
        }

        let existingVersion = try String.fetchOne(
            db,
            sql: "SELECT value FROM search_metadata WHERE key = ?",
            arguments: ["schema_version"]
        )

        if existingVersion != schemaVersion {
            try self.dropTables(in: db)
            try self.createTables(in: db)
            try db.execute(
                sql: "INSERT OR REPLACE INTO search_metadata (key, value) VALUES (?, ?)",
                arguments: ["schema_version", schemaVersion]
            )
            return true
        }

        return false
    }

    static func createTables(in db: Database) throws {
        try db.execute(sql: """
        CREATE TABLE IF NOT EXISTS recipes (
            id INTEGER PRIMARY KEY,
            name TEXT,
            ingredients TEXT,
            instructions TEXT,
            updated_at TEXT
        );
        """)

        try db.execute(sql: """
        CREATE VIRTUAL TABLE IF NOT EXISTS recipes_fts USING fts5(
            name,
            ingredients,
            instructions,
            tokenize='unicode61 remove_diacritics 2',
            prefix='2 3 4'
        );
        """)
    }

    static func dropTables(in db: Database) throws {
        try db.execute(sql: "DROP TABLE IF EXISTS recipes_fts")
        try db.execute(sql: "DROP TABLE IF EXISTS recipes")
    }

    static func upsertNames(_ recipes: [SearchIndexNameInput], in db: Database) throws {
        for recipe in recipes {
            try db.execute(
                sql: """
                INSERT INTO recipes (id, name, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    updated_at = excluded.updated_at
                """,
                arguments: [recipe.id, recipe.name, self.iso8601String(from: recipe.updatedAt)]
            )

            let stored = try Row.fetchOne(
                db,
                sql: "SELECT ingredients, instructions FROM recipes WHERE id = ?",
                arguments: [recipe.id]
            )
            try self.replaceIndexedText(
                id: recipe.id,
                name: recipe.name,
                ingredients: stored?["ingredients"] ?? "",
                instructions: stored?["instructions"] ?? "",
                in: db
            )
        }
    }

    static func upsertPersisted(_ recipes: [SearchIndexDetailInput], in db: Database) throws {
        for recipe in recipes {
            let ingredients = recipe.ingredients.joined(separator: "\n")
            let instructions = recipe.instructions.joined(separator: "\n")

            try db.execute(
                sql: """
                INSERT INTO recipes (id, name, ingredients, instructions, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    ingredients = excluded.ingredients,
                    instructions = excluded.instructions,
                    updated_at = excluded.updated_at
                """,
                arguments: [
                    recipe.id,
                    recipe.name,
                    ingredients,
                    instructions,
                    Self.iso8601String(from: recipe.updatedAt)
                ]
            )

            try self.replaceIndexedText(
                id: recipe.id,
                name: recipe.name,
                ingredients: ingredients,
                instructions: instructions,
                in: db
            )
        }
    }

    static func upsertDetails(_ details: [SearchIndexDetailInput], in db: Database) throws {
        try self.upsertPersisted(details, in: db)
    }

    /// The FTS table holds text in `RecipeSearchQuery.searchableText` form, so the
    /// index and queries fold ß, ł and case the same way. Bump the index's
    /// schema version when that folding changes.
    private static func replaceIndexedText(
        id: Int,
        name: String,
        ingredients: String,
        instructions: String,
        in db: Database
    ) throws {
        try db.execute(sql: "DELETE FROM recipes_fts WHERE rowid = ?", arguments: [id])
        try db.execute(
            sql: "INSERT INTO recipes_fts (rowid, name, ingredients, instructions) VALUES (?, ?, ?, ?)",
            arguments: [
                id,
                RecipeSearchQuery.searchableText(name),
                RecipeSearchQuery.searchableText(ingredients),
                RecipeSearchQuery.searchableText(instructions)
            ]
        )
    }

    private static func iso8601String(from date: Date) -> String {
        date.formatted(Date.ISO8601FormatStyle(includingFractionalSeconds: true))
    }
}

import Foundation
import GRDB
import os
import SQLite3

protocol RecipeSearchIndexProtocol: Sendable {
    func configure(userId: Int, cookbookId: Int) async
    func isAvailable() async -> Bool
    func needsRebuild() async -> Bool
    func rebuildIndex(with recipes: [SearchIndexDetailInput]) async
    func indexNames(_ recipes: [SearchIndexNameInput]) async
    func indexDetails(_ details: [SearchIndexDetailInput]) async
    func delete(ids: [Int]) async
    func search(_ query: String, limit: Int) async -> [Int]
    func reset() async
}

actor RecipeSearchIndex: RecipeSearchIndexProtocol {
    static let shared = RecipeSearchIndex()
    private static let schemaVersion = "4"

    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "RecipeSearchIndex")
    private var dbQueue: DatabaseQueue?
    private var available = false
    private var requiresRebuild = false
    private var currentUserId: Int?
    private var currentCookbookId: Int?

    func configure(userId: Int, cookbookId: Int) async {
        guard self.currentUserId != userId || self.currentCookbookId != cookbookId else { return }
        self.currentUserId = userId
        self.currentCookbookId = cookbookId

        guard self.isFTS5Available() else {
            self.logger.info("FTS5 not available; search index disabled")
            self.available = false
            self.dbQueue = nil
            self.requiresRebuild = false
            return
        }

        do {
            let dbPath = try self.databasePath(for: userId, cookbookId: cookbookId)
            self.dbQueue = try DatabaseQueue(path: dbPath)

            let needsRebuild = try await self.dbQueue?.write { db in
                try RecipeSearchStore.prepareSchema(in: db, schemaVersion: Self.schemaVersion)
            }

            self.available = true
            self.requiresRebuild = needsRebuild ?? false
            if needsRebuild == true {
                self.logger.info("Search index schema updated; rebuild required")
            }
        } catch {
            self.logger.error("Failed to initialize search index: \(error.localizedDescription)")
            self.available = false
            self.dbQueue = nil
            self.requiresRebuild = false
        }
    }

    func isAvailable() -> Bool {
        self.available
    }

    func needsRebuild() -> Bool {
        self.requiresRebuild
    }

    func rebuildIndex(with recipes: [SearchIndexDetailInput]) async {
        guard self.available, let dbQueue else { return }

        do {
            try await dbQueue.write { db in
                try RecipeSearchStore.dropTables(in: db)
                try RecipeSearchStore.createTables(in: db)
                try RecipeSearchStore.upsertPersisted(recipes, in: db)
            }
            self.requiresRebuild = false
        } catch {
            if await self.recoverFromCorruptionIfNeeded(
                error: error,
                userId: self.currentUserId,
                cookbookId: self.currentCookbookId
            ) {
                await self.rebuildIndex(with: recipes)
                return
            }
            self.logger.error("Failed to rebuild search index: \(error.localizedDescription)")
        }
    }

    func indexNames(_ recipes: [SearchIndexNameInput]) async {
        guard self.available, let dbQueue, !recipes.isEmpty else { return }

        do {
            try await dbQueue.write { db in
                try RecipeSearchStore.upsertNames(recipes, in: db)
            }
        } catch {
            if await self.recoverFromCorruptionIfNeeded(
                error: error,
                userId: self.currentUserId,
                cookbookId: self.currentCookbookId
            ) {
                await self.indexNames(recipes)
                return
            }
            self.logger.error("Failed to index recipe names: \(error.localizedDescription)")
        }
    }

    func indexDetails(_ details: [SearchIndexDetailInput]) async {
        guard self.available, let dbQueue, !details.isEmpty else { return }

        do {
            try await dbQueue.write { db in
                try RecipeSearchStore.upsertDetails(details, in: db)
            }
        } catch {
            if await self.recoverFromCorruptionIfNeeded(
                error: error,
                userId: self.currentUserId,
                cookbookId: self.currentCookbookId
            ) {
                await self.indexDetails(details)
                return
            }
            self.logger.error("Failed to index recipe details: \(error.localizedDescription)")
        }
    }

    func delete(ids: [Int]) async {
        guard self.available, let dbQueue, !ids.isEmpty else { return }

        do {
            try await dbQueue.write { db in
                let ids = ids
                try db.execute(
                    sql: "DELETE FROM recipes WHERE id IN (\(ids.map { _ in "?" }.joined(separator: ",")))",
                    arguments: StatementArguments(ids)
                )
                try db.execute(
                    sql: "DELETE FROM recipes_fts WHERE rowid IN (\(ids.map { _ in "?" }.joined(separator: ",")))",
                    arguments: StatementArguments(ids)
                )
            }
        } catch {
            if await self.recoverFromCorruptionIfNeeded(
                error: error,
                userId: self.currentUserId,
                cookbookId: self.currentCookbookId
            ) {
                await self.delete(ids: ids)
                return
            }
            self.logger.error("Failed to delete recipes from search index: \(error.localizedDescription)")
        }
    }

    func search(_ query: String, limit: Int) async -> [Int] {
        guard self.available, let dbQueue else { return [] }
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let ftsQuery = Self.buildFTSQuery(from: trimmed) else { return [] }

        do {
            return try await dbQueue.read { db in
                let rows = try Row.fetchAll(
                    db,
                    sql: """
                    SELECT recipes.id
                    FROM recipes_fts
                    JOIN recipes ON recipes_fts.rowid = recipes.id
                    WHERE recipes_fts MATCH ?
                    ORDER BY bm25(recipes_fts, 10.0, 3.0, 1.0), recipes.updated_at DESC
                    LIMIT ?
                    """,
                    arguments: [ftsQuery, max(limit, 1)]
                )
                return rows.compactMap { $0["id"] as Int? }
            }
        } catch {
            if await self.recoverFromCorruptionIfNeeded(
                error: error,
                userId: self.currentUserId,
                cookbookId: self.currentCookbookId
            ) {
                return await self.search(query, limit: limit)
            }
            self.logger.error("Search query failed: \(error.localizedDescription)")
            return []
        }
    }

    func reset() {
        self.resetDatabaseFile()
    }

    // MARK: - Private

    private func isFTS5Available() -> Bool {
        sqlite3_compileoption_used("ENABLE_FTS5") == 1
    }

    private func databasePath(for userId: Int, cookbookId: Int) throws -> String {
        try self.databaseURL(for: userId, cookbookId: cookbookId).path
    }

    private func databaseURL(for userId: Int, cookbookId: Int) throws -> URL {
        let baseURL = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )

        let directory = baseURL.appendingPathComponent("recipe-search", isDirectory: true)
        if !FileManager.default.fileExists(atPath: directory.path) {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        }

        return directory.appendingPathComponent("user-\(userId)-cookbook-\(cookbookId).sqlite")
    }

    private func resetDatabaseFile() {
        guard let userId = self.currentUserId, let cookbookId = self.currentCookbookId else { return }
        do {
            let url = try self.databaseURL(for: userId, cookbookId: cookbookId)
            if FileManager.default.fileExists(atPath: url.path) {
                try FileManager.default.removeItem(at: url)
            }
        } catch {
            self.logger.error("Failed to delete search index file: \(error.localizedDescription)")
        }

        self.dbQueue = nil
        self.available = false
        self.requiresRebuild = false
        self.currentUserId = nil
        self.currentCookbookId = nil
    }

    private static func buildFTSQuery(from raw: String) -> String? {
        RecipeSearchQuery.buildFTSQuery(from: raw)
    }

    private func recoverFromCorruptionIfNeeded(error: Error, userId: Int?, cookbookId: Int?) async -> Bool {
        guard let userId, let cookbookId else { return false }
        guard let dbError = error as? DatabaseError else { return false }
        let isCorrupt = dbError.resultCode == .SQLITE_CORRUPT || dbError.resultCode == .SQLITE_NOTADB
        guard isCorrupt else { return false }

        self.logger.error("Search index corrupted; rebuilding: \(dbError.message ?? "unknown error")")
        self.resetDatabaseFile()
        await self.configure(userId: userId, cookbookId: cookbookId)
        return self.available
    }
}

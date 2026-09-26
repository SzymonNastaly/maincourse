import Foundation
import os
import SwiftData

enum MealPlanRepositoryError: Error, LocalizedError {
    case notConfigured

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            String(localized: "Repository not configured with model context")
        }
    }
}

@MainActor
protocol MealPlanRepositoryProtocol {
    func configure(modelContext: ModelContext)
    func getEntries(cookbookId: Int, date: String) throws -> [PersistedMealPlanEntry]
    func saveDays(_ days: [MealPlanDay], cookbookId: Int) throws
    func addLocalEntry(
        cookbookId: Int,
        date: String,
        recipeId: Int,
        recipeName: String,
        recipeCoverImageUrl: String?
    ) throws
    func getPendingEntries(cookbookId: Int) throws -> [PersistedMealPlanEntry]
    func deletePendingEntry(cookbookId: Int, date: String, recipeId: Int) throws
    func clearAll() throws
}

@MainActor
final class MealPlanRepository: MealPlanRepositoryProtocol {
    private var modelContext: ModelContext?
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "MealPlanRepository")

    func configure(modelContext: ModelContext) {
        self.modelContext = modelContext
        self.logger.info("MealPlanRepository configured with model context")
    }

    func getEntries(cookbookId: Int, date: String) throws -> [PersistedMealPlanEntry] {
        guard let modelContext else { throw MealPlanRepositoryError.notConfigured }

        let descriptor = FetchDescriptor<PersistedMealPlanEntry>(
            predicate: #Predicate { entry in
                entry.cookbookId == cookbookId && entry.date == date
            }
        )
        return try modelContext.fetch(descriptor)
    }

    func saveDays(_ days: [MealPlanDay], cookbookId: Int) throws {
        guard let modelContext else { throw MealPlanRepositoryError.notConfigured }

        for day in days {
            // Upsert day
            let scopedDate = "\(cookbookId)|\(day.date)"
            let dayDescriptor = FetchDescriptor<PersistedMealPlanDay>(
                predicate: #Predicate { $0.scopedDate == scopedDate }
            )
            if let existing = try modelContext.fetch(dayDescriptor).first {
                existing.selectedEntryId = day.selectedEntryId
                existing.selectedByUserId = day.selectedByUserId
                existing.selectedAt = day.selectedAt
            } else {
                let persisted = PersistedMealPlanDay(
                    cookbookId: cookbookId,
                    date: day.date,
                    selectedEntryId: day.selectedEntryId,
                    selectedByUserId: day.selectedByUserId,
                    selectedAt: day.selectedAt
                )
                modelContext.insert(persisted)
            }

            // Sync entries: remove server entries not in response, upsert the rest
            let serverEntryIds = Set(day.entries.map(\.id))
            let entryDescriptor = FetchDescriptor<PersistedMealPlanEntry>(
                predicate: #Predicate { entry in
                    entry.cookbookId == cookbookId && entry.date == day.date
                }
            )
            let localEntries = try modelContext.fetch(entryDescriptor)

            for local in localEntries
                where local
                .syncState == .synced && (local.serverId == nil || !serverEntryIds.contains(local.serverId!)) {
                modelContext.delete(local)
            }

            for apiEntry in day.entries {
                let scopedId = "\(cookbookId)|\(day.date)|\(apiEntry.recipe.id)"
                let existingDescriptor = FetchDescriptor<PersistedMealPlanEntry>(
                    predicate: #Predicate { $0.scopedId == scopedId }
                )
                if let existing = try modelContext.fetch(existingDescriptor).first {
                    existing.serverId = apiEntry.id
                    existing.recipeName = apiEntry.recipe.name
                    existing.recipeCoverImageUrl = apiEntry.recipe.thumbnailCoverImageUrl
                    existing.proposedByEmail = apiEntry.proposedBy?.email
                    existing.voteCount = apiEntry.voteCount
                    existing.votedByCurrentUser = apiEntry.votedByCurrentUser
                    existing.syncState = .synced
                } else {
                    let persisted = PersistedMealPlanEntry(
                        cookbookId: cookbookId,
                        date: day.date,
                        serverId: apiEntry.id,
                        recipeId: apiEntry.recipe.id,
                        recipeName: apiEntry.recipe.name,
                        recipeCoverImageUrl: apiEntry.recipe.thumbnailCoverImageUrl,
                        proposedByEmail: apiEntry.proposedBy?.email,
                        voteCount: apiEntry.voteCount,
                        votedByCurrentUser: apiEntry.votedByCurrentUser,
                        syncState: .synced
                    )
                    modelContext.insert(persisted)
                }
            }
        }

        try modelContext.save()
    }

    func addLocalEntry(
        cookbookId: Int,
        date: String,
        recipeId: Int,
        recipeName: String,
        recipeCoverImageUrl: String?
    ) throws {
        guard let modelContext else { throw MealPlanRepositoryError.notConfigured }

        // Check if entry already exists
        let scopedId = "\(cookbookId)|\(date)|\(recipeId)"
        let descriptor = FetchDescriptor<PersistedMealPlanEntry>(
            predicate: #Predicate { $0.scopedId == scopedId }
        )
        guard try modelContext.fetch(descriptor).isEmpty else { return }

        let entry = PersistedMealPlanEntry(
            cookbookId: cookbookId,
            date: date,
            recipeId: recipeId,
            recipeName: recipeName,
            recipeCoverImageUrl: recipeCoverImageUrl,
            syncState: .pendingCreate
        )
        modelContext.insert(entry)

        // Ensure day record exists
        let dayKey = "\(cookbookId)|\(date)"
        let dayDescriptor = FetchDescriptor<PersistedMealPlanDay>(
            predicate: #Predicate { $0.scopedDate == dayKey }
        )
        if try modelContext.fetch(dayDescriptor).isEmpty {
            let day = PersistedMealPlanDay(cookbookId: cookbookId, date: date)
            modelContext.insert(day)
        }

        try modelContext.save()
    }

    func getPendingEntries(cookbookId: Int) throws -> [PersistedMealPlanEntry] {
        guard let modelContext else { throw MealPlanRepositoryError.notConfigured }

        let descriptor = FetchDescriptor<PersistedMealPlanEntry>(
            predicate: #Predicate { $0.cookbookId == cookbookId && $0.syncStateRaw == "pending_create" }
        )
        return try modelContext.fetch(descriptor)
    }

    func deletePendingEntry(cookbookId: Int, date: String, recipeId: Int) throws {
        guard let modelContext else { throw MealPlanRepositoryError.notConfigured }

        let scopedId = "\(cookbookId)|\(date)|\(recipeId)"
        let descriptor = FetchDescriptor<PersistedMealPlanEntry>(
            predicate: #Predicate { $0.scopedId == scopedId }
        )
        if let entry = try modelContext.fetch(descriptor).first {
            modelContext.delete(entry)
            try modelContext.save()
        }
    }

    func clearAll() throws {
        guard let modelContext else { throw MealPlanRepositoryError.notConfigured }

        let dayDescriptor = FetchDescriptor<PersistedMealPlanDay>()
        for day in try modelContext.fetch(dayDescriptor) {
            modelContext.delete(day)
        }

        let entryDescriptor = FetchDescriptor<PersistedMealPlanEntry>()
        for entry in try modelContext.fetch(entryDescriptor) {
            modelContext.delete(entry)
        }

        try modelContext.save()
    }
}

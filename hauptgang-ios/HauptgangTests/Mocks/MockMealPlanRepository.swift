import Foundation
@testable import Hauptgang
import SwiftData

@MainActor
final class MockMealPlanRepository: MealPlanRepositoryProtocol {
    var configuredCalled = false
    var savedDays: [[MealPlanDay]] = []
    var entries: [String: [PersistedMealPlanEntry]] = [:]
    var pendingEntries: [PersistedMealPlanEntry] = []
    var addedLocalEntries: [(cookbookId: Int, date: String, recipeId: Int)] = []
    var deletedPendingEntries: [(cookbookId: Int, date: String, recipeId: Int)] = []

    var shouldThrowOnSave = false
    var shouldThrowOnGet = false

    func configure(modelContext _: ModelContext) {
        self.configuredCalled = true
    }

    func getEntries(cookbookId _: Int, date: String) throws -> [PersistedMealPlanEntry] {
        if self.shouldThrowOnGet {
            throw MockMealPlanRepoError.testError
        }
        return self.entries[date] ?? []
    }

    func saveDays(_ days: [MealPlanDay], cookbookId _: Int) throws {
        if self.shouldThrowOnSave {
            throw MockMealPlanRepoError.testError
        }
        self.savedDays.append(days)
    }

    func addLocalEntry(
        cookbookId: Int,
        date: String,
        recipeId: Int,
        recipeName _: String,
        recipeCoverImageUrl _: String?
    ) throws {
        if self.shouldThrowOnSave {
            throw MockMealPlanRepoError.testError
        }
        self.addedLocalEntries.append((cookbookId: cookbookId, date: date, recipeId: recipeId))
    }

    func getPendingEntries(cookbookId _: Int) throws -> [PersistedMealPlanEntry] {
        if self.shouldThrowOnGet {
            throw MockMealPlanRepoError.testError
        }
        return self.pendingEntries
    }

    func deletePendingEntry(cookbookId: Int, date: String, recipeId: Int) throws {
        self.deletedPendingEntries.append((cookbookId: cookbookId, date: date, recipeId: recipeId))
    }

    func clearAll() throws {
        self.entries = [:]
        self.pendingEntries = []
    }
}

enum MockMealPlanRepoError: Error {
    case testError
}

import Foundation
@testable import Hauptgang

final class MockMealPlanService: MealPlanServiceProtocol, @unchecked Sendable {
    var fetchResult: [MealPlanDay] = []
    var addEntryResult: MealPlanDay?
    var deleteEntryError: Error?
    var voteResult: MealPlanDay?
    var unvoteResult: MealPlanDay?
    var selectResult: MealPlanDay?
    var deselectResult: MealPlanDay?

    var fetchCallCount = 0
    var addEntryCallCount = 0
    var deleteEntryCallCount = 0
    var voteCallCount = 0
    var unvoteCallCount = 0
    var selectCallCount = 0
    var deselectCallCount = 0

    var lastDeletedEntryId: Int?
    var lastVotedEntryId: Int?
    var lastSelectedEntryId: Int?

    var shouldThrow = false
    var errorToThrow: Error = APIError.networkError(URLError(.notConnectedToInternet))

    func fetchMealPlans(cookbookId _: Int, from _: String, to _: String) async throws -> [MealPlanDay] {
        self.fetchCallCount += 1
        if self.shouldThrow {
            throw self.errorToThrow
        }
        return self.fetchResult
    }

    func addEntry(cookbookId _: Int, date _: String, recipeId _: Int) async throws -> MealPlanDay {
        self.addEntryCallCount += 1
        if self.shouldThrow {
            throw self.errorToThrow
        }
        guard let result = self.addEntryResult else { throw MockMealPlanError.notConfigured }
        return result
    }

    func deleteEntry(id: Int) async throws {
        self.deleteEntryCallCount += 1
        self.lastDeletedEntryId = id
        if let error = self.deleteEntryError {
            throw error
        }
        if self.shouldThrow {
            throw self.errorToThrow
        }
    }

    func vote(entryId: Int) async throws -> MealPlanDay {
        self.voteCallCount += 1
        self.lastVotedEntryId = entryId
        if self.shouldThrow {
            throw self.errorToThrow
        }
        guard let result = self.voteResult else { throw MockMealPlanError.notConfigured }
        return result
    }

    func unvote(entryId: Int) async throws -> MealPlanDay {
        self.unvoteCallCount += 1
        self.lastVotedEntryId = entryId
        if self.shouldThrow {
            throw self.errorToThrow
        }
        guard let result = self.unvoteResult else { throw MockMealPlanError.notConfigured }
        return result
    }

    func select(cookbookId _: Int, date _: String, entryId: Int) async throws -> MealPlanDay {
        self.selectCallCount += 1
        self.lastSelectedEntryId = entryId
        if self.shouldThrow {
            throw self.errorToThrow
        }
        guard let result = self.selectResult else { throw MockMealPlanError.notConfigured }
        return result
    }

    func deselect(cookbookId _: Int, date _: String) async throws -> MealPlanDay {
        self.deselectCallCount += 1
        if self.shouldThrow {
            throw self.errorToThrow
        }
        guard let result = self.deselectResult else { throw MockMealPlanError.notConfigured }
        return result
    }
}

enum MockMealPlanError: Error {
    case notConfigured
}

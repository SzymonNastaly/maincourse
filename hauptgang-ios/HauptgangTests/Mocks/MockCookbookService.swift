import Foundation
@testable import Hauptgang

final class MockCookbookService: CookbookServiceProtocol, @unchecked Sendable {
    var cookbooksToReturn: [Cookbook] = []
    var fetchCookbooksCallCount = 0
    var createCookbookResult: Cookbook?
    var createInvitationResult: CookbookInvitationResponse?
    var invitationPreviewResult: CookbookInvitationPreview?
    var acceptInvitationResult: CookbookInvitationAcceptResponse?
    var shouldThrowError = false
    var fetchHandler: (@Sendable () async throws -> [Cookbook])?

    func fetchCookbooks() async throws -> [Cookbook] {
        self.fetchCookbooksCallCount += 1
        if let fetchHandler {
            return try await fetchHandler()
        }
        if self.shouldThrowError {
            throw MockCookbookError.notConfigured
        }
        return self.cookbooksToReturn
    }

    func createCookbook(name _: String, movePersonalRecipes _: Bool) async throws -> Cookbook {
        guard let result = self.createCookbookResult else {
            throw MockCookbookError.notConfigured
        }
        return result
    }

    func deleteCookbook(id _: Int) async throws {}

    func leaveCookbook(id _: Int) async throws {}

    func createInvitation(cookbookId _: Int) async throws -> CookbookInvitationResponse {
        guard let result = self.createInvitationResult else {
            throw MockCookbookError.notConfigured
        }
        return result
    }

    func fetchInvitationPreview(token _: String) async throws -> CookbookInvitationPreview {
        guard let result = self.invitationPreviewResult else {
            throw MockCookbookError.notConfigured
        }
        return result
    }

    func acceptInvitation(token _: String) async throws -> CookbookInvitationAcceptResponse {
        guard let result = self.acceptInvitationResult else {
            throw MockCookbookError.notConfigured
        }
        return result
    }

    func rejectInvitation(token _: String) async throws {}
}

enum MockCookbookError: Error {
    case notConfigured
}

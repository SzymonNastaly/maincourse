import Foundation
@testable import Hauptgang
import Testing

@MainActor
struct OnboardingCoordinatorTests {
    private func defaults() -> UserDefaults {
        UserDefaults(suiteName: "OnboardingTests.\(UUID().uuidString)")!
    }

    @Test func keepSurvivesRelaunchAndRepeatedTapsReuseRequest() throws {
        let store = self.defaults()
        let coordinator = OnboardingCoordinator(defaults: store)
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        let first = try #require(coordinator.pending)
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        #expect(coordinator.pending == first)
        let restored = OnboardingCoordinator(defaults: store)
        #expect(restored.pending == first)
        #expect(first.userId == nil)
    }

    @Test func skipDoesNotInventAKeepIntent() {
        let coordinator = OnboardingCoordinator(defaults: self.defaults())
        coordinator.finishIntroduction()
        #expect(coordinator.pending == nil)
    }

    @Test func retryUsesBoundDestinationAndOriginalRequest() async throws {
        let service = SaveStub()
        await service.failNext()
        let coordinator = OnboardingCoordinator(service: service, defaults: self.defaults())
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        coordinator.sessionChanged(userId: 1)
        await coordinator.resume(userId: 1, personalCookbookId: 10)
        let pending = try #require(coordinator.pending)
        #expect(pending.userId == 1)
        #expect(pending.cookbookId == 10)
        #expect(coordinator.canRetry)
        await coordinator.resume(userId: 1, personalCookbookId: 999)
        let requests = await service.requests
        #expect(requests.count == 2)
        #expect(requests.allSatisfy { $0.cookbookId == 10 && $0.requestId == pending.requestId })
        #expect(coordinator.pending == nil)
        #expect(coordinator.takeSavedRecipe() == RecipeSaveResult(recipeId: 123, cookbookId: 10))
        #expect(coordinator.takeSavedRecipe() == nil)
    }

    @Test func logoutDiscardsLateResponseAndBoundIntent() async {
        let service = SaveStub()
        await service.holdResponse()
        let store = self.defaults()
        let coordinator = OnboardingCoordinator(service: service, defaults: store)
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        coordinator.sessionChanged(userId: 1)
        let task = Task { await coordinator.resume(userId: 1, personalCookbookId: 10) }
        await service.waitForRequest()
        coordinator.sessionChanged(userId: nil)
        await service.releaseResponse()
        await task.value
        #expect(coordinator.pending == nil)
        #expect(coordinator.takeSavedRecipe() == nil)
        #expect(OnboardingCoordinator(defaults: store).pending == nil)
    }

    @Test func differentAccountCannotInheritBoundIntent() async {
        let service = SaveStub()
        await service.failNext()
        let coordinator = OnboardingCoordinator(service: service, defaults: self.defaults())
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        coordinator.sessionChanged(userId: 1)
        await coordinator.resume(userId: 1, personalCookbookId: 10)
        coordinator.sessionChanged(userId: 2)
        await coordinator.resume(userId: 2, personalCookbookId: 20)
        #expect(await service.requests.count == 1)
        #expect(coordinator.pending == nil)
    }

    @Test func deletedSaveStopsRetryAndExplicitKeepCreatesNewRequest() async throws {
        let service = SaveStub()
        await service.failNext(with: .resourceGone)
        let coordinator = OnboardingCoordinator(service: service, defaults: self.defaults())
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        coordinator.sessionChanged(userId: 1)
        let oldId = try #require(coordinator.pending?.requestId)
        await coordinator.resume(userId: 1, personalCookbookId: 10)
        #expect(!coordinator.canRetry)
        #expect(coordinator.pending == nil)
        await coordinator.resume(userId: 1, personalCookbookId: 10)
        #expect(await service.requests.count == 1)
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        #expect(coordinator.pending?.requestId != oldId)
    }

    @Test func authenticatedKeepCannotTransferBeforeSaveStarts() async {
        let service = SaveStub()
        let coordinator = OnboardingCoordinator(service: service, defaults: self.defaults())
        coordinator.sessionChanged(userId: 1)
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        #expect(coordinator.pending?.userId == 1)
        coordinator.sessionChanged(userId: nil)
        await coordinator.resume(userId: 2, personalCookbookId: 20)
        #expect(await service.requests.isEmpty)
    }

    @Test func anonymousKeepBindsAtFirstAuthenticationBeforeStartup() {
        let coordinator = OnboardingCoordinator(defaults: self.defaults())
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        coordinator.sessionChanged(userId: 1)
        #expect(coordinator.pending?.userId == 1)
        coordinator.sessionChanged(userId: nil)
        #expect(coordinator.pending == nil)
    }

    @Test func staleRetryCannotChangeSessionOrDiscardNewAccountsIntent() async {
        let service = SaveStub()
        let coordinator = OnboardingCoordinator(service: service, defaults: self.defaults())
        coordinator.sessionChanged(userId: 2)
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        let secondAccountsIntent = coordinator.pending
        await coordinator.resume(userId: 1, personalCookbookId: 10)
        #expect(coordinator.pending == secondAccountsIntent)
        #expect(await service.requests.isEmpty)
    }

    @Test func terminalFailureDoesNotFollowUserIntoAnotherAccount() async {
        let service = SaveStub()
        await service.failNext(with: .resourceGone)
        let coordinator = OnboardingCoordinator(service: service, defaults: self.defaults())
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        coordinator.sessionChanged(userId: 1)
        await coordinator.resume(userId: 1, personalCookbookId: 10)
        #expect(coordinator.showsContinuation)
        coordinator.sessionChanged(userId: nil)
        coordinator.sessionChanged(userId: 2)
        #expect(!coordinator.showsContinuation)
    }

    @Test func missingCookbookCanRecoverWithoutGlobalStartupBlock() async {
        let coordinator = OnboardingCoordinator(service: SaveStub(), defaults: self.defaults())
        coordinator.keep(sampleKey: "tomato-orzo-v1")
        coordinator.sessionChanged(userId: 1)
        await coordinator.resume(userId: 1, personalCookbookId: nil)
        #expect(coordinator.canRetry)
        #expect(coordinator.pending != nil)
        await coordinator.resume(userId: 1, personalCookbookId: 10)
        #expect(coordinator.savedRecipe != nil)
    }
}

private actor SaveStub: RecipeSaving {
    struct Request: Sendable {
        let cookbookId: Int
        let requestId: UUID
    }

    private(set) var requests: [Request] = []
    private var error: APIError?
    private var held = false
    private var response: CheckedContinuation<Void, Never>?
    private var requestWaiter: CheckedContinuation<Void, Never>?

    func failNext(with error: APIError = .networkError(URLError(.notConnectedToInternet))) {
        self.error = error
    }

    func holdResponse() {
        self.held = true
    }

    func waitForRequest() async {
        if !self.requests.isEmpty {
            return
        }
        await withCheckedContinuation { self.requestWaiter = $0 }
    }

    func releaseResponse() {
        self.response?.resume()
        self.response = nil
    }

    func save(source _: RecipeSaveSource, toCookbookId: Int, requestId: UUID) async throws -> RecipeSaveResult {
        self.requests.append(Request(cookbookId: toCookbookId, requestId: requestId))
        self.requestWaiter?.resume()
        self.requestWaiter = nil
        if self.held {
            await withCheckedContinuation { self.response = $0 }
        }
        if let error = self.error {
            self.error = nil
            throw error
        }
        return RecipeSaveResult(recipeId: 123, cookbookId: toCookbookId)
    }
}

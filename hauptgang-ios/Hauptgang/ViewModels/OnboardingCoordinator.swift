import Foundation
import Observation

struct PendingStarterRecipe: Codable, Equatable, Sendable {
    let sampleKey: String
    let requestId: UUID
    var userId: Int?
    var cookbookId: Int?
}

/// Owns only the explicit save intent. AuthenticatedSessionViewModel still owns startup.
@MainActor @Observable
final class OnboardingCoordinator {
    enum SaveState: Equatable {
        case idle
        case saving
        case failed(message: String, canRetry: Bool)
    }

    static let pendingDefaultsKey = "hauptgang.onboarding.pendingRecipe"
    static let dismissedUsersDefaultsKey = "hauptgang.onboarding.dismissedExampleUsers"
    static let completedExampleDefaultsKey = "hauptgang.onboarding.completedExampleAwaitingUser"

    private(set) var pending: PendingStarterRecipe?
    private(set) var state: SaveState = .idle
    private(set) var savedRecipe: RecipeSaveResult?
    var isDemoPresented = false
    var externalNavigationHasPriority = false
    private(set) var dismissedUsers: Set<Int>

    private let service: any RecipeSaving
    private let defaults: UserDefaults
    private var currentUserId: Int?
    private var completedExampleAwaitingUser: Bool
    private var generation = 0

    init(service: any RecipeSaving = RecipeSaveService(), defaults: UserDefaults = .standard) {
        self.service = service
        self.defaults = defaults
        self.pending = defaults.data(forKey: Self.pendingDefaultsKey)
            .flatMap { try? JSONDecoder().decode(PendingStarterRecipe.self, from: $0) }
        self.dismissedUsers = Set(defaults.array(forKey: Self.dismissedUsersDefaultsKey) as? [Int] ?? [])
        self.completedExampleAwaitingUser = defaults.bool(forKey: Self.completedExampleDefaultsKey)
    }

    var canRetry: Bool {
        if case let .failed(_, canRetry) = self.state {
            return canRetry
        }
        return false
    }

    var showsContinuation: Bool {
        if case .failed = self.state {
            return true
        }
        return self.pending != nil
    }

    func keep(sampleKey: String) {
        if self.pending == nil {
            self.savedRecipe = nil
            self.externalNavigationHasPriority = false
            self.generation += 1
            self.pending = PendingStarterRecipe(sampleKey: sampleKey, requestId: UUID(), userId: self.currentUserId)
            self.persist()
        }
        self.state = .idle
        self.defaults.set(Date().timeIntervalSince1970, forKey: OnboardingService.authStepReachedAtDefaultsKey)
    }

    func finishIntroduction() {
        self.defaults.set(Date().timeIntervalSince1970, forKey: OnboardingService.completedAtDefaultsKey)
        self.defaults.removeObject(forKey: OnboardingService.authStepReachedAtDefaultsKey)
    }

    func dismissExample(for userId: Int) {
        self.dismissedUsers.insert(userId)
        self.defaults.set(Array(self.dismissedUsers), forKey: Self.dismissedUsersDefaultsKey)
    }

    func completeExample() {
        if let currentUserId {
            self.dismissExample(for: currentUserId)
            return
        }
        self.completedExampleAwaitingUser = true
        self.defaults.set(true, forKey: Self.completedExampleDefaultsKey)
    }

    func sessionChanged(userId: Int?) {
        if let boundUser = self.pending?.userId, boundUser != userId {
            self.continueWithoutSaving()
        }
        if self.currentUserId != userId {
            self.generation += 1
            self.savedRecipe = nil
            self.isDemoPresented = false
            self.externalNavigationHasPriority = false
            if self.pending == nil {
                self.state = .idle
            }
        }
        self.currentUserId = userId
        if let userId, self.completedExampleAwaitingUser {
            self.dismissExample(for: userId)
            self.completedExampleAwaitingUser = false
            self.defaults.removeObject(forKey: Self.completedExampleDefaultsKey)
        }
        if let userId, self.pending?.userId == nil, self.pending != nil {
            self.pending?.userId = userId
            self.persist()
        }
    }

    func resume(userId: Int, personalCookbookId: Int?) async {
        guard self.currentUserId == userId, !Task.isCancelled,
              var intent = self.pending, intent.userId == userId, self.state != .saving else { return }
        intent.userId = userId
        intent.cookbookId = intent.cookbookId ?? personalCookbookId
        self.pending = intent
        self.persist()
        guard let cookbookId = intent.cookbookId else {
            self.state = .failed(message: "We couldn’t reach My Recipes. Your preview is still here.", canRetry: true)
            return
        }
        self.state = .saving
        let generation = self.generation
        defer {
            if self.isCurrent(intent, generation: generation), self.state == .saving, Task.isCancelled {
                self.state = .failed(message: "Saving was interrupted. You can safely try again.", canRetry: true)
            }
        }
        do {
            let result = try await self.service.save(
                source: RecipeSaveSource(type: "sample", key: intent.sampleKey),
                toCookbookId: cookbookId, requestId: intent.requestId
            )
            guard self.isCurrent(intent, generation: generation), !Task.isCancelled else { return }
            self.savedRecipe = result
            self.dismissExample(for: userId)
            self.pending = nil
            self.persist()
            self.state = .idle
        } catch {
            guard self.isCurrent(intent, generation: generation), !Task.isCancelled else { return }
            let terminal = switch error as? APIError {
            case .resourceGone, .requestConflict: true
            default: false
            }
            if terminal {
                self.pending = nil
                self.persist()
            }
            self.state = .failed(message: error.localizedDescription, canRetry: !terminal)
        }
    }

    func continueWithoutSaving() {
        self.generation += 1
        self.pending = nil
        self.persist()
        self.state = .idle
    }

    func takeSavedRecipe() -> RecipeSaveResult? {
        defer { self.savedRecipe = nil }
        return self.savedRecipe
    }

    private func isCurrent(_ intent: PendingStarterRecipe, generation: Int) -> Bool {
        self.generation == generation && self.pending == intent && self.currentUserId == intent.userId
    }

    private func persist() {
        if let pending = self.pending, let data = try? JSONEncoder().encode(pending) {
            self.defaults.set(data, forKey: Self.pendingDefaultsKey)
        } else {
            self.defaults.removeObject(forKey: Self.pendingDefaultsKey)
        }
    }
}

import Foundation
import os

@MainActor @Observable
final class CookbookViewModel {
    private(set) var cookbooks: [Cookbook] = []
    private(set) var activeCookbook: Cookbook?
    private(set) var isLoading = false
    var error: String?

    private var currentUserId: Int?
    private let service: CookbookServiceProtocol
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "CookbookViewModel")

    init(service: CookbookServiceProtocol = CookbookService.shared) {
        self.service = service
    }

    // MARK: - Configuration

    /// Set the current user's ID so ownership checks work correctly
    func configure(userId: Int) {
        self.currentUserId = userId
    }

    // MARK: - Lifecycle

    /// Fetch cookbooks and set active cookbook on login
    func loadCookbooks() async {
        self.isLoading = true
        defer { self.isLoading = false }

        do {
            let cookbooks = try await service.fetchCookbooks()
            await self.applyAndCache(cookbooks)
        } catch {
            self.logger.error("Failed to load cookbooks: \(error.localizedDescription)")

            // Fall back to cached cookbooks for offline launch
            if let data = await CookbookContext.shared.loadCachedCookbooksData(),
               let cached = try? JSONDecoder().decode([Cookbook].self, from: data), !cached.isEmpty {
                self.logger.info("Using \(cached.count) cached cookbooks (offline)")
                await self.applyAndCache(cached)
            } else {
                self.error = error.localizedDescription
            }
        }
    }

    /// Apply a cookbook list to the view model state and persist for offline use
    private func applyAndCache(_ cookbooks: [Cookbook]) async {
        self.cookbooks = cookbooks

        // If we have a saved selection that's still valid, keep it
        let currentId = await CookbookContext.shared.getActiveCookbookId()
        if let currentId, let match = cookbooks.first(where: { $0.id == currentId }) {
            self.activeCookbook = match
        } else {
            // Default: prefer shared cookbook, fall back to personal
            let defaultCookbook = cookbooks.first(where: { !$0.personal }) ?? cookbooks
                .first(where: { $0.personal })
            self.activeCookbook = defaultCookbook
            await CookbookContext.shared.setActiveCookbookId(defaultCookbook?.id)
        }

        if let data = try? JSONEncoder().encode(cookbooks) {
            await CookbookContext.shared.saveCookbooksData(data)
        }
        self.logger.info("Loaded \(cookbooks.count) cookbooks, active: \(self.activeCookbook?.name ?? "none")")
    }

    /// Refresh cookbooks list without changing active selection
    func refresh() async {
        let requestedUserId = self.currentUserId
        do {
            let cookbooks = try await service.fetchCookbooks()
            guard !Task.isCancelled, self.currentUserId == requestedUserId else { return }
            self.cookbooks = cookbooks

            if let activeId = self.activeCookbook?.id {
                // Update active cookbook data (e.g., recipeCount) if still valid
                self.activeCookbook = cookbooks.first(where: { $0.id == activeId })
            } else {
                // No active cookbook (e.g., initial load failed while offline) — select default
                let defaultCookbook = cookbooks.first(where: { !$0.personal }) ?? cookbooks
                    .first(where: { $0.personal })
                self.activeCookbook = defaultCookbook
                await CookbookContext.shared.setActiveCookbookId(defaultCookbook?.id)
            }
        } catch {
            self.logger.error("Failed to refresh cookbooks: \(error.localizedDescription)")
        }
    }

    // MARK: - Cookbook Switching

    /// Switch the active cookbook. Returns true if the cookbook actually changed.
    @discardableResult
    func setActiveCookbook(_ cookbook: Cookbook) async -> Bool {
        guard cookbook.id != self.activeCookbook?.id else { return false }

        self.activeCookbook = cookbook
        await CookbookContext.shared.setActiveCookbookId(cookbook.id)
        self.logger.info("Switched to cookbook: \(cookbook.name) (id: \(cookbook.id))")
        return true
    }

    // MARK: - Cookbook Management

    func createSharedCookbook(name: String, moveRecipes: Bool) async throws -> Cookbook {
        let cookbook = try await service.createCookbook(name: name, movePersonalRecipes: moveRecipes)
        await self.loadCookbooks()
        return cookbook
    }

    func leaveSharedCookbook() async throws {
        guard let shared = cookbooks.first(where: { !$0.personal }) else { return }
        try await self.service.leaveCookbook(id: shared.id)

        // Reset to personal cookbook
        if let personal = cookbooks.first(where: { $0.personal }) {
            await self.setActiveCookbook(personal)
        }
        await self.loadCookbooks()
    }

    func deleteSharedCookbook() async throws {
        guard let shared = cookbooks.first(where: { !$0.personal }) else { return }
        try await self.service.deleteCookbook(id: shared.id)

        // Reset to personal cookbook
        if let personal = cookbooks.first(where: { $0.personal }) {
            await self.setActiveCookbook(personal)
        }
        await self.loadCookbooks()
    }

    func createInvitation() async throws -> CookbookInvitationResponse {
        guard let shared = cookbooks.first(where: { !$0.personal }) else {
            throw CookbookError.noSharedCookbook
        }
        return try await self.service.createInvitation(cookbookId: shared.id)
    }

    // MARK: - 403 Recovery

    /// Called when a 403 is received on a cookbook-scoped request.
    /// Re-fetches cookbooks and resets to personal.
    func handleForbidden() async {
        self.logger.warning("403 received, re-fetching cookbooks and resetting to personal")
        do {
            let cookbooks = try await service.fetchCookbooks()
            self.cookbooks = cookbooks
            if let personal = cookbooks.first(where: { $0.personal }) {
                await self.setActiveCookbook(personal)
            }
        } catch {
            self.logger.error("Failed to recover from 403: \(error.localizedDescription)")
        }
    }

    // MARK: - Reset

    func reset() async {
        self.cookbooks = []
        self.activeCookbook = nil
        self.currentUserId = nil
        self.error = nil
        await CookbookContext.shared.reset()
    }

    /// Whether the user has a shared cookbook
    var hasSharedCookbook: Bool {
        self.cookbooks.contains { !$0.personal }
    }

    /// The shared cookbook, if any
    var sharedCookbook: Cookbook? {
        self.cookbooks.first { !$0.personal }
    }

    /// The personal cookbook
    var personalCookbook: Cookbook? {
        self.cookbooks.first { $0.personal }
    }

    /// Whether the current user owns the shared cookbook
    var isSharedCookbookOwner: Bool {
        guard let shared = self.sharedCookbook,
              let userId = self.currentUserId else { return false }
        return shared.members.contains { $0.id == userId && $0.role == "owner" }
    }
}

// MARK: - Errors

enum CookbookError: LocalizedError {
    case noSharedCookbook

    var errorDescription: String? {
        switch self {
        case .noSharedCookbook:
            "No shared cookbook found"
        }
    }
}

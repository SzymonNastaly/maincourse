import Foundation
import os
import SwiftData

/// Coordinates authenticated app startup and cookbook switching.
///
/// Owns the explicit ordering:
/// 1. user authenticated
/// 2. CookbookContext configured
/// 3. cookbooks loaded
/// 4. active cookbook resolved
/// 5. recipe view model configured for that cookbook
/// 6. initial recipe content attempted
/// 7. startup splash dismissed (driven by ``canDismissStartupSplash``)
@MainActor
@Observable
final class AuthenticatedSessionViewModel {
    enum StartupState: Equatable {
        case idle
        case loading(userId: Int)
        case ready(userId: Int, cookbookId: Int)
        case failed(userId: Int, message: String)
    }

    private(set) var startupState: StartupState = .idle
    private(set) var currentUser: User?

    let cookbookViewModel: CookbookViewModel
    let recipeViewModel: RecipeViewModel
    let shoppingListViewModel: ShoppingListViewModel

    private var startupTask: Task<Void, Never>?
    private var cookbookSwitchTask: Task<Void, Never>?

    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "AuthenticatedSession")

    init(
        cookbookViewModel: CookbookViewModel = CookbookViewModel(),
        recipeViewModel: RecipeViewModel = RecipeViewModel(),
        shoppingListViewModel: ShoppingListViewModel = ShoppingListViewModel()
    ) {
        self.cookbookViewModel = cookbookViewModel
        self.recipeViewModel = recipeViewModel
        self.shoppingListViewModel = shoppingListViewModel
    }

    /// Whether the startup splash overlay can dismiss. Both `.ready` and `.failed` end states
    /// dismiss the splash so the UI can show error/degraded content rather than hang.
    var canDismissStartupSplash: Bool {
        switch self.startupState {
        case .ready, .failed:
            true
        case .idle, .loading:
            false
        }
    }

    // MARK: - Startup

    /// Starts the authenticated session for the given user. Cancels any in-flight startup.
    func start(user: User, modelContext: ModelContext) async {
        self.startupTask?.cancel()

        let task = Task { @MainActor in
            await self.performStart(user: user, modelContext: modelContext)
        }

        self.startupTask = task
        await task.value

        if self.startupTask == task {
            self.startupTask = nil
        }
    }

    private func performStart(user: User, modelContext: ModelContext) async {
        self.logger.info("Starting authenticated session for user \(user.id)")
        self.currentUser = user
        self.startupState = .loading(userId: user.id)

        await CookbookContext.shared.configure(userId: user.id)
        guard !Task.isCancelled else { return }

        self.cookbookViewModel.configure(userId: user.id)
        await self.cookbookViewModel.loadCookbooks()
        guard !Task.isCancelled else { return }

        // Retry must be able to persist recipes even when the first cookbook request fails.
        self.recipeViewModel.configure(modelContext: modelContext)

        guard let cookbookId = self.cookbookViewModel.activeCookbook?.id else {
            self.logger.warning("No active cookbook resolved after loadCookbooks; failing startup")
            self.startupState = .failed(
                userId: user.id,
                message: self.cookbookViewModel.error ?? "No cookbook available"
            )
            return
        }

        await self.recipeViewModel.configureSearchIndex(userId: user.id, cookbookId: cookbookId)
        guard !Task.isCancelled else { return }

        await self.recipeViewModel.refreshRecipes()
        guard !Task.isCancelled else { return }

        // Make sure we don't override a fresher state written by a newer startup.
        guard case let .loading(loadingUserId) = self.startupState, loadingUserId == user.id else {
            return
        }

        self.startupState = .ready(userId: user.id, cookbookId: cookbookId)
        self.logger.info("Authenticated session ready for user \(user.id), cookbook \(cookbookId)")
    }

    // MARK: - Cookbook Switching

    /// Switch the active cookbook through the session so recipe state is reset and reloaded
    /// in lockstep. Cancels any previous in-flight switch.
    func switchCookbook(_ cookbook: Cookbook) async {
        guard let user = self.currentUser else { return }

        self.cookbookSwitchTask?.cancel()

        let task = Task { @MainActor in
            await self.performCookbookSwitch(cookbook, user: user)
        }

        self.cookbookSwitchTask = task
        await task.value

        if self.cookbookSwitchTask == task {
            self.cookbookSwitchTask = nil
        }
    }

    private func performCookbookSwitch(_ cookbook: Cookbook, user: User) async {
        let changed = await self.cookbookViewModel.setActiveCookbook(cookbook)
        guard changed, !Task.isCancelled else { return }

        self.logger.info("Switching to cookbook \(cookbook.id) for user \(user.id)")
        self.recipeViewModel.resetForCookbookSwitch()
        await self.recipeViewModel.configureSearchIndex(userId: user.id, cookbookId: cookbook.id)
        guard !Task.isCancelled else { return }

        await self.recipeViewModel.refreshRecipes()
        guard !Task.isCancelled else { return }

        self.startupState = .ready(userId: user.id, cookbookId: cookbook.id)
    }

    // MARK: - Foreground Refresh & 403 Recovery

    /// Refresh cookbooks and the active cookbook's recipes (e.g. on returning to foreground).
    func refreshActiveCookbook() async {
        guard let user = self.currentUser else { return }

        await self.cookbookViewModel.refresh()
        guard !Task.isCancelled else { return }

        guard let cookbookId = self.cookbookViewModel.activeCookbook?.id else {
            self.startupState = .failed(userId: user.id, message: "No cookbook available")
            return
        }

        await self.recipeViewModel.configureSearchIndex(userId: user.id, cookbookId: cookbookId)
        await self.recipeViewModel.refreshRecipes()

        if self.recipeViewModel.hasResolvedContent(for: cookbookId) {
            self.startupState = .ready(userId: user.id, cookbookId: cookbookId)
        }
    }

    /// Handle a 403 by re-fetching cookbooks (resetting to personal) and reloading recipes.
    func handleForbidden() async {
        guard let user = self.currentUser else { return }

        await self.cookbookViewModel.handleForbidden()
        guard !Task.isCancelled else { return }

        guard let cookbookId = self.cookbookViewModel.activeCookbook?.id else {
            self.startupState = .failed(userId: user.id, message: "No cookbook available")
            return
        }

        self.recipeViewModel.resetForCookbookSwitch()
        await self.recipeViewModel.configureSearchIndex(userId: user.id, cookbookId: cookbookId)
        await self.recipeViewModel.refreshRecipes()

        self.startupState = .ready(userId: user.id, cookbookId: cookbookId)
    }

    // MARK: - Reset

    /// Reset all session state on logout/account switch. Awaits child VM cleanup so that a
    /// subsequent login cannot race a not-yet-finished search-index reset.
    func reset() async {
        self.logger.info("Resetting authenticated session")

        self.startupTask?.cancel()
        self.startupTask = nil
        self.cookbookSwitchTask?.cancel()
        self.cookbookSwitchTask = nil

        self.currentUser = nil
        self.startupState = .idle

        await self.cookbookViewModel.reset()
        await self.recipeViewModel.clearData()
    }
}

import Foundation
import os
import SwiftData

/// Manages state for the recipe detail view
@MainActor @Observable
final class RecipeDetailViewModel {
    private(set) var recipe: RecipeDetail?
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    private let recipeService: RecipeServiceProtocol
    private let repository: RecipeRepositoryProtocol
    private let viewTracker: RecipeViewTracker
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "RecipeDetailViewModel")
    private var currentLoadID: UUID?

    init(
        recipeService: RecipeServiceProtocol = RecipeService.shared,
        repository: RecipeRepositoryProtocol? = nil,
        viewTracker: RecipeViewTracker = .shared
    ) {
        self.recipeService = recipeService
        self.repository = repository ?? RecipeRepository()
        self.viewTracker = viewTracker
    }

    /// Configure the repository with a model context
    func configure(modelContext: ModelContext) {
        self.repository.configure(modelContext: modelContext)
    }

    /// Load recipe details with cache-aside pattern
    func loadRecipe(id: Int) async {
        let loadID = UUID()
        self.currentLoadID = loadID

        self.logger.info("Loading recipe detail for id: \(id)")
        self.errorMessage = nil

        self.loadCachedRecipe(id: id)

        self.isLoading = (self.recipe == nil)

        defer {
            isLoading = false
        }

        do {
            let apiRecipe = try await recipeService.fetchRecipeDetail(id: id)

            guard self.currentLoadID == loadID else {
                self.logger.debug("Ignoring stale response for recipe id: \(id)")
                return
            }

            self.recipe = apiRecipe
            self.logger.info("Successfully loaded recipe from API: \(apiRecipe.name)")

            // record() is a cheap, non-throwing in-memory + UserDefaults write on the
            // tracker actor — awaited directly rather than detached into an unstructured
            // Task, so it deterministically lands before this call returns without
            // meaningfully delaying the screen the user is looking at.
            await self.viewTracker.record(recipeId: id)

            do {
                try self.repository.saveRecipeDetail(apiRecipe)
            } catch {
                self.logger.error("Failed to persist recipe detail: \(error.localizedDescription)")
            }
        } catch is CancellationError {
            self.logger.debug("Recipe load cancelled for id: \(id)")
            return
        } catch {
            guard self.currentLoadID == loadID else { return }

            self.logger.error("Failed to load recipe detail: \(error.localizedDescription)")
            if self.recipe != nil {
                self.logger.info("Showing cached recipe after fetch failure")
            } else {
                self.errorMessage = "Failed to load recipe. Tap to retry."
            }
        }
    }

    /// Load cached recipe from local storage
    private func loadCachedRecipe(id: Int) {
        do {
            if let persisted = try repository.getRecipe(id: id) {
                let cached = persisted.toRecipeDetail()
                self.recipe = cached
                self.logger.info("Loaded cached recipe: \(cached.name)")
            }
        } catch {
            self.logger.error("Failed to load cached recipe: \(error.localizedDescription)")
        }
    }
}

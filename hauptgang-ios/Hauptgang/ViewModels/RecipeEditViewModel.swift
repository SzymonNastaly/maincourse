import Foundation
import os
import PhotosUI
import SwiftData
import SwiftUI

/// Manages form state for editing a recipe
@MainActor @Observable
final class RecipeEditViewModel {
    // MARK: - Form Fields

    var name: String = ""
    var prepTime: String = ""
    var cookTime: String = ""
    var servings: String = ""
    var ingredients: [String] = []
    var instructions: [String] = []
    var notes: String = ""
    var sourceUrl: String = ""

    // MARK: - Cover Image

    var selectedPhoto: PhotosPickerItem?
    var coverImageData: Data?
    var coverImageMimeType: String = "image/jpeg"
    var hasCoverImageChange: Bool {
        self.coverImageData != nil
    }

    // MARK: - State

    private(set) var isSaving = false
    var errorMessage: String?
    private(set) var didSave = false

    // MARK: - Validation

    var nameError: String? {
        let trimmed = self.name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return "Name is required"
        }
        return nil
    }

    var isValid: Bool {
        self.nameError == nil
    }

    // MARK: - Private

    private var originalRecipe: RecipeDetail?
    private let recipeService: RecipeServiceProtocol
    private let repository: RecipeRepositoryProtocol
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "RecipeEditViewModel")

    init(
        recipeService: RecipeServiceProtocol = RecipeService.shared,
        repository: RecipeRepositoryProtocol? = nil
    ) {
        self.recipeService = recipeService
        self.repository = repository ?? RecipeRepository()
    }

    /// Configure the repository with a model context
    func configure(modelContext: ModelContext) {
        self.repository.configure(modelContext: modelContext)
    }

    /// Populate form fields from an existing recipe
    func populate(from recipe: RecipeDetail) {
        self.originalRecipe = recipe
        self.name = recipe.name
        self.prepTime = recipe.prepTime.map { String($0) } ?? ""
        self.cookTime = recipe.cookTime.map { String($0) } ?? ""
        self.servings = recipe.servings.map { String($0) } ?? ""
        // Use resolvedIngredients so structured rows fall back to `raw` when
        // `name` alone would lose meaningful data. v1 still edits raw strings;
        // structured editing arrives in v2.
        let rawList = recipe.resolvedIngredients.map(\.raw)
        self.ingredients = rawList.isEmpty ? [""] : rawList
        self.instructions = recipe.instructions.isEmpty ? [""] : recipe.instructions
        self.notes = recipe.notes ?? ""
        self.sourceUrl = recipe.sourceUrl ?? ""
    }

    // MARK: - Ingredient Management

    func addIngredient() {
        self.ingredients.append("")
    }

    func removeIngredient(at index: Int) {
        guard self.ingredients.indices.contains(index) else { return }
        self.ingredients.remove(at: index)
        if self.ingredients.isEmpty {
            self.ingredients.append("")
        }
    }

    func moveIngredient(from source: IndexSet, to destination: Int) {
        self.ingredients.move(fromOffsets: source, toOffset: destination)
    }

    // MARK: - Instruction Management

    func addInstruction() {
        self.instructions.append("")
    }

    func removeInstruction(at index: Int) {
        guard self.instructions.indices.contains(index) else { return }
        self.instructions.remove(at: index)
        if self.instructions.isEmpty {
            self.instructions.append("")
        }
    }

    func moveInstruction(from source: IndexSet, to destination: Int) {
        self.instructions.move(fromOffsets: source, toOffset: destination)
    }

    // MARK: - Photo Loading

    func loadPhoto() async {
        guard let item = self.selectedPhoto else { return }

        do {
            if let data = try await item.loadTransferable(type: Data.self) {
                self.coverImageData = data
                let type = item.supportedContentTypes.first
                self.coverImageMimeType = type?.preferredMIMEType ?? "image/jpeg"
            }
        } catch {
            self.logger.error("Failed to load photo: \(error.localizedDescription)")
        }
    }

    // MARK: - Save

    func save() async {
        guard let recipe = self.originalRecipe, self.isValid else { return }

        self.isSaving = true
        self.errorMessage = nil

        defer { self.isSaving = false }

        do {
            let params = self.buildUpdateParams()
            var updatedRecipe = try await recipeService.updateRecipe(id: recipe.id, params: params)

            if let imageData = self.coverImageData {
                updatedRecipe = try await self.recipeService.updateRecipeCoverImage(
                    id: recipe.id,
                    imageData: imageData,
                    mimeType: self.coverImageMimeType
                )
            }

            do {
                try self.repository.saveRecipeDetail(updatedRecipe)
            } catch {
                self.logger.error("Failed to persist updated recipe: \(error.localizedDescription)")
            }

            self.didSave = true
            self.logger.info("Successfully saved recipe \(recipe.id)")
        } catch {
            self.logger.error("Failed to save recipe: \(error.localizedDescription)")
            self.errorMessage = "Failed to save changes. Please try again."
        }
    }

    // MARK: - Private

    private func buildUpdateParams() -> RecipeUpdateParams {
        let trimmedName = self.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let filteredIngredients = self.ingredients
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let filteredInstructions = self.instructions
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let trimmedNotes = self.notes.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedSourceUrl = self.sourceUrl.trimmingCharacters(in: .whitespacesAndNewlines)

        return RecipeUpdateParams(
            name: trimmedName,
            prepTime: Int(self.prepTime),
            cookTime: Int(self.cookTime),
            servings: Int(self.servings),
            ingredients: filteredIngredients,
            instructions: filteredInstructions,
            notes: trimmedNotes.isEmpty ? nil : trimmedNotes,
            sourceUrl: trimmedSourceUrl.isEmpty ? nil : trimmedSourceUrl
        )
    }
}

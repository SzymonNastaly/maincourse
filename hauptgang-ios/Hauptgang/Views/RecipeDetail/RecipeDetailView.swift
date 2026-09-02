import os
import SwiftUI

private let logger = Logger(subsystem: "app.hauptgang.ios", category: "RecipeDetailView")

struct RecipeDetailView: View {
    @Environment(\.modelContext) private var modelContext

    let recipeId: Int

    @State private var viewModel: RecipeDetailViewModel
    @State private var shoppingListViewModel = ShoppingListViewModel()
    @State private var shoppingListReviewDraft: ShoppingListReviewDraft?
    @State private var isCookingMode = false
    @State private var showEditSheet = false
    @State private var currentServings: Int?

    private let heroImageHeight: CGFloat = 280

    private var isIOS26: Bool {
        if #available(iOS 26, *) {
            return true
        }
        return false
    }

    private var showsLoadingState: Bool {
        self.viewModel.isLoading && self.viewModel.recipe == nil
    }

    private var errorMessageForDisplay: String? {
        guard !self.showsLoadingState, self.viewModel.recipe == nil else {
            return nil
        }

        return self.viewModel.errorMessage
    }

    init(recipeId: Int, viewModel: RecipeDetailViewModel? = nil) {
        self.recipeId = recipeId
        self._viewModel = State(initialValue: viewModel ?? RecipeDetailViewModel())
    }

    var body: some View {
        ZStack {
            if let recipe = self.viewModel.recipe {
                RecipeDetailContentView(
                    recipe: recipe,
                    heroImageHeight: self.heroImageHeight,
                    isIOS26: self.isIOS26,
                    isCookingMode: self.isCookingMode,
                    onToggleCookingMode: self.toggleCookingMode,
                    currentServings: self.$currentServings
                )
            }

            if self.showsLoadingState {
                RecipeDetailLoadingView()
            }

            if let errorMessage = self.errorMessageForDisplay {
                RecipeDetailErrorView(message: errorMessage, onRetry: self.retryLoad)
            }
        }
        .background(Color.mcCanvas)
        .navigationBarTitleDisplayMode(.inline)
        .modifier(NavigationBarBackgroundModifier())
        .toolbar {
            if let recipe = self.viewModel.recipe {
                RecipeDetailToolbarContent(
                    hasIngredients: !recipe.resolvedIngredients.isEmpty,
                    onAddToShoppingList: {
                        self.presentShoppingListReview(
                            for: recipe.resolvedIngredients,
                            scale: self.scale(forBaseServings: recipe.servings)
                        )
                    },
                    onEdit: self.showEditRecipe
                )
            }
        }
        .sheet(isPresented: self.$showEditSheet) {
            if let recipe = self.viewModel.recipe {
                RecipeEditView(recipe: recipe, onSave: self.reloadRecipeAfterEdit)
            }
        }
        .sheet(item: self.$shoppingListReviewDraft) { draft in
            ShoppingListReviewSheet(
                recipeId: draft.recipeId,
                initialItems: draft.items,
                shoppingListViewModel: self.shoppingListViewModel
            )
        }
        .task(id: self.recipeId) {
            await self.loadRecipeTask()
        }
        .onDisappear {
            self.resetCookingModeIfNeeded()
        }
    }

    private func loadRecipeTask() async {
        logger.info("RecipeDetailView appeared for recipe id: \(self.recipeId)")
        self.viewModel.configure(modelContext: self.modelContext)
        self.shoppingListViewModel.configure(modelContext: self.modelContext)
        await self.viewModel.loadRecipe(id: self.recipeId)
    }

    private func retryLoad() {
        Task {
            await self.viewModel.loadRecipe(id: self.recipeId)
        }
    }

    private func reloadRecipeAfterEdit() {
        Task {
            await self.viewModel.loadRecipe(id: self.recipeId)
        }
    }

    private func showEditRecipe() {
        self.showEditSheet = true
    }

    private func toggleCookingMode() {
        withAnimation(.smooth(duration: 0.4)) {
            self.isCookingMode.toggle()
        }
        UIApplication.shared.isIdleTimerDisabled = self.isCookingMode
    }

    private func resetCookingModeIfNeeded() {
        guard self.isCookingMode else {
            return
        }

        self.isCookingMode = false
        UIApplication.shared.isIdleTimerDisabled = false
    }

    private func scale(forBaseServings baseServings: Int?) -> Decimal {
        guard let base = baseServings, base > 0 else { return 1 }
        let effective = self.currentServings ?? base
        return Decimal(effective) / Decimal(base)
    }

    private func presentShoppingListReview(for ingredients: [StructuredIngredient], scale: Decimal) {
        let draftItems: [ShoppingListDraftItem] = ingredients.compactMap { ingredient in
            let split = self.shoppingListSplit(for: ingredient, scale: scale)
            let name = split.name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty else { return nil }
            return ShoppingListDraftItem(name: name, details: split.details)
        }

        guard !draftItems.isEmpty else {
            return
        }

        self.shoppingListReviewDraft = ShoppingListReviewDraft(recipeId: self.recipeId, items: draftItems)
    }

    /// Split a structured ingredient into a (name, details) pair for the
    /// shopping list. Parsed rows put `ingredient.name` on the first line and
    /// the formatted quantity (+ optional note) on the second. Unparsed rows
    /// fall back to the raw string with no details.
    private func shoppingListSplit(
        for ingredient: StructuredIngredient,
        scale: Decimal
    ) -> (name: String, details: String?) {
        guard ingredient.hasStructuredFields else {
            return (ingredient.raw, nil)
        }

        let rawQuantity = IngredientFormatter.formatQuantity(
            amount: ingredient.amount,
            amountMax: ingredient.amountMax,
            unit: ingredient.unit,
            scale: scale
        )
        let quantity = rawQuantity.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = (ingredient.name ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let note = ingredient.note?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        let detailParts = [quantity, note].filter { !$0.isEmpty }
        let details = detailParts.isEmpty ? nil : detailParts.joined(separator: ", ")

        if name.isEmpty {
            return (ingredient.raw, details)
        }
        return (name, details)
    }
}

private struct RecipeDetailLoadingView: View {
    var body: some View {
        VStack(spacing: Theme.Spacing.md) {
            ProgressView()
                .scaleEffect(1.2)
                .tint(.mcAccent)

            Text("Loading recipe...")
                .font(.subheadline)
                .foregroundColor(.mcBody)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct RecipeDetailErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: Theme.Spacing.lg) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundColor(.mcDanger)

            Text(self.message)
                .font(.subheadline)
                .foregroundColor(.mcBody)
                .multilineTextAlignment(.center)

            Button(action: self.onRetry) {
                Label("Retry", systemImage: "arrow.clockwise")
                    .font(.subheadline)
            }
            .buttonStyle(.bordered)
            .tint(.mcAccent)
        }
        .padding(Theme.Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct NavigationBarBackgroundModifier: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content
        } else {
            content.toolbarBackground(.visible, for: .navigationBar)
        }
    }
}

#Preview("With data") {
    NavigationStack {
        RecipeDetailView(recipeId: 1)
    }
}

#Preview("Loading") {
    NavigationStack {
        RecipeDetailView(recipeId: 999)
    }
}

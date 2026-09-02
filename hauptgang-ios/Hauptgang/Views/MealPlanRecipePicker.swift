import SwiftData
import SwiftUI

struct MealPlanRecipePicker: View {
    let cookbookId: Int
    let dateString: String
    let onRecipePicked: (PersistedRecipe) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.displayScale) private var displayScale
    @Environment(\.modelContext) private var modelContext
    @State private var searchText = ""
    @State private var recipes: [PersistedRecipe] = []

    var body: some View {
        NavigationStack {
            Group {
                if self.filteredRecipes.isEmpty {
                    self.emptyState
                } else {
                    self.recipeList
                }
            }
            .background(Color.hauptgangBackground.ignoresSafeArea())
            .navigationTitle("Add Recipe")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { self.dismiss() }
                }
            }
            .searchable(text: self.$searchText, prompt: "Search recipes")
            .onAppear { self.loadRecipes() }
        }
    }

    private var filteredRecipes: [PersistedRecipe] {
        if self.searchText.isEmpty {
            return self.recipes
        }
        return self.recipes.filter { $0.name.localizedStandardContains(self.searchText) }
    }

    private var recipeList: some View {
        List(self.filteredRecipes, id: \.id) { recipe in
            Button {
                self.onRecipePicked(recipe)
                self.dismiss()
            } label: {
                HStack(spacing: Theme.Spacing.md) {
                    self.recipeImage(recipe)

                    Text(recipe.name)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Color.hauptgangTextPrimary)
                        .lineLimit(2)
                }
                .padding(.vertical, Theme.Spacing.xs)
            }
            .listRowBackground(Color.hauptgangBackground)
            .listRowSeparator(.hidden, edges: recipe.id == self.filteredRecipes.first?.id ? .top : [])
            .listRowSeparator(.hidden, edges: recipe.id == self.filteredRecipes.last?.id ? .bottom : [])
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func recipeImage(_ recipe: PersistedRecipe) -> some View {
        Group {
            if let url = Constants.API.resolveURL(recipe.thumbnailCoverImageUrl) {
                CachedRecipeImage(url: url, maxPixelSize: 48 * self.displayScale) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.clear
                } failure: {
                    Color.clear
                }
            } else {
                Color.clear
            }
        }
        .frame(width: 48, height: 48)
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.sm))
    }

    private var emptyState: some View {
        VStack(spacing: Theme.Spacing.md) {
            Spacer()
            Image(systemName: "fork.knife")
                .font(.system(size: 40))
                .foregroundStyle(Color.hauptgangTextMuted)
            Text("No recipes found")
                .font(.subheadline)
                .foregroundStyle(Color.hauptgangTextSecondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private func loadRecipes() {
        do {
            let descriptor = FetchDescriptor<PersistedRecipe>()
            self.recipes = try self.modelContext.fetch(descriptor)
                .filter { $0.cookbookId == self.cookbookId && $0.importStatus != "failed" }
                .sorted { $0.updatedAt > $1.updatedAt }
        } catch {
            self.recipes = []
        }
    }
}

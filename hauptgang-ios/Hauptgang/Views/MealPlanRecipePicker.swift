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
            .background(Color.mcCanvas.ignoresSafeArea())
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
                        .foregroundStyle(Color.mcInk)
                        .lineLimit(2)
                }
                .padding(.vertical, Theme.Spacing.xs)
            }
            .listRowBackground(Color.mcCanvas)
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
                    Color.mcSunken
                } failure: {
                    RecipePlaceholderGradient.gradient(for: String(recipe.id))
                }
            } else {
                RecipePlaceholderGradient.gradient(for: String(recipe.id))
            }
        }
        .frame(width: 48, height: 48)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.control))
    }

    private var emptyState: some View {
        VStack(spacing: Theme.Spacing.md) {
            Spacer()
            Image(systemName: "fork.knife")
                .font(.system(size: 40))
                .foregroundStyle(Color.mcMuted)
            Text("No recipes found")
                .font(.subheadline)
                .foregroundStyle(Color.mcBody)
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

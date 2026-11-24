import SwiftUI

struct RecipeSearchView: View {
    @Environment(CookbookViewModel.self) private var cookbookViewModel
    var recipeViewModel: RecipeViewModel
    @Binding var searchQuery: String

    @State private var navigationPath = NavigationPath()
    @State private var recipeToDelete: DeleteCandidate?
    @State private var recipeToMove: MoveCandidate?

    var body: some View {
        NavigationStack(path: self.$navigationPath) {
            ScrollView {
                VStack(spacing: Theme.Spacing.sm) {
                    if #unavailable(iOS 26) {
                        SearchInputBar(
                            text: self.$searchQuery,
                            prompt: "Search recipes",
                            correctsText: false
                        )
                    }

                    if self.searchQuery.isEmpty {
                        if self.recentlyAdded.isEmpty {
                            self.promptView
                        } else {
                            self.section(
                                title: Text("Recently Added"),
                                recipes: self.recentlyAdded
                            )
                        }
                    } else if self.recipeViewModel.searchResults.isEmpty {
                        self.emptyResultsView
                    } else {
                        self.section(
                            title: Text(RecipeDisplayFormatter.recipeCount(self.recipeViewModel.searchResults.count)),
                            recipes: self.recipeViewModel.searchResults
                        )
                    }
                }
                .padding(.vertical, Theme.Spacing.sm)
            }
            .readableContentWidth()
            .scrollDismissesKeyboard(.immediately)
            .background(Color.mcCanvas.ignoresSafeArea())
            .navigationTitle("Search")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: Int.self) { recipeId in
                RecipeDetailView(recipeId: recipeId)
            }
            .modifier(SearchableModifier(searchQuery: self.$searchQuery))
        }
    }

    private var recentlyAdded: [PersistedRecipe] {
        Array(
            self.recipeViewModel.successfulRecipes
                .sorted { ($0.createdAt ?? $0.updatedAt) > ($1.createdAt ?? $1.updatedAt) }
                .prefix(8)
        )
    }

    private func section(title: Text, recipes: [PersistedRecipe]) -> some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            title
                .font(.caption.weight(.medium))
                .textCase(.uppercase)
                .tracking(1.1)
                .foregroundStyle(Color.mcBody)
                .padding(.horizontal, Theme.Spacing.md)

            LazyVStack(spacing: 0) {
                ForEach(recipes) { recipe in
                    if recipe.id != recipes.first?.id {
                        Rectangle()
                            .fill(Color.mcLine)
                            .frame(height: 1)
                            .padding(.leading, Theme.Spacing.md * 2 + RecipeRowView.photoSize)
                    }
                    self.recipeRow(recipe)
                }
            }
            .background(Color.mcSurface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.panel))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Radius.panel)
                    .stroke(Color.mcHairline, lineWidth: 1)
            )
        }
        .padding(.horizontal, Theme.Spacing.md)
    }

    private var promptView: some View {
        VStack(spacing: Theme.Spacing.md) {
            Spacer().frame(height: 60)
            Image(systemName: "magnifyingglass")
                .font(.system(size: 48))
                .foregroundStyle(Color.mcMuted)
            Text("Search your recipes")
                .font(.subheadline)
                .foregroundStyle(Color.mcBody)
        }
        .frame(maxWidth: .infinity)
    }

    private var emptyResultsView: some View {
        VStack(spacing: Theme.Spacing.md) {
            Spacer().frame(height: 60)
            Text("No results")
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundStyle(Color.mcInk)
            Text("Try a different search term")
                .font(.subheadline)
                .foregroundStyle(Color.mcBody)
        }
        .frame(maxWidth: .infinity)
    }

    private func recipeRow(_ recipe: PersistedRecipe) -> some View {
        Button {
            self.navigationPath.append(recipe.id)
        } label: {
            RecipeRowView(recipe: recipe)
        }
        .buttonStyle(.plain)
        .contentShape(.contextMenuPreview, RoundedRectangle(cornerRadius: Theme.Radius.panel))
        .contextMenu {
            if let targetCookbook = self.cookbookViewModel.cookbooks.first(where: {
                $0.id != self.cookbookViewModel.activeCookbook?.id
            }) {
                Button {
                    self.recipeToMove = MoveCandidate(
                        id: recipe.id,
                        name: recipe.name,
                        targetCookbookId: targetCookbook.id,
                        targetCookbookName: targetCookbook.displayName
                    )
                } label: {
                    Label("Move to \(targetCookbook.displayName)", systemImage: "arrow.right.arrow.left")
                }
            }
            Button(role: .destructive) {
                self.recipeToDelete = DeleteCandidate(id: recipe.id, name: recipe.name)
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
        .confirmationDialog(
            "Delete Recipe",
            isPresented: Binding(
                get: { self.recipeToDelete?.id == recipe.id },
                set: {
                    if !$0 {
                        self.recipeToDelete = nil
                    }
                }
            ),
            presenting: self.recipeToDelete
        ) { candidate in
            Button("Delete", role: .destructive) {
                Task {
                    await self.recipeViewModel.deleteRecipe(id: candidate.id)
                }
            }
        } message: { _ in
            Text("Are you sure?")
        }
        .confirmationDialog(
            "Move Recipe",
            isPresented: Binding(
                get: { self.recipeToMove?.id == recipe.id },
                set: {
                    if !$0 {
                        self.recipeToMove = nil
                    }
                }
            ),
            presenting: self.recipeToMove
        ) { candidate in
            Button("Move to \(candidate.targetCookbookName)") {
                Task {
                    await self.recipeViewModel.moveRecipe(
                        id: candidate.id,
                        toCookbookId: candidate.targetCookbookId
                    )
                }
            }
        } message: { candidate in
            Text("Move \"\(candidate.name)\" to \(candidate.targetCookbookName)?")
        }
    }
}

private struct SearchableModifier: ViewModifier {
    @Binding var searchQuery: String

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content
                .searchable(text: self.$searchQuery, prompt: "Search recipes")
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        } else {
            content
        }
    }
}

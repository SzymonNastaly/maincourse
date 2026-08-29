import SwiftUI

/// Main tab view container for authenticated users.
/// Renders tabs only; startup readiness and the splash overlay are owned by
/// `AuthenticatedAppShell`.
///
/// Note: the Meal Plan tab is intentionally hidden. `MealPlanView` and its
/// view model, service, repository, and API wiring are all still in place —
/// only the tab entry point was removed, so it can be restored by adding the
/// `SwiftUI.Tab` and enum case back.
struct MainTabView: View {
    @Environment(AuthenticatedSessionViewModel.self) private var session
    @State private var selectedTab: Tab = .recipes
    @State private var searchQuery = ""
    @State private var notificationRouter = NotificationRouter.shared
    @State private var pendingRecipeId: Int?

    enum Tab: Hashable {
        case recipes
        case shoppingList
        case settings
        case search
    }

    var body: some View {
        TabView(selection: self.$selectedTab) {
            SwiftUI.Tab("Recipes", systemImage: "fork.knife", value: Tab.recipes) {
                RecipesView(
                    recipeViewModel: self.session.recipeViewModel,
                    suppressTransientUI: !self.session.canDismissStartupSplash,
                    pendingRecipeId: self.$pendingRecipeId
                )
            }

            SwiftUI.Tab("Shopping List", systemImage: "cart", value: Tab.shoppingList) {
                ShoppingListView(viewModel: self.session.shoppingListViewModel)
            }

            SwiftUI.Tab("Settings", systemImage: "gearshape", value: Tab.settings) {
                SettingsView()
            }

            SwiftUI.Tab(value: Tab.search, role: .search) {
                RecipeSearchView(
                    recipeViewModel: self.session.recipeViewModel,
                    searchQuery: self.$searchQuery
                )
            }
        }
        .tint(.hauptgangPrimary)
        .modifier(TabBarBackgroundModifier())
        .modifier(TabBarMinimizeModifier())
        .modifier(TabSearchActivationModifier())
        .onChange(of: self.searchQuery) { _, newValue in
            Task { await self.session.recipeViewModel.search(query: newValue) }
        }
        .onChange(of: self.notificationRouter.pendingRoute, initial: true) { _, route in
            guard route != nil else { return }
            Task { await self.navigate(to: self.notificationRouter.consumeRoute()) }
        }
    }

    /// Send the user where a tapped notification pointed. A recipe may live in a
    /// cookbook that is not the active one, so switch first and let the recipe list
    /// reload before pushing the detail screen.
    private func navigate(to route: NotificationRoute?) async {
        switch route {
        case .shoppingList:
            self.selectedTab = .shoppingList

        case let .recipe(id, cookbookId):
            if let cookbookId,
               cookbookId != self.session.cookbookViewModel.activeCookbook?.id,
               let cookbook = self.session.cookbookViewModel.cookbooks.first(where: { $0.id == cookbookId }) {
                await self.session.switchCookbook(cookbook)
            }
            self.selectedTab = .recipes
            self.pendingRecipeId = id

        case nil:
            break
        }
    }
}

private struct TabBarBackgroundModifier: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content
        } else {
            content.toolbarBackgroundVisibility(.visible, for: .tabBar)
        }
    }
}

private struct TabBarMinimizeModifier: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            content
        }
    }
}

private struct TabSearchActivationModifier: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.tabViewSearchActivation(.searchTabSelection)
        } else {
            content
        }
    }
}

#Preview {
    let authManager = AuthManager()
    let session = AuthenticatedSessionViewModel()
    return MainTabView()
        .environmentObject(authManager)
        .environment(session)
        .environment(session.cookbookViewModel)
        .modelContainer(
            for: [
                PersistedRecipe.self,
                PersistedShoppingListItem.self,
                PersistedMealPlanDay.self,
                PersistedMealPlanEntry.self
            ],
            inMemory: true
        )
        .onAppear {
            authManager.signIn(user: User(id: 1, email: "test@example.com"))
        }
}

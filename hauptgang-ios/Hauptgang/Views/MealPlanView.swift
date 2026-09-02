import SwiftUI

struct MealPlanView: View {
    @EnvironmentObject var authManager: AuthManager
    @Environment(AuthenticatedSessionViewModel.self) private var session
    @Environment(CookbookViewModel.self) private var cookbookViewModel
    @Environment(NetworkMonitor.self) private var networkMonitor
    @Environment(\.modelContext) private var modelContext

    @State private var viewModel = MealPlanViewModel()
    @State private var pickerDate: String?

    private var navigationTitle: String {
        self.cookbookViewModel.activeCookbook?.name ?? "Meal Plan"
    }

    private var activeCookbookId: Int? {
        self.cookbookViewModel.activeCookbook?.id
    }

    var body: some View {
        NavigationStack {
            MealPlanDaysList(
                visibleDates: self.viewModel.visibleDates,
                entriesByDate: self.viewModel.entriesByDate,
                onAddTapped: { date in
                    self.pickerDate = date
                },
                onDeleteEntry: self.deleteEntry,
                onToggleVote: self.toggleVote
            )
            .background(Color.hauptgangBackground.ignoresSafeArea())
            .navigationTitle(self.navigationTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarTitleMenu {
                CookbookTitleMenu(
                    cookbooks: self.cookbookViewModel.cookbooks,
                    activeCookbookId: self.activeCookbookId,
                    onSelect: self.selectCookbook
                )
            }
            .refreshable {
                await self.refreshContent()
            }
            .task {
                await self.handleTask()
            }
            .onChange(of: self.authManager.authState) { _, newValue in
                self.handleAuthStateChange(newValue)
            }
            .onChange(of: self.activeCookbookId) { _, _ in
                self.handleCookbookChange()
            }
            .onChange(of: self.viewModel.didReceiveForbidden) { _, forbidden in
                self.handleForbiddenChange(forbidden)
            }
            .navigationDestination(for: Int.self) { recipeId in
                RecipeDetailView(recipeId: recipeId)
            }
            .sheet(item: self.$pickerDate, content: self.pickerSheet)
        }
        .offlineToast(isOffline: self.networkMonitor.isOffline)
    }

    private func pickerSheet(date: String) -> some View {
        MealPlanPickerSheet(
            cookbookId: self.activeCookbookId,
            date: date,
            onPickRecipe: { recipe in
                guard let cookbookId = self.activeCookbookId else { return }
                self.viewModel.addEntry(cookbookId: cookbookId, date: date, recipe: recipe)
            }
        )
    }

    private func handleTask() async {
        self.viewModel.configure(modelContext: self.modelContext)
        await self.refreshMealPlan()
    }

    private func refreshContent() async {
        await self.networkMonitor.refreshStatus()
        await self.refreshMealPlan()
    }

    private func handleAuthStateChange(_ newValue: AuthManager.AuthState) {
        if case .unauthenticated = newValue {
            self.viewModel.clearData()
        }
    }

    private func handleCookbookChange() {
        self.viewModel.resetForCookbookSwitch()
        Task {
            await self.refreshMealPlan()
        }
    }

    private func handleForbiddenChange(_ forbidden: Bool) {
        guard forbidden else { return }

        self.viewModel.didReceiveForbidden = false
        Task {
            await self.session.handleForbidden()
            await self.refreshMealPlan()
        }
    }

    private func selectCookbook(_ cookbook: Cookbook) {
        Task {
            await self.session.switchCookbook(cookbook)
        }
    }

    private func refreshMealPlan() async {
        guard let cookbookId = self.activeCookbookId else { return }
        await self.viewModel.refresh(cookbookId: cookbookId)
    }

    private func deleteEntry(_ entry: PersistedMealPlanEntry) {
        guard let cookbookId = self.activeCookbookId else { return }

        withAnimation {
            self.viewModel.deleteEntry(entry, cookbookId: cookbookId)
        }
    }

    private func toggleVote(_ entry: PersistedMealPlanEntry) {
        guard let cookbookId = self.activeCookbookId else { return }
        self.viewModel.toggleVote(entry: entry, cookbookId: cookbookId)
    }
}

private struct MealPlanDaysList: View {
    let visibleDates: [String]
    let entriesByDate: [String: [PersistedMealPlanEntry]]
    let onAddTapped: (String) -> Void
    let onDeleteEntry: (PersistedMealPlanEntry) -> Void
    let onToggleVote: (PersistedMealPlanEntry) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Theme.Spacing.lg) {
                ForEach(self.visibleDates, id: \.self) { date in
                    MealPlanDayRow(
                        dateString: date,
                        entries: self.entriesByDate[date] ?? [],
                        onAddTapped: { self.onAddTapped(date) },
                        onDeleteEntry: self.onDeleteEntry,
                        onToggleVote: self.onToggleVote
                    )
                }
            }
            .padding(.vertical, Theme.Spacing.md)
        }
    }
}

private struct MealPlanPickerSheet: View {
    let cookbookId: Int?
    let date: String
    let onPickRecipe: (PersistedRecipe) -> Void

    var body: some View {
        if let cookbookId {
            MealPlanRecipePicker(
                cookbookId: cookbookId,
                dateString: self.date,
                onRecipePicked: self.onPickRecipe
            )
        }
    }
}

// MARK: - String + Identifiable for sheet binding

extension String: @retroactive Identifiable {
    public var id: String {
        self
    }
}

#Preview {
    let authManager = AuthManager()
    let session = AuthenticatedSessionViewModel()
    return MealPlanView()
        .environmentObject(authManager)
        .environment(session)
        .environment(session.cookbookViewModel)
        .environment(NetworkMonitor.shared)
        .modelContainer(
            for: [PersistedRecipe.self, PersistedMealPlanDay.self, PersistedMealPlanEntry.self],
            inMemory: true
        )
        .onAppear {
            authManager.signIn(user: User(id: 1, email: "test@example.com"))
        }
}

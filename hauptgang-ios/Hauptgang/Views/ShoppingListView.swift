import SwiftUI

struct ShoppingListView: View {
    @EnvironmentObject var authManager: AuthManager
    @Environment(AuthenticatedSessionViewModel.self) private var session
    @Environment(CookbookViewModel.self) private var cookbookViewModel
    @Environment(NetworkMonitor.self) private var networkMonitor
    @Environment(\.modelContext) private var modelContext

    let viewModel: ShoppingListViewModel

    /// Mirrors `Notifications::StaleShoppingListCampaign::MIN_ITEMS` — the point at which
    /// this list actually becomes eligible for a reminder.
    static let notificationPromptThreshold = 3

    @State private var showRemoveAllConfirmation = false
    @State private var addItemText = ""
    @State private var checkedSectionExpanded = true

    private var displayUncheckedItems: [ShoppingListDisplayItem] {
        self.viewModel.uncheckedItems.map { item in
            ShoppingListDisplayItem(
                id: item.scopedClientId,
                name: item.name,
                details: item.details,
                isChecked: item.isChecked,
                onTap: { self.viewModel.toggleItem(item) },
                onDelete: { self.viewModel.deleteItem(item) }
            )
        }
    }

    private var displayCheckedItems: [ShoppingListDisplayItem] {
        self.viewModel.checkedItems.map { item in
            ShoppingListDisplayItem(
                id: item.scopedClientId,
                name: item.name,
                details: item.details,
                isChecked: item.isChecked,
                onTap: { self.viewModel.toggleItem(item) },
                onDelete: { self.viewModel.deleteItem(item) }
            )
        }
    }

    var body: some View {
        NavigationStack {
            self.screenContent
        }
        .offlineToast(isOffline: self.networkMonitor.isOffline)
    }

    private var screenContent: some View {
        Group {
            if self.viewModel.items.isEmpty {
                self.emptyState
            } else {
                self.gridView
            }
        }
        .refreshable {
            await self.networkMonitor.refreshStatus()
            await self.viewModel.refresh()
        }
        .background(Color.mcCanvas.ignoresSafeArea())
        .navigationTitle(self.cookbookViewModel.activeCookbook?.name ?? "Shopping List")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarTitleMenu {
            CookbookTitleMenu(
                cookbooks: self.cookbookViewModel.cookbooks,
                activeCookbookId: self.cookbookViewModel.activeCookbook?.id,
                onSelect: self.selectCookbook
            )
        }
        .task {
            self.viewModel.configure(modelContext: self.modelContext)
            await self.viewModel.refresh()
            await self.promptIfListIsWorthReminding()
        }
        .onAppear {
            // Returning to the tab counts as opening the list, and does not depend on
            // whether `.task` re-runs for a TabView child.
            Task { await self.promptIfListIsWorthReminding() }
        }
        .onChange(of: self.authManager.authState) { _, newValue in
            if case .unauthenticated = newValue {
                self.viewModel.clearData()
            }
        }
        .onChange(of: self.cookbookViewModel.activeCookbook?.id) { _, _ in
            self.viewModel.resetForCookbookSwitch()
            Task {
                await self.viewModel.refresh()
                await self.promptIfListIsWorthReminding()
            }
        }
        .onChange(of: self.viewModel.didReceiveForbidden) { _, forbidden in
            guard forbidden else { return }
            self.viewModel.didReceiveForbidden = false
            Task {
                await self.session.handleForbidden()
                await self.viewModel.refresh()
            }
        }
    }

    /// Called on list open and after every refresh that isn't mid-typing — never from
    /// `addCustomItem`. `keepFocusOnSubmit` leaves the keyboard up while someone types a
    /// list, and a permission dialog on the third item is the worst moment to ask.
    private func promptIfListIsWorthReminding() async {
        guard self.viewModel.uncheckedItems.count >= Self.notificationPromptThreshold else { return }

        await PushNotificationService.shared.promptForAuthorization()
    }

    private func selectCookbook(_ cookbook: Cookbook) {
        Task {
            await self.session.switchCookbook(cookbook)
        }
    }

    private var gridView: some View {
        ScrollView {
            VStack(spacing: 0) {
                ShoppingAddItemBar(viewModel: self.viewModel, text: self.$addItemText)

                ShoppingListSectionsContent(
                    uncheckedItems: self.displayUncheckedItems,
                    checkedItems: self.displayCheckedItems,
                    checkedSectionExpanded: self.$checkedSectionExpanded
                ) {
                    self.removeAllButton
                }
                .padding(.horizontal, Theme.Spacing.lg)
                .padding(.bottom, Theme.Spacing.lg)
            }
            .contentShape(Rectangle())
            .onTapGesture {
                UIApplication.shared.sendAction(
                    #selector(UIResponder.resignFirstResponder),
                    to: nil,
                    from: nil,
                    for: nil
                )
            }
        }
        .scrollDismissesKeyboard(.immediately)
    }

    @ViewBuilder
    private var removeAllButton: some View {
        if #available(iOS 26, *) {
            self.removeAllButtonGlass
        } else {
            self.removeAllButtonLegacy
        }
    }

    @available(iOS 26, *)
    private var removeAllButtonGlass: some View {
        Button {
            self.showRemoveAllConfirmation = true
        } label: {
            Text("Remove All")
                .font(.caption)
                .fontWeight(.medium)
        }
        .buttonStyle(.glass)
        .controlSize(.small)
        .disabled(self.viewModel.isSyncing)
        .opacity(self.viewModel.isSyncing ? 0.5 : 1.0)
        .textCase(nil)
        .confirmationDialog(
            "This will remove all items from your shopping list, including checked items.",
            isPresented: self.$showRemoveAllConfirmation,
            titleVisibility: .visible
        ) {
            Button("Remove All", role: .destructive) {
                Task { await self.viewModel.removeAllItems() }
            }
        }
    }

    private var removeAllButtonLegacy: some View {
        Button {
            self.showRemoveAllConfirmation = true
        } label: {
            Text("Remove All")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(Color.mcBody)
                .padding(.horizontal, Theme.Spacing.sm + 4)
                .padding(.vertical, Theme.Spacing.xs + 2)
                .background(
                    Capsule()
                        .fill(Color.mcCanvas)
                )
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .strokeBorder(Color.mcMuted.opacity(0.4), lineWidth: 1)
                )
        }
        .disabled(self.viewModel.isSyncing)
        .opacity(self.viewModel.isSyncing ? 0.5 : 1.0)
        .buttonStyle(RemoveAllButtonStyle())
        .textCase(nil)
        .confirmationDialog(
            "This will remove all items from your shopping list, including checked items.",
            isPresented: self.$showRemoveAllConfirmation,
            titleVisibility: .visible
        ) {
            Button("Remove All", role: .destructive) {
                Task { await self.viewModel.removeAllItems() }
            }
        }
    }

    private var emptyState: some View {
        ScrollView {
            VStack(spacing: Theme.Spacing.md) {
                ShoppingAddItemBar(viewModel: self.viewModel, text: self.$addItemText)

                Spacer()

                Text("Your shopping list is empty")
                    .font(.headline)
                    .foregroundStyle(Color.mcInk)

                Text("Add items from a recipe or type your own")
                    .font(.subheadline)
                    .foregroundStyle(Color.mcBody)
                    .multilineTextAlignment(.center)

                Spacer()
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: UIScreen.main.bounds.height * 0.5)
            .contentShape(Rectangle())
            .onTapGesture {
                UIApplication.shared.sendAction(
                    #selector(UIResponder.resignFirstResponder),
                    to: nil,
                    from: nil,
                    for: nil
                )
            }
        }
        .scrollDismissesKeyboard(.immediately)
    }
}

struct ShoppingAddItemBar: View {
    let viewModel: ShoppingListViewModel
    @Binding var text: String

    var body: some View {
        SearchInputBar(text: self.$text, prompt: "Add item", icon: "plus", onSubmit: {
            self.addItem()
        }, keepFocusOnSubmit: true)
    }

    private func addItem() {
        let trimmed = self.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        self.viewModel.addCustomItem(trimmed)
        self.text = ""
    }
}

private struct RemoveAllButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .offset(y: configuration.isPressed ? 2 : 0)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }
}

#Preview {
    let authManager = AuthManager()
    let session = AuthenticatedSessionViewModel()
    return ShoppingListView(viewModel: session.shoppingListViewModel)
        .environmentObject(authManager)
        .environment(session)
        .environment(session.cookbookViewModel)
        .environment(NetworkMonitor.shared)
        .modelContainer(for: PersistedShoppingListItem.self, inMemory: true)
        .onAppear {
            authManager.signIn(user: User(id: 1, email: "test@example.com"))
        }
}

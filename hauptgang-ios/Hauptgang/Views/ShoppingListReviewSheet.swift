import SwiftUI

struct ShoppingListReviewSheet: View {
    @Environment(\.dismiss) private var dismiss

    let recipeId: Int
    let shoppingListViewModel: ShoppingListViewModel

    @State private var items: [ShoppingListDraftItem]
    @State private var checkedSectionExpanded = true
    @State private var showsOldListConfirmation = false
    @State private var isReplacing = false
    @State private var replacementError: String?

    init(
        recipeId: Int,
        initialItems: [ShoppingListDraftItem],
        shoppingListViewModel: ShoppingListViewModel
    ) {
        self.recipeId = recipeId
        self.shoppingListViewModel = shoppingListViewModel
        self._items = State(initialValue: initialItems)
    }

    private var uncheckedItems: [ShoppingListDraftItem] {
        self.items.filter { !$0.isChecked }
    }

    private var checkedItems: [ShoppingListDraftItem] {
        self.items.filter(\.isChecked)
    }

    private var addButtonTitle: String {
        "Add \(self.uncheckedItems.count)"
    }

    private var displayUncheckedItems: [ShoppingListDisplayItem] {
        self.uncheckedItems.map { item in
            ShoppingListDisplayItem(
                id: item.id.uuidString,
                name: item.name,
                details: item.details,
                isChecked: item.isChecked,
                onTap: { self.toggleItem(item) },
                onDelete: nil
            )
        }
    }

    private var displayCheckedItems: [ShoppingListDisplayItem] {
        self.checkedItems.map { item in
            ShoppingListDisplayItem(
                id: item.id.uuidString,
                name: item.name,
                details: item.details,
                isChecked: item.isChecked,
                onTap: { self.toggleItem(item) },
                onDelete: nil
            )
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Theme.Spacing.md) {
                    if let replacementError {
                        Text(replacementError)
                            .font(.subheadline)
                            .foregroundStyle(Color.mcDanger)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .accessibilityIdentifier("shopping-list-replacement-error")
                    }

                    ShoppingListSectionsContent(
                        uncheckedItems: self.displayUncheckedItems,
                        checkedItems: self.displayCheckedItems,
                        checkedSectionExpanded: self.$checkedSectionExpanded
                    ) {
                        EmptyView()
                    }
                }
                .padding(.horizontal, Theme.Spacing.lg)
                .padding(.vertical, Theme.Spacing.lg)
            }
            .background(Color.mcCanvas.ignoresSafeArea())
            .navigationTitle("Add to Shopping List")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") {
                        self.dismiss()
                    }
                    .disabled(self.isReplacing)
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button(self.addButtonTitle) {
                        self.confirmAdd()
                    }
                    .disabled(
                        self.uncheckedItems.isEmpty ||
                            self.isReplacing ||
                            !self.shoppingListViewModel.hasCompletedShoppingListRefresh
                    )
                }
            }
        }
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(self.isReplacing)
        .confirmationDialog(
            "Start a fresh shopping list?",
            isPresented: self.$showsOldListConfirmation,
            titleVisibility: .visible
        ) {
            Button("Clear and add", role: .destructive) {
                Task { await self.clearAndAdd() }
            }
            Button("Keep and add") {
                self.keepAndAdd()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(
                "Some items have been here for more than 36 hours. " +
                    "Clear the whole list before adding these ingredients?"
            )
        }
    }

    private func toggleItem(_ item: ShoppingListDraftItem) {
        guard let index = self.items.firstIndex(where: { $0.id == item.id }) else {
            return
        }

        withAnimation(.snappy(duration: 0.25)) {
            self.items[index].isChecked.toggle()
        }
    }

    private func confirmAdd() {
        self.replacementError = nil
        if self.shoppingListViewModel.needsReviewBeforeAddingRecipeIngredients() {
            self.showsOldListConfirmation = true
            return
        }

        self.keepAndAdd()
    }

    private func keepAndAdd() {
        self.shoppingListViewModel.addIngredientsFromRecipe(
            self.uncheckedItems,
            sourceRecipeId: self.recipeId
        )
        self.finishAddition()
    }

    private func clearAndAdd() async {
        self.isReplacing = true
        let succeeded = await self.shoppingListViewModel.replaceListWithIngredientsFromRecipe(
            self.uncheckedItems,
            sourceRecipeId: self.recipeId
        )
        self.isReplacing = false

        if succeeded {
            self.finishAddition()
        } else {
            self.replacementError = "Could not replace the shopping list. Your existing items were kept."
        }
    }

    private func finishAddition() {
        let listIsWorthReminding =
            self.shoppingListViewModel.uncheckedItems.count >= ShoppingListView.notificationPromptThreshold
        self.dismiss()

        // Unlike the typed path, this is one deliberate button press with no keyboard in
        // the way, so asking right after the sheet closes is fair game.
        if listIsWorthReminding {
            Task { await PushNotificationService.shared.promptForAuthorization() }
        }
    }
}

#Preview {
    ShoppingListReviewSheet(
        recipeId: 1,
        initialItems: [
            ShoppingListDraftItem(name: "Onions", details: "2"),
            ShoppingListDraftItem(name: "Parsley", details: "1 bunch"),
            ShoppingListDraftItem(name: "Pasta", details: "500g", isChecked: true)
        ],
        shoppingListViewModel: ShoppingListViewModel()
    )
}

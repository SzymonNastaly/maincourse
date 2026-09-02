import SwiftUI

struct RecipeDetailToolbarContent: ToolbarContent {
    let hasIngredients: Bool
    let onAddToShoppingList: () -> Void
    let onEdit: () -> Void

    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            self.addToShoppingListButton
            self.editButton
        }
    }

    @ViewBuilder
    private var addToShoppingListButton: some View {
        if self.hasIngredients {
            if #available(iOS 26, *) {
                Button(action: self.onAddToShoppingList) {
                    Image(systemName: "cart.badge.plus")
                }
                .tint(Color.mcAccent)
                .accessibilityLabel("Add to shopping list")
                .accessibilityHint("Review this recipe's ingredients before adding them to your shopping list")
            } else {
                Button(action: self.onAddToShoppingList) {
                    Image(systemName: "cart")
                }
                .accessibilityLabel("Add to shopping list")
                .accessibilityHint("Review this recipe's ingredients before adding them to your shopping list")
                .tint(Color.mcAccent)
            }
        }
    }

    @ViewBuilder
    private var editButton: some View {
        if #available(iOS 26, *) {
            Button(action: self.onEdit) {
                Image(systemName: "pencil")
            }
            .tint(Color.mcAccent)
            .accessibilityLabel("Edit recipe")
        } else {
            Button(action: self.onEdit) {
                Image(systemName: "pencil")
            }
            .accessibilityLabel("Edit recipe")
        }
    }
}

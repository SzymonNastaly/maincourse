import Foundation

struct ShoppingListDraftItem: Identifiable, Hashable {
    let id: UUID
    let name: String
    let details: String?
    var isChecked: Bool

    init(id: UUID = UUID(), name: String, details: String? = nil, isChecked: Bool = false) {
        self.id = id
        self.name = name
        self.details = details
        self.isChecked = isChecked
    }

    init(id: UUID = UUID(), ingredient: StructuredIngredient, scale: Decimal) {
        let name: String
        let details: String?

        if ingredient.hasStructuredFields {
            let quantity = IngredientFormatter.formatQuantity(
                amount: ingredient.amount,
                amountMax: ingredient.amountMax,
                unit: ingredient.unit,
                scale: scale,
                // Details are persisted and shared across clients; keep their numeric representation stable.
                locale: Locale(identifier: "en_US_POSIX")
            )
            .trimmingCharacters(in: .whitespacesAndNewlines)
            let parsedName = (ingredient.name ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let note = ingredient.note?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let detailParts = [quantity, note].filter { !$0.isEmpty }

            name = parsedName.isEmpty
                ? ingredient.raw.trimmingCharacters(in: .whitespacesAndNewlines)
                : parsedName
            details = detailParts.isEmpty ? nil : detailParts.joined(separator: ", ")
        } else {
            name = ingredient.raw.trimmingCharacters(in: .whitespacesAndNewlines)
            details = nil
        }

        self.init(
            id: id,
            name: name,
            details: details,
            isChecked: !ingredient.shoppingDefaultIncluded
        )
    }
}

struct ShoppingListReviewDraft: Identifiable {
    let id = UUID()
    let recipeId: Int
    let items: [ShoppingListDraftItem]
}

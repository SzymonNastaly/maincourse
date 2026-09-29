import Foundation

/// Supermarket aisle for a shopping list item. Raw values and order match the
/// server's `Llm::IngredientInstructions::CATEGORIES`.
enum ShoppingCategory: String, CaseIterable, Identifiable {
    case produce
    case bakery
    case meatSeafood = "meat_seafood"
    case dairyEggs = "dairy_eggs"
    case pantry
    case oilsSpicesCondiments = "oils_spices_condiments"
    case frozen
    case beverages
    case household
    case other

    var id: String {
        self.rawValue
    }

    /// Unknown or missing values (an older app meeting a newer server) land in Other.
    init(serverValue: String?) {
        self = serverValue.flatMap(Self.init(rawValue:)) ?? .other
    }

    var title: String {
        switch self {
        case .produce: String(localized: "Produce")
        case .bakery: String(localized: "Bakery")
        case .meatSeafood: String(localized: "Meat & Seafood")
        case .dairyEggs: String(localized: "Dairy & Eggs")
        case .pantry: String(localized: "Pantry")
        case .oilsSpicesCondiments: String(localized: "Oils, Spices & Sauces")
        case .frozen: String(localized: "Frozen")
        case .beverages: String(localized: "Drinks")
        case .household: String(localized: "Household")
        case .other: String(localized: "Other")
        }
    }

    /// Groups items by aisle in display order, keeping their order within each
    /// aisle. A list that is entirely Other (e.g. right after an upgrade, before the
    /// server has categorized anything) comes back as one unlabeled group.
    static func aisles<Item>(
        for items: [Item],
        category: (Item) -> ShoppingCategory
    ) -> [(aisle: ShoppingCategory?, items: [Item])] {
        guard !items.isEmpty else { return [] }

        let byAisle = Dictionary(grouping: items, by: category)
        if byAisle.keys.allSatisfy({ $0 == .other }) {
            return [(nil, items)]
        }
        return Self.allCases.compactMap { aisle in
            byAisle[aisle].map { (aisle, $0) }
        }
    }
}

struct ShoppingCategoryHint: Hashable {
    let category: String
    let canonicalName: String?

    /// The most common category and canonical name pair among recipe ingredients
    /// whose parsed or canonical name matches `name`, ignoring case. Mirrors the
    /// server's `ShoppingList::CategoryLookup`.
    static func lookup(name: String, in ingredients: [StructuredIngredient]) -> ShoppingCategoryHint? {
        let key = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !key.isEmpty else { return nil }

        var counts: [ShoppingCategoryHint: Int] = [:]
        for ingredient in ingredients {
            guard let category = ingredient.category,
                  ShoppingCategory(rawValue: category) != nil,
                  ingredient.name?.lowercased() == key || ingredient.canonicalName == key
            else { continue }
            counts[ShoppingCategoryHint(category: category, canonicalName: ingredient.canonicalName), default: 0] += 1
        }

        // Ties break by category order so the result doesn't depend on dictionary order.
        return counts.max { lhs, rhs in
            if lhs.value != rhs.value {
                return lhs.value < rhs.value
            }
            return lhs.key.sortKey > rhs.key.sortKey
        }?.key
    }

    private var sortKey: (Int, String) {
        let index = ShoppingCategory.allCases.firstIndex(of: ShoppingCategory(serverValue: self.category)) ?? 0
        return (index, self.canonicalName ?? "")
    }
}

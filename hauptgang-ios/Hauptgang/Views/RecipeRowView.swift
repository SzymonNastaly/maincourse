import SwiftUI

/// Displays a single recipe in a list
struct RecipeRowView: View {
    let recipe: PersistedRecipe

    var body: some View {
        HStack(spacing: Theme.Spacing.md) {
            // Recipe info
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                HStack(spacing: Theme.Spacing.sm) {
                    Text(self.recipe.name)
                        .font(.headline)
                        .foregroundColor(.mcInk)
                        .lineLimit(2)

                    if self.recipe.favorite {
                        Image(systemName: "heart.fill")
                            .font(.caption)
                            .foregroundColor(.mcAccent)
                    }
                }

                // Time info
                if let timeText = formattedTime {
                    HStack(spacing: Theme.Spacing.sm) {
                        Image(systemName: "clock")
                            .font(.caption2)
                        Text(timeText)
                            .font(.mcMono(.caption))
                    }
                    .foregroundColor(.mcMuted)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // Chevron indicator
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundColor(.mcMuted)
        }
        .padding(Theme.Spacing.md)
        .background(Color.mcSurface)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.card))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Radius.card)
                .stroke(Color.mcHairline, lineWidth: 1)
        )
    }

    /// Formats prep and cook time into a readable string
    private var formattedTime: String? {
        let prep = self.recipe.prepTime.flatMap { $0 > 0 ? RecipeDisplayFormatter.minutes($0) : nil }
        let cook = self.recipe.cookTime.flatMap { $0 > 0 ? RecipeDisplayFormatter.minutes($0) : nil }

        switch (prep, cook) {
        case let (prep?, cook?):
            return String(
                localized: "\(prep) prep + \(cook) cook",
                comment: "Recipe preparation and cooking durations."
            )
        case let (prep?, nil):
            return String(localized: "\(prep) prep", comment: "Recipe preparation duration.")
        case let (nil, cook?):
            return String(localized: "\(cook) cook", comment: "Recipe cooking duration.")
        default:
            return nil
        }
    }
}

#Preview("With all info") {
    RecipeRowView(
        recipe: PersistedRecipe(
            id: 1,
            name: "Spaghetti Carbonara",
            prepTime: 15,
            cookTime: 20,
            favorite: true,
            updatedAt: Date()
        )
    )
    .padding()
    .background(Color.mcCanvas)
}

#Preview("Minimal info") {
    RecipeRowView(
        recipe: PersistedRecipe(
            id: 2,
            name: "Quick Salad",
            favorite: false,
            updatedAt: Date()
        )
    )
    .padding()
    .background(Color.mcCanvas)
}

import SwiftUI

/// Compact recipe row for lists: a square photo, the title and the total time.
struct RecipeRowView: View {
    @Environment(\.displayScale) private var displayScale

    let recipe: PersistedRecipe

    static let photoSize: CGFloat = 56

    var body: some View {
        HStack(spacing: Theme.Spacing.md) {
            self.photo
                .frame(width: Self.photoSize, height: Self.photoSize)
                .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.card))

            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(self.recipe.name)
                    .font(.headline)
                    .foregroundStyle(Color.mcInk)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                if let totalTime = self.totalTimeMinutes {
                    Text(RecipeDisplayFormatter.minutes(totalTime))
                        .font(.mcMono(.subheadline))
                        .foregroundStyle(Color.mcBody)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, Theme.Spacing.md)
        .padding(.vertical, Theme.Spacing.sm)
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var photo: some View {
        let placeholder = RecipePlaceholderGradient.gradient(for: String(self.recipe.id))
        ZStack {
            if let url = Constants.API.resolveURL(
                self.recipe.thumbnailCoverImageUrl ?? self.recipe.cardCoverImageUrl
            ) {
                Color.clear
                    .background {
                        CachedRecipeImage(url: url, maxPixelSize: Self.photoSize * self.displayScale) { image in
                            image
                                .resizable()
                                .scaledToFill()
                        } placeholder: {
                            Color.mcSunken
                        } failure: {
                            placeholder
                        }
                    }
                    .clipped()
            } else {
                placeholder
            }

            if self.recipe.importStatus == "pending" {
                Color.black.opacity(0.5)
                ProgressView()
                    .tint(.white)
            }
        }
    }

    private var totalTimeMinutes: Int? {
        let total = (self.recipe.prepTime ?? 0) + (self.recipe.cookTime ?? 0)
        return total > 0 ? total : nil
    }
}

#Preview("Rows") {
    VStack(spacing: 0) {
        RecipeRowView(
            recipe: PersistedRecipe(
                id: 1,
                name: "Spaghetti Carbonara with Crispy Pancetta and a Long Title",
                prepTime: 15,
                cookTime: 20,
                favorite: true,
                coverImageUrl: "https://images.unsplash.com/photo-1612874742237-6526221588e3?w=400",
                updatedAt: Date()
            )
        )
        Divider()
        RecipeRowView(
            recipe: PersistedRecipe(id: 2, name: "Quick Salad", favorite: false, updatedAt: Date())
        )
    }
    .background(Color.mcSurface)
    .padding()
    .background(Color.mcCanvas)
}

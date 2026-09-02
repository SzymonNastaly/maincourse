import SwiftUI

/// Card-style recipe display matching web design
/// Two visual modes: with image (gradient overlay) or without (solid background)
struct RecipeCardView: View {
    @Environment(\.displayScale) private var displayScale

    let recipe: PersistedRecipe

    /// Fixed card height matching web design (224px)
    private let cardHeight: CGFloat = 224

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .bottom) {
                // Background layer
                if let url = Constants.API.resolveURL(self.recipe.cardCoverImageUrl) {
                    self.imageBackgroundView(
                        url: url,
                        maxPixelSize: max(proxy.size.width, proxy.size.height) * self.displayScale
                    )
                } else {
                    self.solidBackgroundView
                }

                // Content overlay
                self.contentView

                // Import status overlay
                if let status = self.recipe.importStatus {
                    self.importStatusOverlay(status: status)
                }
            }
        }
        .frame(height: self.cardHeight)
        .contentShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
        .shadow(
            color: Theme.Shadow.sm.color,
            radius: Theme.Shadow.sm.radius,
            y: Theme.Shadow.sm.offsetY
        )
    }

    // MARK: - Background Views

    private func imageBackgroundView(url: URL, maxPixelSize: CGFloat) -> some View {
        // Use Color.clear as sizing anchor - it fills exactly the proposed size (like Shape)
        // AsyncImage in .background doesn't affect layout, .clipped clips overflow
        Color.clear
            .background {
                CachedRecipeImage(url: url, maxPixelSize: maxPixelSize) { image in
                    image
                        .resizable()
                        .scaledToFill()
                } placeholder: {
                    Color.hauptgangSurfaceRaised
                } failure: {
                    // Fall back to solid background on error.
                    Color.hauptgangCard
                }
            }
            .clipped()
            .overlay {
                // Gradient overlay for text readability
                // Matches web: 60% opacity at bottom → 35% at 40% → transparent at 70%
                LinearGradient(
                    stops: [
                        .init(color: .black.opacity(0.6), location: 0),
                        .init(color: .black.opacity(0.35), location: 0.4),
                        .init(color: .clear, location: 0.7)
                    ],
                    startPoint: .bottom,
                    endPoint: .top
                )
            }
    }

    private var solidBackgroundView: some View {
        RoundedRectangle(cornerRadius: Theme.CornerRadius.lg)
            .fill(Color.hauptgangSurfaceRaised)
            .overlay(
                RoundedRectangle(cornerRadius: Theme.CornerRadius.lg)
                    .stroke(Color.hauptgangBorderSubtle, lineWidth: 1)
            )
    }

    // MARK: - Content View

    private var contentView: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            Spacer()

            // Recipe name
            Text(self.recipe.name)
                .font(.system(.headline, design: .serif))
                .fontWeight(.bold)
                .foregroundColor(self.hasImage ? .white : .hauptgangTextPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)

            // Time info
            if let totalTime = totalTimeMinutes {
                HStack(spacing: Theme.Spacing.xs) {
                    Image(systemName: "clock")
                        .font(.caption2)
                    Text("\(totalTime)m")
                        .font(.caption)
                }
                .foregroundColor(self.hasImage ? .white.opacity(0.8) : .hauptgangTextSecondary)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .padding(Theme.Spacing.md)
    }

    // MARK: - Import Status Overlay

    @ViewBuilder
    private func importStatusOverlay(status: String) -> some View {
        switch status {
        case "pending":
            ZStack {
                // Semi-transparent overlay
                Color.black.opacity(0.5)

                VStack(spacing: Theme.Spacing.sm) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(1.2)

                    Text("Importing...")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.white)
                }
            }

        default:
            EmptyView()
        }
    }

    // MARK: - Helpers

    private var hasImage: Bool {
        self.recipe.cardCoverImageUrl != nil
    }

    /// Combined prep + cook time
    private var totalTimeMinutes: Int? {
        let prep = self.recipe.prepTime ?? 0
        let cook = self.recipe.cookTime ?? 0
        let total = prep + cook
        return total > 0 ? total : nil
    }

    /// Whether the recipe is currently being imported
    var isPending: Bool {
        self.recipe.importStatus == "pending"
    }
}

// MARK: - Previews

#Preview("With image") {
    RecipeCardView(
        recipe: PersistedRecipe(
            id: 1,
            name: "Spaghetti Carbonara with Crispy Pancetta",
            prepTime: 15,
            cookTime: 20,
            favorite: true,
            coverImageUrl: "https://images.unsplash.com/photo-1612874742237-6526221588e3?w=400",
            updatedAt: Date()
        )
    )
    .frame(width: 180)
    .padding()
    .background(Color.hauptgangBackground)
}

#Preview("Without image") {
    RecipeCardView(
        recipe: PersistedRecipe(
            id: 2,
            name: "Quick Garden Salad",
            prepTime: 10,
            favorite: false,
            updatedAt: Date()
        )
    )
    .frame(width: 180)
    .padding()
    .background(Color.hauptgangBackground)
}

#Preview("Grid layout") {
    RecipeCardGridPreview()
}

private struct RecipeCardGridPreview: View {
    private let columns = [
        GridItem(.flexible(), spacing: Theme.Spacing.md),
        GridItem(.flexible(), spacing: Theme.Spacing.md)
    ]

    private var sampleRecipes: [PersistedRecipe] {
        [
            PersistedRecipe(
                id: 1,
                name: "Spaghetti Carbonara",
                prepTime: 15,
                cookTime: 20,
                favorite: true,
                coverImageUrl: "https://images.unsplash.com/photo-1612874742237-6526221588e3?w=400",
                updatedAt: Date()
            ),
            PersistedRecipe(id: 2, name: "Quick Salad", prepTime: 10, favorite: false, updatedAt: Date()),
            PersistedRecipe(
                id: 3,
                name: "Chicken Tikka Masala with Basmati Rice",
                prepTime: 20,
                cookTime: 40,
                favorite: true,
                coverImageUrl: "https://images.unsplash.com/photo-1565557623262-b51c2513a641?w=400",
                updatedAt: Date()
            ),
            PersistedRecipe(id: 4, name: "Avocado Toast", prepTime: 5, favorite: false, updatedAt: Date())
        ]
    }

    var body: some View {
        ScrollView {
            LazyVGrid(columns: self.columns, spacing: Theme.Spacing.md) {
                ForEach(self.sampleRecipes, id: \.id) { recipe in
                    RecipeCardView(recipe: recipe)
                }
            }
            .padding(Theme.Spacing.lg)
        }
        .background(Color.hauptgangBackground)
    }
}

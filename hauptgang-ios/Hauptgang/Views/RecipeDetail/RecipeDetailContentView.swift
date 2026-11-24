import SwiftUI

/// One column on iPhone and narrow iPad windows. On a regular-width iPad the
/// facts and ingredients sit beside the steps so neither scrolls the other
/// away. Accessibility text sizes keep one column, because a 360-point column
/// holds only a few words per line at those sizes.
enum RecipePageLayout {
    case singleColumn
    case twoColumns

    init(horizontalSizeClass: UserInterfaceSizeClass?, dynamicTypeSize: DynamicTypeSize) {
        self = horizontalSizeClass == .regular && !dynamicTypeSize.isAccessibilitySize
            ? .twoColumns
            : .singleColumn
    }

    var maxContentWidth: CGFloat {
        switch self {
        case .singleColumn: 700
        case .twoColumns: 1100
        }
    }
}

struct RecipeDetailContentView: View {
    @Environment(\.displayScale) private var displayScale
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    let recipe: RecipeDetail
    let heroImageHeight: CGFloat
    let isIOS26: Bool

    /// Portion-scaling state owned by the parent so the toolbar's "add to
    /// shopping list" action can read the current scale.
    @Binding var currentServings: Int?
    var localHero: AnyView?

    @State private var checkedIngredients: Set<StructuredIngredient.ID> = []
    @State private var doneSteps: Set<Int> = []

    private var layout: RecipePageLayout {
        RecipePageLayout(horizontalSizeClass: self.horizontalSizeClass, dynamicTypeSize: self.dynamicTypeSize)
    }

    private var heroHeight: CGFloat {
        self.layout == .twoColumns ? 340 : self.heroImageHeight
    }

    private var hasHeroImage: Bool {
        self.localHero != nil || self.recipe.heroCoverImageUrl != nil
    }

    private var hasFacts: Bool {
        (self.recipe.prepTime ?? 0) > 0 || (self.recipe.cookTime ?? 0) > 0 || self.baseServings != nil
    }

    private var baseServings: Int? {
        guard let servings = self.recipe.servings, servings > 0 else { return nil }
        return servings
    }

    private var effectiveServings: Int {
        self.currentServings ?? self.baseServings ?? 1
    }

    /// Multiplier applied to ingredient quantities. Returns 1 when scaling
    /// is unavailable (no base servings or no override).
    private var scale: Decimal {
        guard let base = self.baseServings, base > 0 else { return 1 }
        return Decimal(self.effectiveServings) / Decimal(base)
    }

    private var source: (url: URL, host: String)? {
        guard let string = self.recipe.sourceUrl,
              let url = URL(string: string),
              let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https",
              let host = url.host(), !host.isEmpty
        else { return nil }
        return (url, host.hasPrefix("www.") ? String(host.dropFirst(4)) : host)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if self.hasHeroImage {
                    self.heroImage
                }

                VStack(alignment: .leading, spacing: Theme.Spacing.md + Theme.Spacing.xs) {
                    self.titleBlock

                    switch self.layout {
                    case .singleColumn:
                        self.factsAndIngredients
                        self.stepsAndNotes
                    case .twoColumns:
                        HStack(alignment: .top, spacing: Theme.Spacing.xl) {
                            VStack(alignment: .leading, spacing: Theme.Spacing.md + Theme.Spacing.xs) {
                                self.factsAndIngredients
                            }
                            .frame(width: 360)
                            // Otherwise the facts card's dividers stretch to the steps column's height.
                            .fixedSize(horizontal: false, vertical: true)

                            VStack(alignment: .leading, spacing: Theme.Spacing.md + Theme.Spacing.xs) {
                                self.stepsAndNotes
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }

                    if let source = self.source {
                        Link(destination: source.url) {
                            Text("View original at \(source.host)")
                                .font(.subheadline)
                                .multilineTextAlignment(.leading)
                        }
                        .tint(Color.mcAccent)
                    }
                }
                .frame(maxWidth: self.layout.maxContentWidth, alignment: .leading)
                .padding(Theme.Spacing.lg)
                .frame(maxWidth: .infinity)
            }
            .frame(maxWidth: .infinity)
        }
        .scrollContentBackground(.hidden)
        .ignoresSafeArea(edges: self.hasHeroImage && self.isIOS26 ? .top : [])
        .modifier(RecipeScreenAwakeModifier())
    }

    @ViewBuilder
    private var heroImage: some View {
        if let localHero = self.localHero {
            localHero
        } else if let url = Constants.API.resolveURL(self.recipe.heroCoverImageUrl) {
            Color.clear
                .frame(height: self.heroHeight)
                .frame(maxWidth: .infinity)
                .background {
                    GeometryReader { proxy in
                        CachedRecipeImage(
                            url: url,
                            maxPixelSize: max(proxy.size.width, proxy.size.height) * self.displayScale
                        ) { image in
                            image
                                .resizable()
                                .scaledToFill()
                        } placeholder: {
                            Color.mcSunken
                                .overlay {
                                    ProgressView()
                                        .tint(.mcMuted)
                                }
                        } failure: {
                            Color.mcSunken
                        }
                    }
                }
                .clipped()
                .overlay(alignment: .top) {
                    if self.isIOS26 {
                        LinearGradient(
                            colors: [.black.opacity(0.4), .clear],
                            startPoint: .top,
                            endPoint: .center
                        )
                        .frame(height: 100)
                    }
                }
        }
    }

    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            Text(self.recipe.name)
                .font(.title2)
                .fontWeight(.semibold)
                .foregroundColor(.mcInk)

            if !self.recipe.tags.isEmpty {
                Text(verbatim: self.recipe.tags.map(\.name).joined(separator: " · "))
                    .font(.subheadline)
                    .foregroundColor(.mcBody)
            }
        }
    }

    @ViewBuilder
    private var factsAndIngredients: some View {
        if self.hasFacts {
            RecipeFactsCard(
                prepTime: self.recipe.prepTime,
                cookTime: self.recipe.cookTime,
                baseServings: self.baseServings,
                servings: Binding(
                    get: { self.effectiveServings },
                    set: { self.currentServings = $0 }
                )
            )
        }

        if !self.recipe.resolvedIngredients.isEmpty {
            self.ingredientsSection
        }
    }

    @ViewBuilder
    private var stepsAndNotes: some View {
        if !self.recipe.instructions.isEmpty {
            self.instructionsSection
        }

        if let notes = self.recipe.notes, !notes.isEmpty {
            self.notesSection(notes)
        }
    }

    private var ingredientsSection: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            self.sectionHeader("Ingredients")

            VStack(alignment: .leading, spacing: 0) {
                ForEach(self.recipe.resolvedIngredients) { ingredient in
                    IngredientRow(
                        ingredient: ingredient,
                        scale: self.scale,
                        isChecked: self.checkedIngredients.contains(ingredient.id)
                    ) {
                        HapticManager.shared.selection()
                        self.checkedIngredients.formSymmetricDifference([ingredient.id])
                    }
                }
            }
        }
    }

    private var instructionsSection: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            self.sectionHeader("Steps")

            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                ForEach(Array(self.recipe.instructions.enumerated()), id: \.offset) { index, instruction in
                    StepRow(
                        number: index + 1,
                        instruction: instruction,
                        isDone: self.doneSteps.contains(index),
                        timers: StepTimerLocator.locate(self.recipe.timers(forStep: index), in: instruction),
                        recipeId: self.recipe.id,
                        recipeName: self.recipe.name
                    ) {
                        HapticManager.shared.selection()
                        self.doneSteps.formSymmetricDifference([index])
                    }
                }
            }
        }
    }

    private func notesSection(_ notes: String) -> some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
            self.sectionHeader("Notes")

            Text(notes)
                .font(.body)
                .foregroundColor(.mcBody)
                .italic()
        }
    }

    private func sectionHeader(_ title: LocalizedStringKey) -> some View {
        Text(title)
            .font(.headline)
            .foregroundColor(.mcInk)
            .accessibilityAddTraits(.isHeader)
    }
}

#Preview("Source link and tags") {
    @Previewable @State var servings: Int?
    RecipeDetailContentView(
        recipe: RecipeDetail(
            id: 1,
            name: "Creamy Tomato & Basil Orzo",
            prepTime: 10,
            cookTime: 20,
            favorite: false,
            servings: 4,
            ingredients: ["2 tbsp olive oil", "3 cloves garlic, sliced", "250 g orzo"],
            instructions: ["Warm the olive oil in a deep pan.", "Add the garlic and cook until fragrant."],
            notes: "A one-pan lunch.",
            sourceUrl: "https://www.example.com/orzo",
            tags: [RecipeTag(id: 1, name: "Dinner"), RecipeTag(id: 2, name: "Vegetarian")],
            createdAt: .now,
            updatedAt: .now
        ),
        heroImageHeight: 280,
        isIOS26: false,
        currentServings: $servings
    )
}

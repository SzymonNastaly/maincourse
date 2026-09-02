import SwiftUI

struct RecipeDetailContentView: View {
    @Environment(\.displayScale) private var displayScale

    let recipe: RecipeDetail
    let heroImageHeight: CGFloat
    let isIOS26: Bool
    let isCookingMode: Bool
    let onToggleCookingMode: () -> Void

    /// Portion-scaling state owned by the parent so the toolbar's "add to
    /// shopping list" action can read the current scale.
    @Binding var currentServings: Int?

    private var hasHeroImage: Bool {
        self.recipe.heroCoverImageUrl != nil
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

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if self.hasHeroImage {
                    self.heroImage
                }

                VStack(alignment: .leading, spacing: Theme.Spacing.lg) {
                    if !self.hasHeroImage {
                        HStack {
                            Spacer()
                            self.cookingModeButton
                        }
                    }

                    Text(self.recipe.name)
                        .font(.system(.title2, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(.hauptgangTextPrimary)

                    if (self.recipe.prepTime ?? 0) > 0
                        || (self.recipe.cookTime ?? 0) > 0
                        || (self.recipe.servings ?? 0) > 0 {
                        self.durationCard
                    }

                    if !self.recipe.resolvedIngredients.isEmpty {
                        self.ingredientsSection
                    }

                    if !self.recipe.instructions.isEmpty {
                        self.instructionsSection
                    }

                    if let notes = self.recipe.notes, !notes.isEmpty {
                        self.notesSection(notes)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Theme.Spacing.lg)
                .overlay(alignment: .topTrailing) {
                    if self.hasHeroImage {
                        self.cookingModeButton
                            .padding(.trailing, Theme.Spacing.lg)
                            .offset(y: -18)
                    }
                }
            }
            .frame(maxWidth: .infinity)
        }
        .scrollContentBackground(.hidden)
        .ignoresSafeArea(edges: self.hasHeroImage && self.isIOS26 ? .top : [])
    }

    @ViewBuilder
    private var heroImage: some View {
        if let url = Constants.API.resolveURL(self.recipe.heroCoverImageUrl) {
            Color.clear
                .frame(height: self.heroImageHeight)
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
                            Color.gray.opacity(0.2)
                                .overlay {
                                    ProgressView()
                                        .tint(.hauptgangTextMuted)
                                }
                        } failure: {
                            Color.hauptgangSurfaceRaised
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

    private var durationCard: some View {
        let hasPrep = (self.recipe.prepTime ?? 0) > 0
        let hasCook = (self.recipe.cookTime ?? 0) > 0
        let hasDuration = hasPrep || hasCook

        return HStack(spacing: 0) {
            if hasDuration {
                HStack(spacing: 0) {
                    if let prepTime = self.recipe.prepTime, prepTime > 0 {
                        self.durationItem(icon: "clock", label: "Prep", value: "\(prepTime)m")
                    }

                    if let cookTime = self.recipe.cookTime, cookTime > 0 {
                        if hasPrep {
                            Divider()
                                .frame(height: 32)
                        }
                        self.durationItem(icon: "flame", label: "Cook", value: "\(cookTime)m")
                    }
                }
                .frame(maxWidth: .infinity)
            }

            if let baseServings = self.baseServings {
                if hasDuration {
                    Divider()
                        .frame(height: 32)
                }
                PortionScalerView(
                    servings: Binding(
                        get: { self.effectiveServings },
                        set: { self.currentServings = $0 }
                    ),
                    baseServings: baseServings
                )
                .frame(maxWidth: .infinity)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Theme.Spacing.md)
        .background(Color.hauptgangSurfaceRaised)
        .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.lg))
    }

    private var ingredientsSection: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            self.sectionHeader("Ingredients")

            VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                ForEach(self.recipe.resolvedIngredients) { ingredient in
                    IngredientRow(ingredient: ingredient, scale: self.scale)
                }
            }
        }
    }

    private var instructionsSection: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            self.sectionHeader("Steps")

            VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                ForEach(Array(self.recipe.instructions.enumerated()), id: \.offset) { index, instruction in
                    HStack(alignment: .top, spacing: Theme.Spacing.md) {
                        Text("\(index + 1)")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .frame(width: 24, height: 24)
                            .background(Color.hauptgangPrimary)
                            .clipShape(Circle())

                        Text(instruction)
                            .font(.body)
                            .foregroundColor(.hauptgangTextPrimary)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var cookingModeButton: some View {
        if #available(iOS 26, *) {
            self.cookingModeButtonGlass
        } else {
            self.cookingModeButtonLegacy
        }
    }

    @available(iOS 26, *)
    @ViewBuilder
    private var cookingModeButtonGlass: some View {
        let button = Button(action: self.onToggleCookingMode) {
            HStack(spacing: 4) {
                Text("Keep Screen On")

                if self.isCookingMode {
                    Text("(active)")
                        .transition(.push(from: .bottom))
                }
            }
            .font(.subheadline)
            .fontWeight(.medium)
        }

        if self.isCookingMode {
            button
                .buttonStyle(.glassProminent)
                .tint(Color.hauptgangPrimary)
        } else {
            button
                .buttonStyle(.glass)
                .tint(Color.hauptgangPrimary)
        }
    }

    private var cookingModeButtonLegacy: some View {
        Button(action: self.onToggleCookingMode) {
            HStack(spacing: 4) {
                Text("Keep Screen On")

                if self.isCookingMode {
                    Text("(active)")
                        .transition(.push(from: .bottom))
                }
            }
            .font(.subheadline)
            .fontWeight(.medium)
            .foregroundColor(self.isCookingMode ? .white : Color.hauptgangPrimary)
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
            .background(
                Capsule()
                    .fill(self.isCookingMode ? Color.hauptgangPrimary : Color.hauptgangSurfaceRaised)
            )
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .strokeBorder(Color.hauptgangPrimary.opacity(self.isCookingMode ? 0 : 0.3), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.1), radius: 4, y: 2)
        }
        .buttonStyle(PressDownButtonStyle())
    }

    private func durationItem(icon: String, label: String, value: String) -> some View {
        VStack(spacing: Theme.Spacing.xs) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(.hauptgangPrimary)
                .frame(height: 24)

            Text(value)
                .font(.headline)
                .foregroundColor(.hauptgangTextPrimary)
                .frame(height: 28)

            Text(label)
                .font(.caption)
                .foregroundColor(.hauptgangTextSecondary)
                .frame(height: 18)
        }
        .frame(maxWidth: .infinity)
    }

    private func notesSection(_ notes: String) -> some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            self.sectionHeader("Notes")

            Text(notes)
                .font(.body)
                .foregroundColor(.hauptgangTextSecondary)
                .italic()
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.headline)
            .foregroundColor(.hauptgangTextPrimary)
    }
}

private struct PressDownButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .offset(y: configuration.isPressed ? 2 : 0)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// Renders a single ingredient row with differential styling for parsed
/// rows (quantity in tabular figures + accent unit) and a plain `raw`
/// fallback for unparsed rows. Honors the `scale` multiplier from the
/// portion scaler.
private struct IngredientRow: View {
    let ingredient: StructuredIngredient
    let scale: Decimal

    var body: some View {
        HStack(alignment: .top, spacing: Theme.Spacing.sm) {
            Circle()
                .fill(Color.hauptgangPrimary)
                .frame(width: 6, height: 6)
                .padding(.top, 8)

            if self.ingredient.hasStructuredFields {
                self.structuredText
            } else {
                Text(self.ingredient.raw)
                    .font(.body)
                    .foregroundColor(.hauptgangTextPrimary)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    @ViewBuilder
    private var structuredText: some View {
        let quantityText = IngredientFormatter.formatQuantity(
            amount: self.ingredient.amount,
            amountMax: self.ingredient.amountMax,
            unit: self.ingredient.unit,
            scale: self.scale
        )

        (
            Text(quantityText.isEmpty ? "" : "\(quantityText) ")
                .font(.body)
                .fontWeight(.semibold)
                .monospacedDigit()
                .foregroundColor(.hauptgangPrimary)
                + Text(self.ingredient.name ?? self.ingredient.raw)
                .font(.body)
                .foregroundColor(.hauptgangTextPrimary)
                + Text(self.ingredient.note.map { ", \($0)" } ?? "")
                .font(.body)
                .foregroundColor(.hauptgangTextSecondary)
        )
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

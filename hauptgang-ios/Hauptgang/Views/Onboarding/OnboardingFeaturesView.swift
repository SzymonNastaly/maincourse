import SwiftUI

/// Shown after the example recipe appears: what else a saved recipe unlocks, right before signup.
struct OnboardingFeaturesView: View {
    let sample: DemoRecipe
    let onKeep: () -> Void
    let onContinueWithoutRecipe: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                    Text("And once it’s in your cookbook…")
                        .font(.title.bold())
                        .foregroundStyle(Color.mcInk)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .padding(.bottom, Theme.Spacing.sm)

                    FeatureRow(
                        title: "Cook for any number",
                        detail: "Change the servings and every amount adjusts."
                    ) {
                        PortionsFragment(sample: self.sample)
                    }
                    // Hidden while meal planning is inactive; restore when it ships again.
                    // FeatureRow(title: "Plan your week", detail: "Put recipes on days, so dinner is already decided.") {
                    //     MealPlanFragment(sample: self.sample)
                    // }
                    FeatureRow(title: "Shop from one list", detail: "Send ingredients to a shopping list in a tap.") {
                        ShoppingFragment(sample: self.sample)
                    }
                    FeatureRow(title: "Cook together", detail: "Share a cookbook with the people you cook with.") {
                        SharedCookbookFragment()
                    }
                }
                .padding(Theme.Spacing.lg)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }

            VStack(spacing: Theme.Spacing.xs) {
                Button(action: self.onKeep) {
                    Text("Sign up to keep this recipe")
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .primaryButton()
                .accessibilityIdentifier("onboarding.keep")
                Button("Continue without it", action: self.onContinueWithoutRecipe)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.mcAccent)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .padding(.horizontal, Theme.Spacing.lg)
            .padding(.vertical, Theme.Spacing.sm)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
            .background(Color.mcCanvas)
        }
        .background(Color.mcCanvas)
    }
}

private struct FeatureRow<Fragment: View>: View {
    let title: LocalizedStringKey
    let detail: LocalizedStringKey
    @ViewBuilder let fragment: () -> Fragment

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = self.dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: Theme.Spacing.md))
            : AnyLayout(HStackLayout(alignment: .center, spacing: Theme.Spacing.md))
        layout {
            self.fragment()
                .dynamicTypeSize(...DynamicTypeSize.large)
                .frame(width: 116, height: 88)
                .background(Color.mcSunken, in: .rect(cornerRadius: Theme.Radius.card))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                Text(self.title)
                    .font(.headline)
                    .foregroundStyle(Color.mcInk)
                Text(self.detail)
                    .font(.subheadline)
                    .foregroundStyle(Color.mcBody)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(Theme.Spacing.sm)
        .background(Color.mcSurface, in: .rect(cornerRadius: Theme.Radius.panel))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.panel).stroke(Color.mcHairline, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}

/// Servings stepper that doubles once on appear, so the scaled amount is visible without explanation.
private struct PortionsFragment: View {
    let sample: DemoRecipe

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var servings: Int

    init(sample: DemoRecipe) {
        self.sample = sample
        self._servings = State(initialValue: sample.servings)
    }

    private var scaledIngredient: String {
        let scalable = self.sample.ingredients.filter { $0.amount != nil }
        guard let orzo = scalable.first(where: { $0.name == "orzo" }) ?? scalable.first else { return "" }
        let scale = Decimal(self.servings) / Decimal(max(self.sample.servings, 1))
        let quantity = IngredientFormatter.formatQuantity(
            amount: orzo.amount, amountMax: nil, unit: orzo.unit, scale: scale
        )
        return "\(quantity) \(orzo.name)"
    }

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: "minus")
                Text(self.servings, format: .number.grouping(.never))
                    .font(.mcMono(.subheadline, weight: .medium))
                    .contentTransition(.numericText())
                    .frame(minWidth: 16)
                Image(systemName: "plus")
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(Color.mcInk)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color.mcSurface, in: Capsule())
            .overlay(Capsule().stroke(Color.mcHairline, lineWidth: 1))
            Text(self.scaledIngredient)
                .font(.mcMono(.caption2, weight: .medium))
                .foregroundStyle(Color.mcBody)
                .contentTransition(.numericText())
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .padding(.horizontal, 6)
        .task {
            guard !self.reduceMotion else { return }
            try? await Task.sleep(for: .seconds(0.8))
            withAnimation(.smooth) { self.servings = self.sample.servings * 2 }
        }
    }
}

private struct MealPlanFragment: View {
    let sample: DemoRecipe

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(1 ... 3, id: \.self) { day in
                HStack(spacing: 6) {
                    Text(DateFormatter().shortWeekdaySymbols[day])
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(Color.mcMuted)
                        .frame(width: 24, alignment: .leading)
                    if day == 2 {
                        HStack(spacing: 4) {
                            DemoRecipePhoto(imageName: self.sample.imageName, height: 14)
                                .frame(width: 14)
                                .clipShape(.rect(cornerRadius: 3))
                            Text(verbatim: "Orzo")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(Color.mcInk)
                        }
                        .padding(.horizontal, 5)
                        .padding(.vertical, 3)
                        .background(Color.mcSurface, in: .rect(cornerRadius: 4))
                    } else {
                        Capsule()
                            .fill(Color.mcHairline)
                            .frame(width: 44, height: 5)
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(.horizontal, 10)
    }
}

private struct ShoppingFragment: View {
    let sample: DemoRecipe

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            let shortItems = self.sample.ingredients.filter { $0.name.count <= 12 }.prefix(3)
            ForEach(Array(shortItems.enumerated()), id: \.offset) { index, item in
                HStack(spacing: 5) {
                    Image(systemName: index == 0 ? "checkmark.circle.fill" : "circle")
                        .font(.system(size: 10))
                        .foregroundStyle(index == 0 ? Color.mcAccent : Color.mcMuted)
                    Text(item.name)
                        .font(.caption2)
                        .foregroundStyle(index == 0 ? Color.mcMuted : Color.mcInk)
                        .strikethrough(index == 0)
                        .lineLimit(1)
                }
            }
        }
        .padding(.horizontal, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct SharedCookbookFragment: View {
    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: -8) {
                ForEach(["SK", "AM"], id: \.self) { initials in
                    Text(initials)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Color.mcAccentDark)
                        .frame(width: 30, height: 30)
                        .background(Color.mcAccentTint, in: Circle())
                        .overlay(Circle().stroke(Color.mcSurface, lineWidth: 2))
                }
                Image(systemName: "plus")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Color.mcMuted)
                    .frame(width: 30, height: 30)
                    .background(Color.mcSurface, in: Circle())
                    .overlay(Circle().stroke(Color.mcHairline, style: StrokeStyle(lineWidth: 1, dash: [3])))
            }
            Label("Family cookbook", systemImage: "book.closed")
                .font(.caption2.weight(.medium))
                .foregroundStyle(Color.mcInk)
                .lineLimit(1)
        }
    }
}

#Preview {
    if let sample = try? DemoRecipe.load() {
        OnboardingFeaturesView(sample: sample, onKeep: {}, onContinueWithoutRecipe: {})
    }
}

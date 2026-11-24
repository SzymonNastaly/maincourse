import SwiftUI

/// Prep, cook and servings. A row of columns at standard text sizes, a
/// vertical list of label/value rows at the accessibility sizes.
struct RecipeFactsCard: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    let prepTime: Int?
    let cookTime: Int?
    let baseServings: Int?
    @Binding var servings: Int

    private var form: FactForm {
        self.dynamicTypeSize.isAccessibilitySize ? .row : .column
    }

    private var servingsLabel: String {
        guard let base = self.baseServings, self.servings != base else {
            return String(localized: "Servings")
        }
        let factor = Double(self.servings) / Double(max(base, 1))
        let scaleLabel = factor.formatted(.number.precision(.fractionLength(0 ... 2)))
        return String(localized: "Servings (×\(scaleLabel))")
    }

    var body: some View {
        let layout = self.form == .row
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 0))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 0))

        layout {
            if let prepTime, prepTime > 0 {
                FactItem(form: self.form, icon: "clock", label: Text("Prep")) {
                    Text(RecipeDisplayFormatter.minutes(prepTime))
                        .font(.mcMono(.subheadline, weight: .medium))
                        .foregroundColor(.mcInk)
                }
            }

            if let cookTime, cookTime > 0 {
                if (self.prepTime ?? 0) > 0 {
                    self.divider
                }
                FactItem(form: self.form, icon: "flame", label: Text("Cook")) {
                    Text(RecipeDisplayFormatter.minutes(cookTime))
                        .font(.mcMono(.subheadline, weight: .medium))
                        .foregroundColor(.mcInk)
                }
            }

            if self.baseServings != nil {
                if (self.prepTime ?? 0) > 0 || (self.cookTime ?? 0) > 0 {
                    self.divider
                }
                FactItem(form: self.form, icon: "person.2", label: Text(self.servingsLabel)) {
                    PortionScalerView(servings: self.$servings)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, self.form == .column ? Theme.Spacing.md : Theme.Spacing.xs)
        .background(Color.mcSunken)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.panel))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Radius.panel)
                .stroke(Color.mcHairline, lineWidth: 1)
        )
    }

    @ViewBuilder
    private var divider: some View {
        switch self.form {
        case .column:
            Divider().frame(maxHeight: 80).padding(.top, Theme.Spacing.sm)
        case .row:
            Divider()
        }
    }
}

private enum FactForm {
    case column
    case row
}

private struct FactItem<Value: View>: View {
    @ScaledMetric(relativeTo: .body) private var iconHeight: CGFloat = 24

    let form: FactForm
    let icon: String
    let label: Text
    @ViewBuilder let value: Value

    var body: some View {
        switch self.form {
        case .column:
            VStack(spacing: Theme.Spacing.xs) {
                Image(systemName: self.icon)
                    .font(.body)
                    .foregroundStyle(Color.mcMuted)
                    .frame(height: self.iconHeight)
                    .accessibilityHidden(true)

                self.value
                    .frame(minHeight: 44)

                self.label
                    .font(.caption)
                    .foregroundColor(.mcBody)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Theme.Spacing.xs)
        case .row:
            let label = self.label
                .font(.subheadline)
                .foregroundColor(.mcBody)

            ViewThatFits(in: .horizontal) {
                HStack(spacing: Theme.Spacing.sm) {
                    label.frame(maxWidth: .infinity, alignment: .leading)
                    self.value
                }
                VStack(alignment: .leading, spacing: 0) {
                    label
                    self.value
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, Theme.Spacing.md)
            .frame(minHeight: 44)
        }
    }
}

#Preview("Facts") {
    @Previewable @State var servings = 6
    RecipeFactsCard(prepTime: 10, cookTime: 20, baseServings: 4, servings: $servings)
        .padding()
}

#Preview("Facts, accessibility size") {
    @Previewable @State var servings = 4
    RecipeFactsCard(prepTime: 10, cookTime: 20, baseServings: 4, servings: $servings)
        .padding()
        .environment(\.dynamicTypeSize, .accessibility5)
}

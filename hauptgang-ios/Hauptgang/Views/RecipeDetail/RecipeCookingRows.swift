import SwiftUI

/// An ingredient the cook can tick off. Parsed rows show the scaled quantity
/// in Plex Mono; unparsed rows fall back to the raw string.
struct IngredientRow: View {
    @ScaledMetric(relativeTo: .body) private var bulletSize: CGFloat = 6

    let ingredient: StructuredIngredient
    let scale: Decimal
    let isChecked: Bool
    let onToggle: () -> Void

    private var textColor: Color {
        self.isChecked ? .mcBody : .mcInk
    }

    var body: some View {
        Button(action: self.onToggle) {
            HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.sm) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.body)
                    .hidden()
                    .overlay {
                        if self.isChecked {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.body)
                                .foregroundStyle(Color.mcAccent)
                        } else {
                            Circle()
                                .fill(Color.mcMuted)
                                .frame(width: self.bulletSize, height: self.bulletSize)
                        }
                    }
                    .accessibilityHidden(true)

                self.text
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 3)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityValue(self.isChecked ? Text("Checked") : Text(verbatim: ""))
    }

    private var text: Text {
        guard self.ingredient.hasStructuredFields else {
            return Text(self.ingredient.raw)
                .font(.body)
                .foregroundColor(self.textColor)
                .strikethrough(self.isChecked)
        }

        let quantityText = IngredientFormatter.formatQuantity(
            amount: self.ingredient.amount,
            amountMax: self.ingredient.amountMax,
            unit: self.ingredient.unit,
            scale: self.scale
        )

        return Text(verbatim: quantityText.isEmpty ? "" : "\(quantityText) ")
            .font(.mcMono(.body, weight: .medium))
            .foregroundColor(self.textColor)
            .strikethrough(self.isChecked)
            + Text(self.ingredient.name ?? self.ingredient.raw)
            .font(.body)
            .foregroundColor(self.textColor)
            .strikethrough(self.isChecked)
            + Text(verbatim: self.ingredient.note.map { ", \($0)" } ?? "")
            .font(.body)
            .foregroundColor(.mcBody)
            .strikethrough(self.isChecked)
    }
}

/// A numbered step the cook can mark done. Done steps are not struck
/// through, because a cook rereads them.
struct StepRow: View {
    @ScaledMetric(relativeTo: .body) private var badgeSize: CGFloat = 24

    let number: Int
    let instruction: String
    let isDone: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: self.onToggle) {
            HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.md) {
                Text(self.number, format: .number.grouping(.never))
                    .font(.mcMono(.caption, weight: .medium))
                    .foregroundColor(Color.mcAccent)
                    .opacity(self.isDone ? 0 : 1)
                    .frame(width: self.badgeSize, height: self.badgeSize)
                    .background(
                        self.isDone ? Color.mcAccent : Color.mcAccentTint,
                        in: RoundedRectangle(cornerRadius: 6)
                    )
                    .overlay {
                        if self.isDone {
                            Image(systemName: "checkmark")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(Color.mcSurface)
                        }
                    }
                    .accessibilityHidden(true)

                Text(self.instruction)
                    .font(.body)
                    .foregroundColor(self.isDone ? .mcBody : .mcInk)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, Theme.Spacing.xs)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Step \(self.number): \(self.instruction)"))
        .accessibilityValue(self.isDone ? Text("Done") : Text(verbatim: ""))
    }
}

import SwiftUI

/// The − servings + control that rescales a recipe's quantities.
///
/// Owns no state itself: the parent passes a `Binding<Int>` so the chosen
/// value resets when the parent goes away.
struct PortionScalerView: View {
    @Binding var servings: Int

    private let minServings = 1
    private let maxServings = 64

    var body: some View {
        HStack(spacing: Theme.Spacing.xs) {
            self.stepButton(systemImage: "minus", label: "Decrease servings", delta: -1)
                .disabled(self.servings <= self.minServings)

            Text(self.servings, format: .number.grouping(.never))
                .font(.mcMono(.headline, weight: .medium))
                .foregroundColor(.mcInk)
                .monospacedDigit()
                .frame(minWidth: 24)

            self.stepButton(systemImage: "plus", label: "Increase servings", delta: 1)
                .disabled(self.servings >= self.maxServings)
        }
    }

    private func stepButton(systemImage: String, label: LocalizedStringKey, delta: Int) -> some View {
        Button {
            self.servings = min(max(self.servings + delta, self.minServings), self.maxServings)
        } label: {
            Image(systemName: systemImage)
                .font(.body.weight(.semibold))
                .frame(minWidth: 44, minHeight: 32)
                // Keeps a 44-point tall hit area without making the facts card taller.
                .contentShape(Rectangle().inset(by: -6))
        }
        .buttonStyle(.borderless)
        .tint(Color.mcAccent)
        .accessibilityLabel(label)
    }
}

import SwiftUI

/// Compact stepper that lets the user rescale a recipe's quantities.
///
/// Owns no state itself — receives a `Binding<Int>` from the parent view so
/// the chosen value resets when the parent goes away. The parent computes
/// `scale = Double(servings) / Double(baseServings)` and passes it down
/// to ingredient rows.
struct PortionScalerView: View {
    @Binding var servings: Int
    let baseServings: Int

    private let minServings = 1
    private let maxServings = 64

    var body: some View {
        VStack(spacing: Theme.Spacing.xs) {
            Image(systemName: "person.2")
                .font(.system(size: 18))
                .foregroundColor(.mcAccent)
                .frame(height: 24)

            HStack(spacing: Theme.Spacing.sm) {
                Button {
                    if self.servings > self.minServings {
                        self.servings -= 1
                    }
                } label: {
                    Image(systemName: "minus")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.borderless)
                .tint(Color.mcAccent)
                .disabled(self.servings <= self.minServings)

                Text("\(self.servings)")
                    .font(.headline)
                    .foregroundColor(.mcInk)
                    .monospacedDigit()
                    .frame(minWidth: 24)

                Button {
                    if self.servings < self.maxServings {
                        self.servings += 1
                    }
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.borderless)
                .tint(Color.mcAccent)
                .disabled(self.servings >= self.maxServings)
            }
            .frame(height: 28)

            Text(self.servings == self.baseServings ? "Servings" : "Servings (×\(self.scaleLabel))")
                .font(.caption)
                .foregroundColor(.mcBody)
                .frame(height: 18)
        }
        .frame(maxWidth: .infinity)
    }

    private var scaleLabel: String {
        let factor = Double(self.servings) / Double(max(self.baseServings, 1))
        if factor.rounded() == factor {
            return String(Int(factor))
        }
        return String(format: "%.2f", factor).trimmingTrailingZeros()
    }
}

private extension String {
    func trimmingTrailingZeros() -> String {
        guard self.contains(".") else { return self }
        var result = self
        while result.hasSuffix("0") {
            result.removeLast()
        }
        if result.hasSuffix(".") {
            result.removeLast()
        }
        return result
    }
}

import SwiftUI

/// Tappable chip used by every onboarding question. Visually swaps between selected
/// (ink fill, white text) and unselected (surface with hairline border) states.
struct OnboardingChip: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: self.action) {
            Text(self.label)
                .font(.body.weight(.medium))
                .foregroundColor(self.isSelected ? .white : .mcInk)
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.vertical, Theme.Spacing.sm + 2)
                .background(
                    RoundedRectangle(cornerRadius: Theme.Radius.control)
                        .fill(self.isSelected ? Color.mcInk : Color.mcSurface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: Theme.Radius.control)
                        .stroke(
                            self.isSelected ? Color.clear : Color.mcHairline,
                            lineWidth: 1
                        )
                )
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.15), value: self.isSelected)
    }
}

/// Shared header used at the top of every onboarding question screen.
struct OnboardingQuestionHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            Text(self.title)
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.mcInk)
                .fixedSize(horizontal: false, vertical: true)

            Text(self.subtitle)
                .font(.subheadline)
                .foregroundColor(.mcBody)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

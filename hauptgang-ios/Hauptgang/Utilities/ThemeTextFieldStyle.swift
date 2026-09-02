import SwiftUI

struct ThemeTextFieldModifier: ViewModifier {
    var isError: Bool = false
    var isGrouped: Bool = false

    func body(content: Content) -> some View {
        content
            .textFieldStyle(.plain)
            .padding(.horizontal, Theme.Spacing.md)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 52, alignment: .center)
            .background(self.isError ? Color.mcDanger
                .opacity(0.1) : (self.isGrouped ? Color.clear : Color.mcSurface))
            .clipShape(.rect(cornerRadius: self.isGrouped ? 0 : Theme.Radius.card))
            .contentShape(.rect(cornerRadius: self.isGrouped ? 0 : Theme.Radius.card))
    }
}

// MARK: - Primary Button Style

struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(Theme.Spacing.md)
            .background(
                self.isEnabled
                    ? (configuration.isPressed ? Color.mcAccentDark : Color.mcAccent)
                    : Color.mcMuted
            )
            .cornerRadius(Theme.Radius.card)
            .animation(.easeInOut(duration: 0.15), value: configuration.isPressed)
    }
}

// MARK: - Puffy Button Style

struct PuffyButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed
        configuration.label
            .scaleEffect(pressed ? 0.90 : 1.0)
            .brightness(pressed ? -0.12 : 0)
            .shadow(
                color: Color.black.opacity(pressed ? 0.05 : 0.15),
                radius: pressed ? 1 : 3,
                y: pressed ? 1 : 2
            )
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Radius.card)
                    .fill(Color.black.opacity(pressed ? 0.15 : 0))
            )
            .animation(.easeInOut(duration: 0.15), value: pressed)
    }
}

// MARK: - View Extension

extension View {
    func themeTextField(isError: Bool = false, isGrouped: Bool = false) -> some View {
        self.modifier(ThemeTextFieldModifier(isError: isError, isGrouped: isGrouped))
    }

    func primaryButton() -> some View {
        self.buttonStyle(PrimaryButtonStyle())
    }

    func puffyButton() -> some View {
        self.buttonStyle(PuffyButtonStyle())
    }
}

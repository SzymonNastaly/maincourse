import SwiftUI

// MARK: - Text fields

/// Standalone fields sit on a surface card with a hairline border; grouped
/// fields (stacked inside a shared card) draw no background of their own.
struct ThemeTextFieldModifier: ViewModifier {
    var isError: Bool = false
    var isGrouped: Bool = false

    func body(content: Content) -> some View {
        let radius = self.isGrouped ? 0 : Theme.Radius.card
        return content
            .textFieldStyle(.plain)
            .padding(.horizontal, Theme.Spacing.md)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 52, alignment: .center)
            .background(self.fillColor)
            .clipShape(.rect(cornerRadius: radius))
            .overlay {
                if !self.isGrouped {
                    RoundedRectangle(cornerRadius: radius)
                        .stroke(self.isError ? Color.mcDangerLine : Color.mcHairline, lineWidth: 1)
                }
            }
            .contentShape(.rect(cornerRadius: radius))
    }

    private var fillColor: Color {
        if self.isError {
            return Color.mcDangerTint
        }
        return self.isGrouped ? Color.clear : Color.mcSurface
    }
}

// MARK: - Buttons

/// Solid accent button: the single primary action on a screen.
struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(Theme.Spacing.md)
            .background(self.fill(isPressed: configuration.isPressed))
            .clipShape(.rect(cornerRadius: Theme.Radius.card))
            .animation(.easeInOut(duration: 0.15), value: configuration.isPressed)
    }

    private func fill(isPressed: Bool) -> Color {
        guard self.isEnabled else {
            return Color.mcAccent.opacity(0.4)
        }
        return isPressed ? Color.mcAccentDark : Color.mcAccent
    }
}

/// Surface button with a hairline border: secondary actions next to a primary one.
struct OutlineButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundColor(Color.mcInk)
            .frame(maxWidth: .infinity)
            .padding(Theme.Spacing.md)
            .background(configuration.isPressed ? Color.mcSunken : Color.mcSurface)
            .clipShape(.rect(cornerRadius: Theme.Radius.card))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.card)
                    .stroke(Color.mcHairline, lineWidth: 1)
            }
            .opacity(self.isEnabled ? 1 : 0.4)
            .animation(.easeInOut(duration: 0.15), value: configuration.isPressed)
    }
}

// MARK: - View helpers

extension View {
    func themeTextField(isError: Bool = false, isGrouped: Bool = false) -> some View {
        self.modifier(ThemeTextFieldModifier(isError: isError, isGrouped: isGrouped))
    }

    func primaryButton() -> some View {
        self.buttonStyle(PrimaryButtonStyle())
    }

    func outlineButton() -> some View {
        self.buttonStyle(OutlineButtonStyle())
    }
}

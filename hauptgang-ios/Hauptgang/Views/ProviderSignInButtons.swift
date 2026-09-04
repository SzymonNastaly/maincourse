import SwiftUI

/// Apple and Google sign-in buttons drawn to one MainCourse spec — same height,
/// radius, label size and mark size, so the pair reads as a single stack.
/// The SDK controls (`SignInWithAppleButton`, `GoogleSignInButton`) each bring
/// their own type scale, shadow and mark placement and never line up.
struct ContinueWithAppleButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: self.action) {
            ProviderButtonLabel(title: "Continue with Apple") {
                Image(systemName: "apple.logo")
                    .font(.body)
            }
        }
        .buttonStyle(ProviderButtonStyle(
            foreground: Color.white,
            background: Color.black,
            pressedBackground: Color(white: 0.2),
            border: nil
        ))
    }
}

struct ContinueWithGoogleButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: self.action) {
            ProviderButtonLabel(title: "Continue with Google") {
                Image("GoogleG")
                    .resizable()
                    .scaledToFit()
            }
        }
        .buttonStyle(ProviderButtonStyle(
            foreground: Color.mcInk,
            background: Color.mcSurface,
            pressedBackground: Color.mcSunken,
            border: Color.mcHairline
        ))
    }
}

// MARK: - Shared chrome

private struct ProviderButtonLabel<Mark: View>: View {
    let title: String
    @ViewBuilder let mark: Mark
    @ScaledMetric(relativeTo: .body) private var markSize: CGFloat = 18

    var body: some View {
        HStack(spacing: Theme.Spacing.sm) {
            self.mark
                .frame(width: self.markSize, height: self.markSize)
                .accessibilityHidden(true)
            Text(self.title)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct ProviderButtonStyle: ButtonStyle {
    let foreground: Color
    let background: Color
    let pressedBackground: Color
    let border: Color?

    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.medium))
            .foregroundStyle(self.foreground)
            .padding(.vertical, 10)
            .frame(minHeight: 44)
            .background(configuration.isPressed ? self.pressedBackground : self.background)
            .clipShape(.rect(cornerRadius: Theme.Radius.card))
            .overlay {
                if let border = self.border {
                    RoundedRectangle(cornerRadius: Theme.Radius.card)
                        .stroke(border, lineWidth: 1)
                }
            }
            .opacity(self.isEnabled ? 1 : 0.4)
            .animation(.easeInOut(duration: 0.15), value: configuration.isPressed)
    }
}

#Preview {
    VStack(spacing: Theme.Spacing.sm) {
        ContinueWithAppleButton {}
        ContinueWithGoogleButton {}
        ContinueWithGoogleButton {}
            .disabled(true)
    }
    .padding()
    .background(Color.mcCanvas)
}

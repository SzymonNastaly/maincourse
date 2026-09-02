import SwiftUI

/// First screen of the onboarding flow.
///
/// Brand-forward intro with the app logo, the same tagline used on `LoginView`,
/// and a single "Get started" CTA. Elements stagger in on appear so the screen
/// feels like it arrives, rather than snapping in.
struct OnboardingWelcomeView: View {
    let onStart: () -> Void

    @State private var logoVisible = false
    @State private var taglineVisible = false
    @State private var ctaVisible = false

    var body: some View {
        VStack(spacing: Theme.Spacing.xl) {
            Spacer()

            VStack(spacing: Theme.Spacing.lg) {
                Image("LoginLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 96, height: 96)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.panel))
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.Radius.panel)
                            .stroke(Color.mcHairline, lineWidth: 1)
                    )
                    .opacity(self.logoVisible ? 1 : 0)
                    .scaleEffect(self.logoVisible ? 1 : 0.92)
                    .offset(y: self.logoVisible ? 0 : 8)

                (Text("Cook something ")
                    .foregroundColor(.mcInk)
                    + Text("delicious")
                    .foregroundColor(.mcAccent)
                    + Text(" today")
                    .foregroundColor(.mcInk))
                    .font(.title2)
                    .fontWeight(.semibold)
                    .multilineTextAlignment(.center)
                    .opacity(self.taglineVisible ? 1 : 0)
                    .offset(y: self.taglineVisible ? 0 : 8)
            }

            Spacer()

            Button(action: self.onStart) {
                Text("Get started")
            }
            .primaryButton()
            .padding(.bottom, Theme.Spacing.md)
            .opacity(self.ctaVisible ? 1 : 0)
            .offset(y: self.ctaVisible ? 0 : 8)
        }
        .padding(.horizontal, Theme.Spacing.lg)
        .onAppear(perform: self.animateIn)
    }

    private func animateIn() {
        withAnimation(.easeOut(duration: 0.5)) {
            self.logoVisible = true
        }
        withAnimation(.easeOut(duration: 0.5).delay(0.15)) {
            self.taglineVisible = true
        }
        withAnimation(.easeOut(duration: 0.5).delay(0.30)) {
            self.ctaVisible = true
        }
    }
}

#Preview {
    ZStack {
        Color.mcCanvas.ignoresSafeArea()
        OnboardingWelcomeView(onStart: {})
    }
}

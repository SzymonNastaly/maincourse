import SwiftUI

/// First-run entry: a playable save example, followed by the existing authentication UI.
struct OnboardingFlowView: View {
    @Environment(OnboardingCoordinator.self) private var onboarding
    @State private var showingAuth = OnboardingService.hasReachedAuthenticationStep()
    @State private var sample = try? DemoRecipe.load()
    let onFinished: () -> Void

    var body: some View {
        NavigationStack {
            Group {
                if self.showingAuth {
                    LoginView(isEmbeddedInOnboarding: true, startsInSignUpMode: true) {
                        self.onboarding.finishIntroduction()
                    }
                    .padding(.horizontal, Theme.Spacing.lg)
                } else if let sample = self.sample {
                    ImportDemoView(sample: sample) { key in
                        self.onboarding.keep(sampleKey: key)
                        self.showingAuth = true
                    }
                } else {
                    ContentUnavailableView("Ready to save something delicious?", systemImage: "fork.knife")
                }
            }
            .background(Color.mcCanvas)
            .navigationTitle(self.showingAuth ? "Keep it in your cookbook" : "Interactive example")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if self.showingAuth {
                        Button("Back") { self.showingAuth = false }
                    } else {
                        Button("Sign in", action: self.finish)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Skip", action: self.finish)
                        .accessibilityIdentifier("onboarding.skip")
                }
            }
        }
        .tint(Color.mcAccent)
    }

    private func finish() {
        self.onboarding.continueWithoutSaving()
        self.onboarding.finishIntroduction()
        self.onFinished()
    }
}

#Preview {
    OnboardingFlowView(onFinished: {})
        .environment(OnboardingCoordinator())
}

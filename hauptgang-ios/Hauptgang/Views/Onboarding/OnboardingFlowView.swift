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
                    ImportDemoView(
                        sample: sample,
                        keepLabel: "Sign up",
                        onRecipeReady: self.onboarding.completeExample
                    ) { key in
                        self.onboarding.keep(sampleKey: key)
                        self.showingAuth = true
                    }
                } else {
                    ContentUnavailableView("Ready to save something delicious?", systemImage: "fork.knife")
                }
            }
            .background(Color.mcCanvas)
            .navigationTitle(self.showingAuth && self.onboarding.pending != nil ? "Keep it in your cookbook" : "")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if self.showingAuth {
                        Button("Back") { self.showingAuth = false }
                    } else {
                        Button("Log in", action: self.finish)
                            .accessibilityIdentifier("onboarding.log-in")
                    }
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

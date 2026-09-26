import SwiftUI

/// First-run entry: welcome, the playable save example, what a saved recipe unlocks,
/// then the existing authentication UI.
struct OnboardingFlowView: View {
    private enum Step { case welcome, demo, features, auth }

    @Environment(OnboardingCoordinator.self) private var onboarding
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var step: Step = OnboardingService.hasReachedAuthenticationStep() ? .auth : .welcome
    @State private var demoFinished = false
    @State private var sample = try? DemoRecipe.load()
    let onFinished: () -> Void

    var body: some View {
        NavigationStack {
            ZStack {
                self.stepContent
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.mcCanvas)
            .navigationTitle(self.step == .auth && self.onboarding.pending != nil ? "Keep it in your cookbook" : "")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(self.step == .welcome ? .hidden : .visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if let previous = self.previousStep {
                        Button("Back") { self.go(to: previous) }
                    }
                }
                if self.step == .demo {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Log in", action: self.finish)
                    }
                }
            }
        }
        .tint(Color.mcAccent)
    }

    @ViewBuilder
    private var stepContent: some View {
        switch self.step {
        case .welcome:
            OnboardingWelcomeView(
                sample: self.sample,
                onGetStarted: { self.go(to: self.sample == nil ? .auth : .demo) },
                onLogIn: self.finish
            )
            .transition(self.stepTransition)
        case .demo:
            if let sample = self.sample {
                ImportDemoView(
                    sample: sample,
                    keepLabel: "Continue",
                    startsWithRecipe: self.demoFinished,
                    onRecipeReady: {
                        self.demoFinished = true
                        self.onboarding.completeExample()
                    },
                    onKeep: { _ in self.go(to: .features) }
                )
                .transition(self.stepTransition)
            }
        case .features:
            if let sample = self.sample {
                OnboardingFeaturesView(
                    sample: sample,
                    onKeep: {
                        self.onboarding.keep(sampleKey: sample.key)
                        self.go(to: .auth)
                    },
                    onContinueWithoutRecipe: {
                        self.onboarding.continueWithoutSaving()
                        self.go(to: .auth)
                    }
                )
                .transition(self.stepTransition)
            }
        case .auth:
            LoginView(isEmbeddedInOnboarding: true, startsInSignUpMode: true) {
                self.onboarding.finishIntroduction()
            }
            .padding(.horizontal, Theme.Spacing.lg)
            .transition(self.stepTransition)
        }
    }

    private var previousStep: Step? {
        switch self.step {
        case .welcome: nil
        case .demo: .welcome
        case .features: .demo
        case .auth: self.sample == nil ? .welcome : self.demoFinished ? .features : .demo
        }
    }

    private var stepTransition: AnyTransition {
        self.reduceMotion ? .opacity : .opacity.combined(with: .offset(y: 16))
    }

    private func go(to step: Step) {
        withAnimation(self.reduceMotion ? .easeOut(duration: 0.15) : .smooth(duration: 0.4)) {
            self.step = step
        }
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

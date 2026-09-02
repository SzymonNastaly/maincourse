import SwiftUI

/// Top-level container for the pre-signup onboarding flow.
///
/// Renders the welcome screen, onboarding questions, and the embedded auth screen for
/// first-run users. Once onboarding has been completed on a previous launch, `RootView`
/// skips this flow and presents the standalone login/signup screen instead.
struct OnboardingFlowView: View {
    @StateObject private var viewModel = OnboardingViewModel()
    @State private var showWelcome: Bool

    /// Called when the user exits onboarding without authenticating, so the parent can
    /// swap to the standalone auth screen.
    let onFinished: () -> Void

    init(onFinished: @escaping () -> Void) {
        self.onFinished = onFinished
        self._showWelcome = State(initialValue: !OnboardingService.hasReachedAuthenticationStep())
    }

    var body: some View {
        ZStack {
            Color.mcCanvas.ignoresSafeArea()

            if self.showWelcome, self.viewModel.step.isQuestion {
                OnboardingWelcomeView {
                    withAnimation(.easeInOut(duration: 0.35)) {
                        self.showWelcome = false
                    }
                }
                .transition(.opacity)
            } else {
                VStack(spacing: 0) {
                    self.header
                    self.content
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    self.footer
                }
                .padding(.horizontal, Theme.Spacing.lg)
                .transition(.opacity)
            }
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack {
            if self.viewModel.step != .household {
                Button {
                    self.viewModel.goBack()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.mcBody)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
            } else {
                Color.clear.frame(width: 44, height: 44)
            }

            Spacer()

            if self.viewModel.step.isQuestion {
                self.progressDots
            } else {
                Text("Account")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.mcBody)
            }

            Spacer()

            if self.viewModel.step.isQuestion {
                Button("Skip") {
                    self.viewModel.skip()
                    self.onFinished()
                }
                .font(.subheadline)
                .foregroundColor(.mcBody)
                .frame(minWidth: 44, minHeight: 44)
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
        }
        .padding(.top, Theme.Spacing.sm)
    }

    private var progressDots: some View {
        HStack(spacing: Theme.Spacing.xs) {
            ForEach(0 ..< OnboardingViewModel.Step.questionCount, id: \.self) { index in
                Capsule()
                    .fill(
                        index <= self.viewModel.step.progressIndex
                            ? Color.mcAccent
                            : Color.mcHairline
                    )
                    .frame(width: index == self.viewModel.step.progressIndex ? 24 : 8, height: 6)
                    .animation(.easeInOut(duration: 0.25), value: self.viewModel.step)
            }
        }
    }

    // MARK: - Content

    @ViewBuilder
    private var content: some View {
        switch self.viewModel.step {
        case .household:
            HouseholdQuestionView(selection: self.$viewModel.householdSize)
        case .saveToday:
            SaveTodayQuestionView(selections: self.$viewModel.saveTodaySelections)
        case .diet:
            DietQuestionView(selections: self.$viewModel.dietSelections)
        case .auth:
            LoginView(
                isEmbeddedInOnboarding: true,
                startsInSignUpMode: true,
                onAuthenticated: self.handleAuthenticated
            )
        }
    }

    // MARK: - Footer

    @ViewBuilder
    private var footer: some View {
        if self.viewModel.step.isQuestion {
            Button(action: self.handleAdvance) {
                HStack {
                    Text(self.advanceLabel)
                    Image(systemName: "arrow.right")
                        .font(.system(size: 14, weight: .semibold))
                }
            }
            .primaryButton()
            .puffyButton()
            .disabled(!self.viewModel.canAdvance)
            .opacity(self.viewModel.canAdvance ? 1.0 : 0.5)
            .padding(.bottom, Theme.Spacing.md)
        }
    }

    private var advanceLabel: String {
        switch self.viewModel.step {
        case .diet: "Continue to sign up"
        default: "Continue"
        }
    }

    private func handleAdvance() {
        guard self.viewModel.canAdvance else { return }
        self.viewModel.advance()
    }

    private func handleAuthenticated() {
        self.viewModel.markCompleted()
    }
}

#Preview {
    OnboardingFlowView(onFinished: {})
}

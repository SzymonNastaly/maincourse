import SwiftUI

/// First screen of the introduction: what MainCourse is for, before the playable example.
struct OnboardingWelcomeView: View {
    let sample: DemoRecipe?
    let onGetStarted: () -> Void
    let onLogIn: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private static let subtitle: LocalizedStringKey = """
    Saved posts, screenshots, cookbook pages — \
    MainCourse turns them into recipes you actually cook from.
    """

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: Theme.Spacing.lg) {
                    HStack(spacing: Theme.Spacing.sm) {
                        Image("LoginLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 32, height: 32)
                            .accessibilityHidden(true)
                        Text(verbatim: "MainCourse")
                            .font(.headline)
                            .foregroundStyle(Color.mcInk)
                    }
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .accessibilityElement(children: .combine)

                    Spacer(minLength: 0)

                    if let sample = self.sample, !self.dynamicTypeSize.isAccessibilitySize {
                        RecipeSourcesAnimation(sample: sample)
                    }

                    Spacer(minLength: 0)

                    VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
                        Text("All your recipes, ready to cook.")
                            .font(.largeTitle.bold())
                            .foregroundStyle(Color.mcInk)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                        Text(Self.subtitle)
                            .font(.body)
                            .foregroundStyle(Color.mcBody)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(.horizontal, Theme.Spacing.lg)
                .padding(.vertical, Theme.Spacing.md)
                .frame(maxWidth: 560, minHeight: geometry.size.height)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: Theme.Spacing.xs) {
                Button("Get started", action: self.onGetStarted)
                    .primaryButton()
                    .accessibilityIdentifier("onboarding.get-started")
                Button("I already have an account", action: self.onLogIn)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.mcAccent)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .accessibilityIdentifier("onboarding.log-in")
            }
            .padding(.horizontal, Theme.Spacing.lg)
            .padding(.vertical, Theme.Spacing.sm)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
            .background(Color.mcCanvas)
        }
        .background(Color.mcCanvas)
    }
}

#Preview {
    OnboardingWelcomeView(sample: try? DemoRecipe.load(), onGetStarted: {}, onLogIn: {})
}

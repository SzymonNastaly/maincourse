import SwiftUI

struct OnboardingSaveView: View {
    @Environment(OnboardingCoordinator.self) private var onboarding
    let onRetry: () -> Void
    @State private var sample = try? DemoRecipe.load()

    var body: some View {
        VStack(spacing: 0) {
            if let sample = self.sample {
                ImportDemoView(sample: sample, startsWithRecipe: true, showsKeepButton: false, onKeep: { _ in })
            }
            VStack(spacing: 12) {
                switch self.onboarding.state {
                case .idle, .saving:
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("Saving to My Recipes…")
                            .font(.headline)
                    }
                case let .failed(message, canRetry):
                    Text("Your preview is still here")
                        .font(.headline)
                    Text(message)
                        .font(.subheadline)
                        .foregroundStyle(Color.mcBody)
                        .multilineTextAlignment(.center)
                    if canRetry {
                        Button("Try saving again", action: self.onRetry)
                            .primaryButton()
                    }
                }
                Button("Continue without saving") { self.onboarding.continueWithoutSaving() }
                    .font(.subheadline)
                    .frame(minHeight: 44)
            }
            .padding(20)
            .frame(maxWidth: .infinity)
            .background(Color.mcSurface)
        }
        .background(Color.mcCanvas)
        .tint(Color.mcAccent)
    }
}

struct AuthenticatedImportDemoView: View {
    @Environment(OnboardingCoordinator.self) private var onboarding
    @Environment(\.dismiss) private var dismiss
    @State private var sample = try? DemoRecipe.load()

    var body: some View {
        NavigationStack {
            Group {
                if let sample = self.sample {
                    ImportDemoView(sample: sample, keepLabel: "Keep in My Recipes") { key in
                        self.onboarding.keep(sampleKey: key)
                        self.dismiss()
                    }
                } else {
                    ContentUnavailableView("Example unavailable", systemImage: "fork.knife")
                }
            }
            .navigationTitle("Interactive example")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { Button("Done") { self.dismiss() } }
            }
        }
        .tint(Color.mcAccent)
    }
}

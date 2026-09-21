import SwiftUI

struct ImportDemoView: View {
    enum Stage: Equatable { case post, sharing, destinations, processing, recipe }

    let sample: DemoRecipe
    var keepLabel = "Keep this recipe"
    var startsWithRecipe = false
    var showsKeepButton = true
    var onRecipeReady: () -> Void = {}
    let onKeep: (String) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Namespace private var photoNamespace
    @State private var stage: Stage = .post
    @State private var currentServings: Int?
    @AccessibilityFocusState private var recipeFocused: Bool

    var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                if self.stage == .recipe {
                    self.recipePreview.transition(.opacity)
                } else if self.stage == .processing {
                    self.processingView.transition(.opacity)
                } else {
                    self.post.transition(.opacity)
                }
            }
            .accessibilityHidden(self.stage == .sharing || self.stage == .destinations)

            if self.stage == .sharing || self.stage == .destinations {
                Color.black.opacity(0.2)
                    .ignoresSafeArea()
                    .onTapGesture { self.changeStage(.post) }
                    .accessibilityHidden(true)
                DemoSharePanel(
                    sample: self.sample,
                    showsDestinations: self.stage == .destinations,
                    onBack: { self.changeStage(self.stage == .destinations ? .sharing : .post) },
                    onShareTo: { self.changeStage(.destinations) },
                    onMainCourse: { self.changeStage(.processing) }
                )
                .transition(.move(edge: .bottom)
                    .combined(with: .opacity))
            }
        }
        .background(Color.mcCanvas)
        .onAppear {
            if self.startsWithRecipe {
                self.stage = .recipe
            }
        }
        .task(id: self.stage) {
            guard self.stage == .processing else { return }
            try? await Task.sleep(for: .seconds(1.5))
            guard !Task.isCancelled, self.stage == .processing else { return }
            self.revealRecipe()
        }
    }

    private var processingView: some View {
        VStack(spacing: Theme.Spacing.md) {
            ProgressView()
                .tint(Color.mcAccent)
                .scaleEffect(1.5)
            Text("Processing recipe…")
                .font(.headline)
                .foregroundStyle(Color.mcBody)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
    }

    private var post: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Try it: share this post to MainCourse.")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Color.mcInk)
                    .fixedSize(horizontal: false, vertical: true)
                DemoSocialPostView(sample: self.sample, isActive: self.stage == .post, photo: {
                    self.photo(height: 260)
                }, onShare: { self.changeStage(.sharing) })
                Text("Works with Instagram, websites, and more.")
                    .font(.footnote)
                    .foregroundStyle(Color.mcMuted)
                    .frame(maxWidth: .infinity)
            }
            .padding(20)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
    }

    private var recipePreview: some View {
        VStack(spacing: 0) {
            RecipeDetailContentView(
                recipe: self.sample.recipeDetail, heroImageHeight: 210, isIOS26: false,
                currentServings: self.$currentServings,
                localHero: AnyView(self.photo(height: 210))
            )
            .accessibilityFocused(self.$recipeFocused)
            if self.showsKeepButton {
                Button {
                    self.onKeep(self.sample.key)
                } label: {
                    Text(self.keepLabel)
                        .multilineTextAlignment(.center)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .primaryButton()
                .accessibilityIdentifier("demo.keep")
                .padding(16)
                .background(Color.mcSurface)
            }
        }
        .frame(maxWidth: 680)
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func photo(height: CGFloat) -> some View {
        if self.reduceMotion {
            DemoRecipePhoto(imageName: self.sample.imageName, height: height)
        } else {
            DemoRecipePhoto(imageName: self.sample.imageName, height: height)
                .matchedGeometryEffect(id: "recipe-photo", in: self.photoNamespace)
        }
    }

    private func changeStage(_ stage: Stage) {
        withAnimation(self.reduceMotion ? .easeOut(duration: 0.15) : .smooth(duration: 0.35)) {
            self.stage = stage
        }
    }

    private func revealRecipe() {
        guard self.stage == .processing else { return }
        withAnimation(self.reduceMotion ? .easeOut(duration: 0.15) : .smooth(duration: 0.8)) {
            self.stage = .recipe
        } completion: {
            self.recipeFocused = true
        }
        self.onRecipeReady()
    }
}

#Preview {
    if let sample = try? DemoRecipe.load() {
        ImportDemoView(sample: sample, onKeep: { _ in })
    }
}

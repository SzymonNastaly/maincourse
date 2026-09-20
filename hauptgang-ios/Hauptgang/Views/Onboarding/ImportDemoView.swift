import SwiftUI

struct ImportDemoView: View {
    enum Stage: Equatable { case post, sharing, destinations, recipe }

    let sample: DemoRecipe
    var keepLabel = "Keep this recipe"
    var startsWithRecipe = false
    var showsKeepButton = true
    let onKeep: (String) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Namespace private var photoNamespace
    @State private var stage: Stage = .post
    @State private var currentServings: Int?
    @State private var cookingMode = false
    @AccessibilityFocusState private var recipeFocused: Bool

    var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                if self.stage == .recipe {
                    self.recipePreview.transition(.opacity)
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
                    onMainCourse: self.revealRecipe
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
        .onDisappear {
            if self.cookingMode {
                UIApplication.shared.isIdleTimerDisabled = false
            }
        }
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
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Color.mcAccent)
                Text("A recipe you can actually cook from.")
                    .font(.subheadline.weight(.medium))
                    .accessibilityFocused(self.$recipeFocused)
            }
            .padding(16)
            RecipeDetailContentView(
                recipe: self.sample.recipeDetail, heroImageHeight: 210, isIOS26: false,
                isCookingMode: self.cookingMode,
                onToggleCookingMode: {
                    self.cookingMode.toggle()
                    UIApplication.shared.isIdleTimerDisabled = self.cookingMode
                },
                currentServings: self.$currentServings,
                localHero: AnyView(self.photo(height: 210))
            )
            if self.showsKeepButton {
                VStack(spacing: 8) {
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
                    Text("Preview · yours to edit once you save it")
                        .font(.caption)
                        .foregroundStyle(Color.mcMuted)
                }
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
        guard self.stage == .destinations else { return }
        withAnimation(self.reduceMotion ? .easeOut(duration: 0.15) : .smooth(duration: 0.8)) {
            self.stage = .recipe
        } completion: {
            self.recipeFocused = true
        }
    }
}

#Preview {
    if let sample = try? DemoRecipe.load() {
        ImportDemoView(sample: sample, onKeep: { _ in })
    }
}

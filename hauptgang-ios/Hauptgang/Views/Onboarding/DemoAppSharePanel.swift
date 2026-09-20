import SwiftUI

struct DemoAppSharePanel: View {
    let sample: DemoRecipe
    let onBack: () -> Void
    let onMainCourse: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .caption) private var labelHeight = 34.0
    @AccessibilityFocusState private var headerFocused: Bool

    var body: some View {
        VStack(spacing: 18) {
            self.header
                .padding(.horizontal, 20)
                .padding(.top, 20)
            Divider().padding(.horizontal, 20)
            self.appRow
            Label("Swipe to MainCourse, then tap it.", systemImage: "arrow.left")
                .font(.footnote)
                .foregroundStyle(Color.mcBody)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 20)
            Divider().padding(.horizontal, 20)
            self.actions
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
        }
        .foregroundStyle(Color.mcInk)
        .background(.regularMaterial, in: .rect(cornerRadius: 30))
        .task { self.headerFocused = true }
    }

    private var header: some View {
        HStack(spacing: 12) {
            DemoRecipePhoto(imageName: self.sample.imageName, height: 54)
                .frame(width: 54)
                .clipShape(.rect(cornerRadius: 12))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text("Post from \(self.sample.creator)")
                    .font(.subheadline.weight(.semibold))
                Text("Recipe link")
                    .font(.caption)
                    .foregroundStyle(Color.mcBody)
            }
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityElement(children: .combine)
            .accessibilityFocused(self.$headerFocused)
            Spacer(minLength: 0)
            Button(action: self.onBack) {
                Image(systemName: "chevron.left")
                    .font(.body.weight(.medium))
                    .frame(width: 44, height: 44)
                    .background(Color.mcSurface.opacity(0.6), in: Circle())
            }
            .accessibilityLabel("Back to sharing")
        }
    }

    private var appRow: some View {
        GeometryReader { geometry in
            // End the viewport at the middle of MainCourse's icon on every screen width.
            // Larger text uses fewer placeholders so labels still have room to wrap.
            let placeholderCount = self.dynamicTypeSize.isAccessibilitySize ? 2 : 3
            let spacing: CGFloat = 12
            let itemWidth = max(
                64,
                (geometry.size.width - 20 - CGFloat(placeholderCount) * spacing)
                    / (CGFloat(placeholderCount) + 0.5)
            )

            ScrollView(.horizontal) {
                HStack(alignment: .top, spacing: spacing) {
                    self.placeholderApp("Messages", symbol: "message.fill", width: itemWidth)
                    self.placeholderApp("Notes", symbol: "note.text", width: itemWidth)
                    if placeholderCount == 3 {
                        self.placeholderApp("Reminders", symbol: "list.bullet", width: itemWidth)
                    }
                    Button(action: self.onMainCourse) {
                        VStack(spacing: 8) {
                            Image("LaunchLogo")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 64, height: 64)
                                .clipShape(.rect(cornerRadius: 14))
                            Text("MainCourse")
                                .font(.caption.weight(.semibold))
                                .multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .frame(width: itemWidth)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("demo.maincourse")
                    .accessibilityHint("Turns this post into a recipe")
                }
                .padding(.horizontal, 20)
            }
            .scrollIndicators(.hidden)
            .accessibilityIdentifier("demo.apps")
        }
        .frame(height: 64 + 8 + self.labelHeight)
    }

    private func placeholderApp(_ name: String, symbol: String, width: CGFloat) -> some View {
        VStack(spacing: 8) {
            Image(systemName: symbol)
                .font(.system(size: 28, weight: .regular))
                .frame(width: 64, height: 64)
                .background(Color.mcSurface.opacity(0.7), in: .rect(cornerRadius: 14))
            Text(name)
                .font(.caption)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(Color.mcBody)
        .opacity(0.55)
        .frame(width: width)
        .accessibilityHidden(true)
    }

    private var actions: some View {
        HStack(alignment: .top, spacing: 12) {
            self.action("Copy", symbol: "doc.on.doc")
            self.action("Save", symbol: "bookmark")
            self.action("More", symbol: "ellipsis")
        }
        .foregroundStyle(Color.mcBody)
        .accessibilityHidden(true)
    }

    private func action(_ title: String, symbol: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: symbol)
                .font(.title2)
                .frame(width: 56, height: 56)
                .background(Color.mcSurface.opacity(0.55), in: Circle())
            Text(title)
                .font(.caption)
        }
        .frame(maxWidth: .infinity)
    }
}

import SwiftUI

/// Welcome illustration: scattered recipe sources (a post, a cluttered website, a photographed
/// cookbook page, a screenshot) drift in and merge into one clean MainCourse recipe card.
struct RecipeSourcesAnimation: View {
    let sample: DemoRecipe

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visibleSources = 0
    @State private var merged = false

    private enum Source: Int, CaseIterable, Identifiable {
        case post, website, cookbookPage, screenshot

        var id: Int {
            self.rawValue
        }

        var offset: CGSize {
            switch self {
            case .post: CGSize(width: -84, height: -80)
            case .website: CGSize(width: 88, height: -76)
            case .cookbookPage: CGSize(width: -80, height: 84)
            case .screenshot: CGSize(width: 86, height: 88)
            }
        }

        var rotation: Angle {
            switch self {
            case .post: .degrees(-8)
            case .website: .degrees(6)
            case .cookbookPage: .degrees(5)
            case .screenshot: .degrees(-6)
            }
        }
    }

    var body: some View {
        ZStack {
            ForEach(Source.allCases) { source in
                let isVisible = self.visibleSources > source.rawValue
                self.card(for: source)
                    .frame(width: 136, height: 164)
                    .clipShape(.rect(cornerRadius: Theme.Radius.card))
                    .overlay(RoundedRectangle(cornerRadius: Theme.Radius.card).stroke(Color.mcHairline, lineWidth: 1))
                    .shadow(color: Color.mcInk.opacity(0.08), radius: 12, y: 6)
                    .rotationEffect(self.merged ? .zero : source.rotation)
                    .offset(self.merged ? .zero : isVisible ? source.offset : source.offset * 1.35)
                    .scaleEffect(self.merged ? 0.5 : isVisible ? 1 : 0.9)
                    .opacity(self.merged || !isVisible ? 0 : 1)
            }

            SampleRecipeCard(sample: self.sample)
                .frame(width: 224)
                .scaleEffect(self.merged ? 1 : 0.75)
                .opacity(self.merged ? 1 : 0)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 356)
        .dynamicTypeSize(...DynamicTypeSize.large)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "Recipes from posts, websites, cookbooks and screenshots come together as one clean recipe."
        )
        .task { await self.play() }
    }

    private func play() async {
        guard !self.reduceMotion else {
            self.visibleSources = Source.allCases.count
            self.merged = true
            return
        }
        try? await Task.sleep(for: .seconds(0.3))
        for index in 1 ... Source.allCases.count {
            guard !Task.isCancelled else { return }
            withAnimation(.spring(duration: 0.6, bounce: 0.25)) { self.visibleSources = index }
            try? await Task.sleep(for: .seconds(0.4))
        }
        try? await Task.sleep(for: .seconds(1.2))
        guard !Task.isCancelled else { return }
        withAnimation(.spring(duration: 0.8, bounce: 0.2)) { self.merged = true }
    }

    @ViewBuilder
    private func card(for source: Source) -> some View {
        switch source {
        case .post: self.postCard
        case .website: self.websiteCard
        case .cookbookPage: self.cookbookPageCard
        case .screenshot: self.screenshotCard
        }
    }

    private var postCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 5) {
                Image("DemoCreator")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 16, height: 16)
                    .clipShape(Circle())
                Text(self.sample.creator)
                    .font(.system(size: 8, weight: .semibold))
                    .foregroundStyle(Color.mcInk)
                    .lineLimit(1)
            }
            .padding([.horizontal, .top], 8)
            DemoRecipePhoto(imageName: self.sample.imageName, height: 76)
            HStack(spacing: 7) {
                Image(systemName: "heart")
                Image(systemName: "bubble.right")
                Image(systemName: "paperplane")
            }
            .font(.system(size: 9))
            .foregroundStyle(Color.mcInk)
            .padding(.horizontal, 8)
            VStack(alignment: .leading, spacing: 4) {
                PlaceholderLine(width: 110)
                PlaceholderLine(width: 84)
            }
            .padding(.horizontal, 8)
            Spacer(minLength: 0)
        }
        .background(Color.mcSurface)
    }

    private var websiteCard: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 3) {
                ForEach(0 ..< 3, id: \.self) { _ in
                    Circle().fill(Color.mcHairline).frame(width: 5, height: 5)
                }
                Text(verbatim: "best-recipes.blog")
                    .font(.system(size: 7))
                    .foregroundStyle(Color.mcMuted)
                    .padding(.leading, 3)
            }
            .padding(6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.mcSunken)
            VStack(alignment: .leading, spacing: 5) {
                PlaceholderLine(width: 96, height: 7, color: Color.mcBody.opacity(0.5))
                PlaceholderLine(width: 118)
                PlaceholderLine(width: 104)
                Text("Ad")
                    .font(.system(size: 8, weight: .medium))
                    .foregroundStyle(Color.mcMuted)
                    .frame(maxWidth: .infinity, minHeight: 34)
                    .background(Color.mcSunken, in: .rect(cornerRadius: 4))
                PlaceholderLine(width: 112)
                PlaceholderLine(width: 90)
            }
            .padding(.horizontal, 8)
            Spacer(minLength: 0)
        }
        .background(Color.mcSurface)
        .overlay(alignment: .bottom) {
            Text("Allow cookies?")
                .font(.system(size: 8, weight: .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 7)
                .background(Color.mcInk)
        }
    }

    private var cookbookPageCard: some View {
        ZStack {
            Color.mcBody
            VStack(alignment: .leading, spacing: 6) {
                PlaceholderLine(width: 70, height: 7, color: Color.mcInk.opacity(0.6))
                PlaceholderLine(width: 46, color: Color.mcMuted)
                ForEach(1 ... 4, id: \.self) { step in
                    HStack(alignment: .top, spacing: 5) {
                        Text(step, format: .number.grouping(.never))
                            .font(.mcMono(.caption2))
                            .foregroundStyle(Color.mcBody)
                        VStack(alignment: .leading, spacing: 3) {
                            PlaceholderLine(width: 74)
                            PlaceholderLine(width: 58)
                        }
                    }
                }
            }
            .padding(10)
            .frame(width: 112, height: 144, alignment: .topLeading)
            .background(Color.mcSurface)
            .rotationEffect(.degrees(-4))
        }
    }

    private var screenshotCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(verbatim: "9:41")
                    .font(.mcMono(.caption2, weight: .medium))
                Spacer()
                Image(systemName: "battery.75percent")
                    .font(.system(size: 8))
            }
            .foregroundStyle(Color.mcInk)
            VStack(alignment: .leading, spacing: 4) {
                PlaceholderLine(width: 80, height: 7, color: Color.mcBody.opacity(0.5))
                PlaceholderLine(width: 100)
                PlaceholderLine(width: 88)
                PlaceholderLine(width: 96)
            }
            Text("make this!!")
                .font(.system(size: 8, weight: .medium))
                .foregroundStyle(.white)
                .padding(.horizontal, 7)
                .padding(.vertical, 4)
                .background(Color.mcAccent, in: Capsule())
                .frame(maxWidth: .infinity, alignment: .trailing)
            PlaceholderLine(width: 72)
            Spacer(minLength: 0)
        }
        .padding(10)
        .background(Color.mcSurface)
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.card).stroke(Color.mcInk, lineWidth: 4))
    }
}

/// The tidy result of an import, drawn at illustration scale.
struct SampleRecipeCard: View {
    let sample: DemoRecipe

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            DemoRecipePhoto(imageName: self.sample.imageName, height: 112)
                .overlay(alignment: .topLeading) {
                    Label("Saved", systemImage: "checkmark")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.mcAccent, in: Capsule())
                        .padding(8)
                }
            VStack(alignment: .leading, spacing: 8) {
                Text(self.sample.name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.mcInk)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 10) {
                    Label(
                        RecipeDisplayFormatter.minutes(self.sample.prepTime + self.sample.cookTime),
                        systemImage: "clock"
                    )
                    Label {
                        Text(self.sample.servings, format: .number.grouping(.never))
                    } icon: {
                        Image(systemName: "person.2")
                    }
                }
                .font(.mcMono(.caption2))
                .foregroundStyle(Color.mcBody)
                Divider()
                ForEach(self.sample.ingredients.prefix(3), id: \.name) { ingredient in
                    HStack(spacing: 6) {
                        Image(systemName: "circle")
                            .font(.system(size: 9))
                            .foregroundStyle(Color.mcMuted)
                        Text(IngredientFormatter.formatQuantity(
                            amount: ingredient.amount, amountMax: nil, unit: ingredient.unit, scale: 1
                        ))
                        .font(.mcMono(.caption2, weight: .medium))
                        .foregroundStyle(Color.mcInk)
                        Text(ingredient.name)
                            .font(.caption)
                            .foregroundStyle(Color.mcBody)
                            .lineLimit(1)
                    }
                }
            }
            .padding(12)
        }
        .background(Color.mcSurface)
        .clipShape(.rect(cornerRadius: Theme.Radius.panel))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.panel).stroke(Color.mcHairline, lineWidth: 1))
        .shadow(color: Color.mcInk.opacity(0.1), radius: 16, y: 8)
    }
}

private struct PlaceholderLine: View {
    let width: CGFloat
    var height: CGFloat = 5
    var color = Color.mcLine

    var body: some View {
        Capsule()
            .fill(self.color)
            .frame(width: self.width, height: self.height)
    }
}

private extension CGSize {
    static func * (size: CGSize, factor: CGFloat) -> CGSize {
        CGSize(width: size.width * factor, height: size.height * factor)
    }
}

#Preview {
    if let sample = try? DemoRecipe.load() {
        RecipeSourcesAnimation(sample: sample)
            .background(Color.mcCanvas)
    }
}

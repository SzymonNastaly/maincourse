import SwiftUI

/// Local sharing controls teach the real route without leaving the example.
struct DemoSharePanel: View {
    let sample: DemoRecipe
    let showsDestinations: Bool
    let onBack: () -> Void
    let onShareTo: () -> Void
    let onMainCourse: () -> Void

    var body: some View {
        ViewThatFits(in: .vertical) {
            self.panelContent
            ScrollView { self.panelContent }
                .scrollBounceBehavior(.basedOnSize)
        }
        .frame(maxWidth: 520)
        .padding(.horizontal, self.showsDestinations ? 12 : 0)
        .padding(.bottom, self.showsDestinations ? 12 : 0)
        .tint(Color.mcInk)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, self.onBack)
    }

    @ViewBuilder
    private var panelContent: some View {
        if self.showsDestinations {
            DemoAppSharePanel(sample: self.sample, onBack: self.onBack, onMainCourse: self.onMainCourse)
        } else {
            DemoSocialSharePanel(onClose: self.onBack, onShareTo: self.onShareTo)
        }
    }
}

private struct DemoSocialSharePanel: View {
    let onClose: () -> Void
    let onShareTo: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .body) private var avatarSize = 60.0
    @AccessibilityFocusState private var shareFocused: Bool

    private let contacts = [
        (initials: "AM", name: "Alex"), (initials: "JL", name: "Jamie"),
        (initials: "SK", name: "Sam"), (initials: "TC", name: "Taylor"),
        (initials: "MP", name: "Morgan"), (initials: "RL", name: "Robin")
    ]

    private var contactColumns: [GridItem] {
        self.dynamicTypeSize.isAccessibilitySize
            ? [GridItem(.adaptive(minimum: max(84, self.avatarSize)))]
            : Array(repeating: GridItem(.flexible()), count: 3)
    }

    var body: some View {
        VStack(spacing: 20) {
            Capsule()
                .fill(Color.mcMuted)
                .frame(width: 34, height: 4)
                .accessibilityHidden(true)
                .padding(.top, 12)

            self.searchRow
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .padding(.horizontal, 16)

            LazyVGrid(columns: self.contactColumns, spacing: 20) {
                ForEach(self.contacts, id: \.initials) { contact in
                    VStack(spacing: 8) {
                        Text(contact.initials)
                            .font(.title3.weight(.medium))
                            .frame(width: self.avatarSize, height: self.avatarSize)
                            .background(Color.mcSunken, in: Circle())
                        Text(contact.name)
                            .font(.caption)
                    }
                }
            }
            .padding(.horizontal, 20)
            .accessibilityHidden(true)

            Divider()

            ScrollView(.horizontal) {
                HStack(alignment: .top, spacing: 16) {
                    self.actionLabel("Add to story", symbol: "plus.circle.dashed")
                        .accessibilityHidden(true)
                    self.actionLabel("Message", symbol: "bubble.left.and.bubble.right")
                        .accessibilityHidden(true)
                    Button(action: self.onShareTo) {
                        self.actionLabel("Share to…", symbol: "square.and.arrow.up", highlighted: true)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("demo.share-to")
                    .accessibilityFocused(self.$shareFocused)
                    self.actionLabel("Copy link", symbol: "link")
                        .accessibilityHidden(true)
                }
                .padding(.horizontal, 20)
            }
            .scrollIndicators(.hidden)
            .padding(.bottom, 24)
        }
        .foregroundStyle(Color.mcInk)
        .background(Color.mcSurface, in: .rect(topLeadingRadius: 28, topTrailingRadius: 28))
        .task { self.shareFocused = true }
    }

    private var searchRow: some View {
        HStack(spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 18))
                Text("Search")
                Spacer()
            }
            .foregroundStyle(Color.mcBody)
            .padding(12)
            .background(Color.mcSunken, in: Capsule())
            .accessibilityHidden(true)

            Image(systemName: "person.2.badge.plus")
                .font(.system(size: 18))
                .frame(width: 44, height: 44)
                .background(Color.mcSunken, in: Circle())
                .accessibilityHidden(true)

            Button(action: self.onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 18))
                    .frame(width: 44, height: 44)
                    .background(Color.mcSunken, in: Circle())
            }
            .accessibilityLabel("Close sharing")
        }
    }

    private func actionLabel(_ title: String, symbol: String, highlighted: Bool = false) -> some View {
        VStack(spacing: 8) {
            Image(systemName: symbol)
                .font(.title2)
                .frame(width: 58, height: 58)
                .foregroundStyle(highlighted ? Color.mcAccent : Color.mcBody)
                .background(highlighted ? Color.mcAccentTint : Color.mcSunken, in: Circle())
            Text(title)
                .font(.caption.weight(highlighted ? .semibold : .regular))
                .foregroundStyle(highlighted ? Color.mcAccent : Color.mcBody)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(width: 72)
    }
}

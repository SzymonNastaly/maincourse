import SwiftUI

/// Deliberately local controls: the real share extension is taught without leaving the example.
struct DemoSharePanel: View {
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
        .background(Color.mcSurface, in: .rect(topLeadingRadius: 28, topTrailingRadius: 28))
        .tint(Color.mcInk)
        .accessibilityAddTraits(.isModal)
    }

    private var panelContent: some View {
        VStack(spacing: 20) {
            Capsule()
                .fill(Color.mcHairline)
                .frame(width: 36, height: 5)
                .accessibilityHidden(true)
            self.header

            if self.showsDestinations {
                Button(action: self.onMainCourse) {
                    VStack(spacing: 10) {
                        Image("LaunchLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 64, height: 64)
                            .clipShape(.rect(cornerRadius: 14))
                        Text("MainCourse")
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(minWidth: 110, minHeight: 106)
                    .padding(12)
                    .background(Color.mcAccentTint, in: .rect(cornerRadius: Theme.Radius.panel))
                }
                .accessibilityIdentifier("demo.maincourse")
                Text("The same way you save from your favorite apps.")
                    .font(.subheadline)
                    .foregroundStyle(Color.mcMuted)
                    .multilineTextAlignment(.center)
            } else {
                HStack(spacing: 24) {
                    ForEach(["AM", "JL", "SK"], id: \.self) { initials in
                        Text(initials)
                            .font(.subheadline.weight(.medium))
                            .frame(width: 54, height: 54)
                            .background(Color.mcSunken, in: Circle())
                    }
                }
                .accessibilityHidden(true)
                Button(action: self.onShareTo) {
                    Label("Share to…", systemImage: "square.and.arrow.up")
                }
                .primaryButton()
                .accessibilityIdentifier("demo.share-to")
            }
            Text("Interactive example")
                .font(.caption)
                .foregroundStyle(Color.mcMuted)
        }
        .padding(20)
        .padding(.bottom, 12)
    }

    private var header: some View {
        HStack {
            Button(action: self.onBack) {
                Image(systemName: self.showsDestinations ? "chevron.left" : "xmark")
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel(self.showsDestinations ? "Back to sharing" : "Close sharing")
            Spacer()
            Text(self.showsDestinations ? "Share to an app" : "Share this recipe")
                .font(.headline)
            Spacer()
            Color.clear.frame(width: 44, height: 44)
        }
    }
}

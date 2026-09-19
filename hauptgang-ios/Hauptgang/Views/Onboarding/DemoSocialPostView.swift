import SwiftUI

struct DemoSocialPostView<Photo: View>: View {
    let sample: DemoRecipe
    @ViewBuilder let photo: () -> Photo
    let onShare: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Image("LaunchLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 34, height: 34)
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(self.sample.creator)
                        .font(.subheadline.weight(.semibold))
                    Text("A little dinner inspiration")
                        .font(.caption)
                        .foregroundStyle(Color.mcMuted)
                }
                Spacer()
            }
            .padding(14)

            self.photo()

            HStack(spacing: 16) {
                Image(systemName: "heart")
                Image(systemName: "bubble.right")
                Button(action: self.onShare) {
                    Label("Share", systemImage: "paperplane")
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 14)
                        .frame(minHeight: 44)
                        .background(Color.mcAccentTint, in: Capsule())
                }
                .tint(Color.mcAccent)
                .accessibilityIdentifier("demo.share")
                Spacer()
                Image(systemName: "bookmark")
            }
            .font(.title3)
            .padding(.horizontal, 14)
            .padding(.top, 10)

            VStack(alignment: .leading, spacing: 6) {
                Text(self.sample.name)
                    .font(.subheadline.weight(.semibold))
                Text(self.sample.caption)
                    .font(.subheadline)
                    .foregroundStyle(Color.mcBody)
            }
            .padding(14)
        }
        .background(Color.mcSurface)
        .clipShape(.rect(cornerRadius: Theme.Radius.panel))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.panel)
            .stroke(Color.mcHairline, lineWidth: 1))
    }
}

import SwiftUI

struct DemoSocialPostView<Photo: View>: View {
    let sample: DemoRecipe
    let isActive: Bool
    @ViewBuilder let photo: () -> Photo
    let onShare: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Image("DemoCreator")
                    .resizable()
                    .scaledToFill()
                    .frame(width: 34, height: 34)
                    .clipShape(Circle())
                    .accessibilityHidden(true)
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
                    .accessibilityHidden(true)
                Image(systemName: "bubble.right")
                    .accessibilityHidden(true)
                DemoShareButton(isActive: self.isActive, onShare: self.onShare)
                Spacer()
                Image(systemName: "bookmark")
                    .accessibilityHidden(true)
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
                    .lineLimit(3)
                    .truncationMode(.tail)
            }
            .padding(14)
        }
        .background(Color.mcSurface)
        .clipShape(.rect(cornerRadius: Theme.Radius.panel))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.panel)
            .stroke(Color.mcHairline, lineWidth: 1))
    }
}

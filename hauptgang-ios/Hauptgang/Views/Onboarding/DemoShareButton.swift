import SwiftUI

struct DemoShareButton: View {
    let isActive: Bool
    let onShare: () -> Void

    @State private var showsHint = false

    var body: some View {
        Button {
            self.showsHint = false
            self.onShare()
        } label: {
            Label("Share", systemImage: "paperplane")
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .background(self.showsHint ? Color.mcAccent : Color.mcAccentTint, in: Capsule())
                .overlay {
                    if self.showsHint {
                        DemoHintHalo(shape: Capsule())
                    }
                }
        }
        .buttonStyle(.plain)
        .foregroundStyle(self.showsHint ? Color.mcSurface : Color.mcAccent)
        .overlay(alignment: .top) {
            if self.showsHint {
                Text("Tap to share")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color.mcAccent, in: Capsule())
                    .overlay(alignment: .bottom) {
                        Image(systemName: "arrowtriangle.down.fill")
                            .font(.system(size: 8))
                            .foregroundStyle(Color.mcAccent)
                            .offset(y: 5)
                    }
                    .fixedSize()
                    .offset(y: -40)
                    .accessibilityHidden(true)
                    .allowsHitTesting(false)
            }
        }
        .accessibilityIdentifier("demo.share")
        .accessibilityHint("Opens the example sharing options")
        .modifier(DemoIdleHint(isActive: self.isActive, showsHint: self.$showsHint))
    }
}

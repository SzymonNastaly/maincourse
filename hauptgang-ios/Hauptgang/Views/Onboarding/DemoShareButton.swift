import SwiftUI

struct DemoShareButton: View {
    let isActive: Bool
    let onShare: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var hasShared = false
    @State private var showsHint = false

    private var canShowHint: Bool {
        self.isActive && !self.hasShared && self.scenePhase == .active
    }

    var body: some View {
        Button {
            self.hasShared = true
            self.showsHint = false
            self.onShare()
        } label: {
            Label("Share", systemImage: "paperplane")
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .background(Color.mcAccentTint, in: Capsule())
                .overlay {
                    if self.showsHint && !self.reduceMotion {
                        Capsule()
                            .stroke(Color.mcAccent, lineWidth: 2)
                            .phaseAnimator([false, true]) { halo, expanded in
                                halo
                                    .scaleEffect(expanded ? 1.12 : 1)
                                    .opacity(expanded ? 0.15 : 0.65)
                            } animation: { _ in
                                .easeInOut(duration: 1)
                            }
                    }
                }
        }
        .buttonStyle(.plain)
        .foregroundStyle(Color.mcAccent)
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
        .task(id: self.canShowHint) {
            self.showsHint = false
            guard self.canShowHint else { return }
            do {
                try await Task.sleep(for: .seconds(3))
            } catch {
                return
            }
            guard !Task.isCancelled, self.canShowHint else { return }
            self.showsHint = true
        }
    }
}

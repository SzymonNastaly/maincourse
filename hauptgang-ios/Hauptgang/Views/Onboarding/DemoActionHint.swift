import SwiftUI

/// Each step gets a fresh idle delay, paused whenever the app is inactive.
struct DemoIdleHint: ViewModifier {
    var isActive = true
    @Binding var showsHint: Bool

    @Environment(\.scenePhase) private var scenePhase

    private var canShowHint: Bool {
        self.isActive && self.scenePhase == .active
    }

    func body(content: Content) -> some View {
        content.task(id: self.canShowHint) {
            self.showsHint = false
            guard self.canShowHint else { return }
            do {
                try await Task.sleep(for: .seconds(1.2))
            } catch {
                return
            }
            guard !Task.isCancelled, self.canShowHint else { return }
            self.showsHint = true
        }
    }
}

/// A shared target treatment for the post, sharing action, and destination icon.
struct DemoHintHalo<S: InsettableShape>: View {
    let shape: S

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        self.shape
            .phaseAnimator(self.reduceMotion ? [false] : [false, true]) { _, expanded in
                // Expand the outline uniformly; scaling a pill stretches its horizontal gap.
                self.shape
                    .inset(by: expanded ? -3.5 : 0)
                    .strokeBorder(Color.mcAccent, lineWidth: 2)
                    .opacity(expanded ? 0.2 : 0.8)
            } animation: { _ in
                .easeInOut(duration: 0.8)
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

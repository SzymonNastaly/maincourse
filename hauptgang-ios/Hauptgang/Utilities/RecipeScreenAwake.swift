import SwiftUI

/// The idle timer is app-wide, so an outgoing recipe must not release a new
/// recipe's request during navigation or an onboarding transition.
@MainActor
final class RecipeScreenAwakeController {
    static let shared = RecipeScreenAwakeController()

    private var activeRecipes: Set<UUID> = []

    func update(_ owner: UUID, isVisible: Bool, scenePhase: ScenePhase, lowPowerMode: Bool) {
        if isVisible, scenePhase == .active, !lowPowerMode {
            self.activeRecipes.insert(owner)
        } else {
            self.activeRecipes.remove(owner)
        }
        UIApplication.shared.isIdleTimerDisabled = !self.activeRecipes.isEmpty
    }

    func remove(_ owner: UUID) {
        self.activeRecipes.remove(owner)
        UIApplication.shared.isIdleTimerDisabled = !self.activeRecipes.isEmpty
    }
}

struct RecipeScreenAwakeModifier: ViewModifier {
    @Environment(\.scenePhase) private var scenePhase
    @State private var owner = UUID()
    @State private var isVisible = false

    func body(content: Content) -> some View {
        content
            .onAppear {
                self.isVisible = true
                self.update()
            }
            .onDisappear {
                self.isVisible = false
                RecipeScreenAwakeController.shared.remove(self.owner)
            }
            .onChange(of: self.scenePhase) { _, _ in
                self.update()
            }
            .onReceive(NotificationCenter.default.publisher(for: .NSProcessInfoPowerStateDidChange)
                .receive(on: DispatchQueue.main)) { _ in
                    self.update()
            }
    }

    private func update() {
        RecipeScreenAwakeController.shared.update(
            self.owner,
            isVisible: self.isVisible,
            scenePhase: self.scenePhase,
            lowPowerMode: ProcessInfo.processInfo.isLowPowerModeEnabled
        )
    }
}

import SwiftUI

extension View {
    /// The running-timer dock, the timer list and the finish haptic for the tab
    /// view. Each tab's content also needs `cookingTimersFallbackDock(showsTimers:)`
    /// for systems without the tab bar accessory.
    func cookingTimersDock(showsTimers: Binding<Bool>) -> some View {
        self.modifier(CookingTimersDockModifier(showsTimers: showsTimers))
    }

    /// Before iOS 26.1 there is no bottom accessory that can hide while nothing
    /// runs, so the dock is a bar above the tab bar inside each tab.
    func cookingTimersFallbackDock(showsTimers: Binding<Bool>) -> some View {
        self.modifier(CookingTimersFallbackDockModifier(showsTimers: showsTimers))
    }
}

private struct CookingTimersDockModifier: ViewModifier {
    @Environment(CookingTimerStore.self) private var store
    @Binding var showsTimers: Bool

    func body(content: Content) -> some View {
        self.accessory(content)
            .sheet(isPresented: self.$showsTimers) {
                RunningTimersSheet()
                    .environment(self.store)
            }
            .task {
                await self.store.restoreFromSystem()
            }
            .task(id: self.store.timers) {
                await self.signalFinishedTimers()
            }
    }

    @ViewBuilder
    private func accessory(_ content: Content) -> some View {
        if #available(iOS 26.1, *) {
            // The accessory is hosted apart from the tab view and does not inherit
            // its environment objects.
            content.tabViewBottomAccessory(isEnabled: !self.store.timers.isEmpty) {
                PlacementAwareTimerDock { self.showsTimers = true }
                    .environment(self.store)
            }
        } else {
            content
        }
    }

    /// A success haptic when a timer runs out while the app is open. Sleeps until
    /// the next end date; any change to the timers restarts the wait.
    private func signalFinishedTimers() async {
        while !Task.isCancelled {
            if self.store.takeNewlyFinished() {
                HapticManager.shared.notification(.success)
            }
            let now = Date()
            guard let next = self.store.timers.compactMap(\.endDate).filter({ $0 > now }).min() else { return }
            try? await Task.sleep(for: .seconds(next.timeIntervalSince(now) + 0.05))
        }
    }
}

@available(iOS 26.1, *)
private struct PlacementAwareTimerDock: View {
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement
    let onOpen: () -> Void

    var body: some View {
        let isInline = self.placement == .inline
        RunningTimersDock(isInline: isInline, onOpen: self.onOpen)
            .padding(.horizontal, isInline ? Theme.Spacing.sm : 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct CookingTimersFallbackDockModifier: ViewModifier {
    @Environment(CookingTimerStore.self) private var store
    @Binding var showsTimers: Bool

    func body(content: Content) -> some View {
        if #available(iOS 26.1, *) {
            content
        } else {
            content.safeAreaInset(edge: .bottom, spacing: 0) {
                if !self.store.timers.isEmpty {
                    RunningTimersDock { self.showsTimers = true }
                        .padding(.horizontal, Theme.Spacing.md)
                        .padding(.vertical, Theme.Spacing.sm)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(.bar)
                        .overlay(alignment: .top) {
                            Rectangle().fill(Color.mcHairline).frame(height: 1)
                        }
                }
            }
        }
    }
}

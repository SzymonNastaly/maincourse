import Foundation
@testable import Hauptgang

/// Records what the store asks of the system, and plays the system's side:
/// timers it still holds for `restore()`, and changes made outside the app.
@MainActor
final class FakeTimerAlerting: TimerAlerting {
    enum Call: Equatable {
        case start(UUID)
        case pause(UUID)
        case resume(UUID)
        case reschedule(UUID, remaining: TimeInterval)
        case cancel(UUID)
    }

    var onSystemChange: ((SystemTimerChange) -> Void)?
    var systemTimers: [CookingTimer] = []
    private(set) var calls: [Call] = []
    var now: () -> Date = Date.init

    func start(_ timer: CookingTimer) {
        self.calls.append(.start(timer.id))
    }

    func pause(_ timer: CookingTimer) {
        self.calls.append(.pause(timer.id))
    }

    func resume(_ timer: CookingTimer) {
        self.calls.append(.resume(timer.id))
    }

    func reschedule(_ timer: CookingTimer) {
        self.calls.append(.reschedule(timer.id, remaining: timer.remaining(at: self.now())))
    }

    func cancel(_ id: UUID) {
        self.calls.append(.cancel(id))
    }

    func restore() async -> [CookingTimer] {
        self.systemTimers
    }

    func systemChanged(_ change: SystemTimerChange) {
        self.onSystemChange?(change)
    }
}

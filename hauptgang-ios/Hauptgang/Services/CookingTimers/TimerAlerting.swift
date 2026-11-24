import Foundation

/// A change the system made to a timer outside the app: a Live Activity button, or
/// the alarm alert's Stop and "+1 min".
/// Timings come from the system when it has them; nil means "as of now".
enum SystemTimerChange: Equatable {
    case stopped(UUID)
    case paused(UUID, remaining: TimeInterval?)
    case resumed(UUID, endDate: Date?)
    /// The alert's "+1 min": the timer runs again until `endDate`.
    case restarted(UUID, endDate: Date, addedSeconds: TimeInterval)
}

/// Mirrors `CookingTimerStore` timers as system timers, so a timer still rings when
/// the app is in the background, the phone is locked, or the app was killed.
@MainActor
protocol TimerAlerting: AnyObject {
    var onSystemChange: ((SystemTimerChange) -> Void)? { get set }
    func start(_ timer: CookingTimer)
    func pause(_ timer: CookingTimer)
    func resume(_ timer: CookingTimer)
    /// The timer's remaining time changed (`add(seconds:to:)`).
    func reschedule(_ timer: CookingTimer)
    func cancel(_ id: UUID)
    /// The timers the system still holds, rebuilt from what it stored with them.
    func restore() async -> [CookingTimer]
}

enum SystemTimerAlerting {
    /// AlarmKit from iOS 26.1 (the first release with the non-deprecated alert
    /// API); a time-sensitive local notification before that.
    @MainActor
    static func make() -> any TimerAlerting {
        if #available(iOS 26.1, *) {
            return AlarmKitTimerAlerting()
        }
        return NotificationTimerAlerting()
    }
}

/// Runs system calls one after another, so a pause issued while the schedule call
/// is still in flight lands after it.
@MainActor
final class SerialTasks {
    private var last: Task<Void, Never>?

    func enqueue(_ operation: @escaping @MainActor () async -> Void) {
        let previous = self.last
        self.last = Task { @MainActor in
            await previous?.value
            await operation()
        }
    }

    /// Waits for everything enqueued so far.
    func drain() async {
        await self.last?.value
    }
}

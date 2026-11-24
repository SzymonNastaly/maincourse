import Foundation
import Observation

/// The app's running cooking timers, so a timer started on one recipe stays
/// visible from every screen. Each change is mirrored to the system through
/// `TimerAlerting`, which rings when the app is in the background or killed, and
/// the list is rebuilt from the system on launch.
@MainActor
@Observable
final class CookingTimerStore {
    static let shared = CookingTimerStore(alerting: SystemTimerAlerting.make())

    private(set) var timers: [CookingTimer] = []

    @ObservationIgnored private let alerting: any TimerAlerting
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private var announcedFinished: Set<CookingTimer.ID> = []

    init(alerting: any TimerAlerting, now: @escaping () -> Date = Date.init) {
        self.alerting = alerting
        self.now = now
        alerting.onSystemChange = { [weak self] change in self?.apply(change) }
    }

    func timer(recipeId: Int, stepNumber: Int, text: String) -> CookingTimer? {
        self.timers.first { $0.belongs(toRecipe: recipeId, step: stepNumber, text: text) }
    }

    /// Starts a timer, replacing one already started from the same duration.
    @discardableResult
    func start(
        recipeId: Int,
        recipeName: String,
        stepNumber: Int,
        label: String,
        sourceText: String,
        seconds: Int
    ) -> CookingTimer {
        if let replaced = self.timer(recipeId: recipeId, stepNumber: stepNumber, text: sourceText) {
            self.cancel(replaced.id)
        }
        let duration = TimeInterval(seconds)
        let timer = CookingTimer(
            id: UUID(),
            recipeId: recipeId,
            recipeName: recipeName,
            stepNumber: stepNumber,
            label: label,
            sourceText: sourceText,
            duration: duration,
            endDate: self.now().addingTimeInterval(duration)
        )
        self.timers.append(timer)
        self.alerting.start(timer)
        return timer
    }

    /// Pauses a running timer; a paused or finished one stays as it is.
    func pause(_ id: CookingTimer.ID) {
        guard let current = self.timers.first(where: { $0.id == id }),
              !current.isPaused, !current.isFinished(at: self.now()),
              let timer = self.update(id, { self.markPaused(&$0) }) else { return }
        self.alerting.pause(timer)
    }

    func resume(_ id: CookingTimer.ID) {
        guard self.timers.contains(where: { $0.id == id && $0.isPaused }),
              let timer = self.update(id, { self.markRunning(&$0) }) else { return }
        self.alerting.resume(timer)
    }

    /// Adds time to a running, paused or finished timer.
    func add(seconds: TimeInterval, to id: CookingTimer.ID) {
        let now = self.now()
        let timer = self.update(id) { timer in
            let remaining = timer.remaining(at: now) + seconds
            timer.duration += seconds
            if timer.isPaused {
                timer.pausedRemaining = remaining
            } else {
                timer.endDate = now.addingTimeInterval(remaining)
            }
        }
        guard let timer else { return }
        self.announcedFinished.remove(id)
        self.alerting.reschedule(timer)
    }

    /// Stops a timer, or dismisses one that has finished.
    func cancel(_ id: CookingTimer.ID) {
        guard self.timers.contains(where: { $0.id == id }) else { return }
        self.timers.removeAll { $0.id == id }
        self.announcedFinished.remove(id)
        self.alerting.cancel(id)
    }

    /// True once for each batch of timers that finished since the last call.
    func takeNewlyFinished() -> Bool {
        let date = self.now()
        let finished = Set(self.timers.filter { $0.isFinished(at: date) }.map(\.id))
        let fresh = finished.subtracting(self.announcedFinished)
        self.announcedFinished = finished
        return !fresh.isEmpty
    }

    /// Finished timers first, then the soonest to end, paused ones last.
    func sortedForDisplay(at date: Date) -> [CookingTimer] {
        func rank(_ timer: CookingTimer) -> Int {
            timer.isFinished(at: date) ? 0 : timer.isPaused ? 2 : 1
        }
        return self.timers.sorted { first, second in
            if rank(first) != rank(second) {
                return rank(first) < rank(second)
            }
            return first.remaining(at: date) < second.remaining(at: date)
        }
    }

    /// Brings back the timers the system still holds after the app was killed.
    /// Timers already in the list are kept as they are.
    func restoreFromSystem() async {
        let restored = await self.alerting.restore()
        let date = self.now()
        for timer in restored where !self.timers.contains(where: { $0.id == timer.id }) {
            self.timers.append(timer)
            // Already ringing on the system's alert; no second haptic on launch.
            if timer.isFinished(at: date) {
                self.announcedFinished.insert(timer.id)
            }
        }
    }

    // MARK: - Private

    /// Returns the timer after the change, or nil when it no longer exists.
    @discardableResult
    private func update(_ id: CookingTimer.ID, _ change: (inout CookingTimer) -> Void) -> CookingTimer? {
        guard let index = self.timers.firstIndex(where: { $0.id == id }) else { return nil }
        change(&self.timers[index])
        return self.timers[index]
    }

    private func markPaused(_ timer: inout CookingTimer) {
        guard !timer.isPaused else { return }
        timer.pausedRemaining = timer.remaining(at: self.now())
        timer.endDate = nil
    }

    private func markRunning(_ timer: inout CookingTimer) {
        guard let remaining = timer.pausedRemaining else { return }
        timer.endDate = self.now().addingTimeInterval(remaining)
        timer.pausedRemaining = nil
    }

    /// Follows a change made outside the app without calling back into the system.
    private func apply(_ change: SystemTimerChange) {
        switch change {
        case let .stopped(id):
            self.timers.removeAll { $0.id == id }
            self.announcedFinished.remove(id)
        case let .paused(id):
            self.update(id) { self.markPaused(&$0) }
        case let .resumed(id):
            self.update(id) { self.markRunning(&$0) }
        case let .restarted(id, seconds):
            let now = self.now()
            self.update(id) { timer in
                timer.pausedRemaining = nil
                timer.endDate = now.addingTimeInterval(seconds)
                timer.duration += seconds
            }
            self.announcedFinished.remove(id)
        }
    }
}

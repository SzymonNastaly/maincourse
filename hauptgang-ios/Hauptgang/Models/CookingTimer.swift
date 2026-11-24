import Foundation

/// A running cooking timer. It stores an end date (or the time left while paused),
/// not a ticking counter, so views compute the countdown from the clock.
struct CookingTimer: Identifiable, Hashable, Codable {
    let id: UUID
    let recipeId: Int
    let recipeName: String
    /// 1-based, as the recipe page numbers its steps.
    let stepNumber: Int
    let label: String
    /// The duration's text in the step ("6–8 minutes"), so the step can show the
    /// timer it started in place.
    let sourceText: String
    /// The whole length, including minutes added since the start.
    var duration: TimeInterval
    /// When the timer ends; nil while paused.
    var endDate: Date?
    /// Time left while paused; nil while running.
    var pausedRemaining: TimeInterval?

    var isPaused: Bool {
        self.pausedRemaining != nil
    }

    func remaining(at date: Date) -> TimeInterval {
        if let pausedRemaining {
            return pausedRemaining
        }
        guard let endDate else { return self.duration }
        return max(0, endDate.timeIntervalSince(date))
    }

    func isFinished(at date: Date) -> Bool {
        !self.isPaused && self.remaining(at: date) <= 0
    }

    /// 0 at the start, 1 when the time is up.
    func progress(at date: Date) -> Double {
        guard self.duration > 0 else { return 1 }
        return min(1, max(0, 1 - self.remaining(at: date) / self.duration))
    }

    func belongs(toRecipe recipeId: Int, step stepNumber: Int, text: String) -> Bool {
        self.recipeId == recipeId && self.stepNumber == stepNumber && self.sourceText == text
    }
}

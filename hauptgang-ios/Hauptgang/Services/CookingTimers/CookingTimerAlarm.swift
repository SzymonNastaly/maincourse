import AlarmKit
import AppIntents
import Foundation

/// What a cooking timer's alarm carries, so the Live Activity can draw it and the
/// app can rebuild the timer after it was killed. Compiled into the app and the
/// CookingTimersWidget extension; AlarmKit stores it in the alarm's attributes.
@available(iOS 26.1, *)
struct CookingTimerMetadata: AlarmMetadata {
    var recipeId: Int
    var recipeName: String
    var stepNumber: Int
    var label: String
    var sourceText: String
    /// The timer's whole length when this alarm was scheduled.
    var duration: TimeInterval
}

// Live Activity buttons. Compiled into both targets: the widget needs the types to
// build its buttons, and `perform()` runs in the app's process. The app follows the
// change through `AlarmManager.alarmUpdates`.

@available(iOS 26.1, *)
struct PauseCookingTimerIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Pause timer"
    static let isDiscoverable = false

    @Parameter(title: "Timer")
    var alarmID: String

    init(alarmID: UUID) {
        self.alarmID = alarmID.uuidString
    }

    init() {
        self.alarmID = ""
    }

    func perform() throws -> some IntentResult {
        if let id = UUID(uuidString: self.alarmID) {
            try AlarmManager.shared.pause(id: id)
        }
        return .result()
    }
}

@available(iOS 26.1, *)
struct ResumeCookingTimerIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Resume timer"
    static let isDiscoverable = false

    @Parameter(title: "Timer")
    var alarmID: String

    init(alarmID: UUID) {
        self.alarmID = alarmID.uuidString
    }

    init() {
        self.alarmID = ""
    }

    func perform() throws -> some IntentResult {
        if let id = UUID(uuidString: self.alarmID) {
            try AlarmManager.shared.resume(id: id)
        }
        return .result()
    }
}

@available(iOS 26.1, *)
struct StopCookingTimerIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Stop timer"
    static let isDiscoverable = false

    @Parameter(title: "Timer")
    var alarmID: String

    init(alarmID: UUID) {
        self.alarmID = alarmID.uuidString
    }

    init() {
        self.alarmID = ""
    }

    func perform() throws -> some IntentResult {
        if let id = UUID(uuidString: self.alarmID) {
            try AlarmManager.shared.cancel(id: id)
        }
        return .result()
    }
}

enum CookingTimerClock {
    /// 9:58, 1:04:30
    static func string(_ seconds: TimeInterval) -> String {
        let total = max(0, Int(seconds.rounded(.up)))
        let hours = total / 3600, minutes = (total % 3600) / 60, rest = total % 60
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, rest)
            : String(format: "%d:%02d", minutes, rest)
    }
}

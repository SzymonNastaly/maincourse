import ActivityKit
import AlarmKit
import Foundation
import os
import SwiftUI

private let logger = Logger(subsystem: "app.hauptgang.ios", category: "AlarmKitTimerAlerting")

/// Schedules each timer as an AlarmKit countdown: it rings like the Clock app's
/// timer, even in silent mode, and shows a Live Activity on the lock screen and in
/// the Dynamic Island (drawn by CookingTimersWidget). Falls back to a notification
/// when the person declines alarms or the system refuses one.
@available(iOS 26.1, *)
@MainActor
final class AlarmKitTimerAlerting: TimerAlerting {
    var onSystemChange: ((SystemTimerChange) -> Void)?

    /// What "+1 min" on the alert adds.
    private static let restartSeconds: TimeInterval = 60

    private let fallback = NotificationTimerAlerting()
    private let tasks = SerialTasks()
    /// Timers scheduled with AlarmKit; the rest went to the notification fallback.
    private var alarmIDs: Set<UUID> = []
    /// Last state seen in `alarmUpdates`. A timer only counts as stopped once it was
    /// seen and then vanished, so an update racing the schedule call can't drop it.
    private var seen: [UUID: Alarm.State] = [:]

    init() {
        Task { [weak self] in
            for await alarms in AlarmManager.shared.alarmUpdates {
                self?.reconcile(alarms)
            }
        }
    }

    func start(_ timer: CookingTimer) {
        self.tasks.enqueue { [self] in
            let manager = AlarmManager.shared
            if manager.authorizationState == .notDetermined {
                do {
                    _ = try await manager.requestAuthorization()
                } catch {
                    logger.error("AlarmKit authorization failed: \(error.localizedDescription)")
                }
            }
            guard manager.authorizationState == .authorized else {
                self.fallback.start(timer)
                return
            }
            do {
                try await self.schedule(timer)
                self.alarmIDs.insert(timer.id)
            } catch {
                logger.error("AlarmKit schedule failed: \(error.localizedDescription)")
                self.fallback.start(timer)
            }
        }
    }

    // Each call decides between AlarmKit and the fallback inside the queue, once any
    // earlier start (which may be waiting on the permission prompt) has finished.

    func pause(_ timer: CookingTimer) {
        self.tasks.enqueue { [self] in
            guard self.alarmIDs.contains(timer.id) else { return self.fallback.pause(timer) }
            self.run("pause") { try AlarmManager.shared.pause(id: timer.id) }
        }
    }

    func resume(_ timer: CookingTimer) {
        self.tasks.enqueue { [self] in
            guard self.alarmIDs.contains(timer.id) else { return self.fallback.resume(timer) }
            self.run("resume") { try AlarmManager.shared.resume(id: timer.id) }
        }
    }

    /// AlarmKit can't change a countdown's length, so replace the alarm under the
    /// same id. Forgetting it first keeps the cancel from reading as a stop.
    func reschedule(_ timer: CookingTimer) {
        self.tasks.enqueue { [self] in
            guard self.alarmIDs.contains(timer.id) else { return self.fallback.reschedule(timer) }
            self.seen[timer.id] = nil
            self.run("cancel") { try AlarmManager.shared.cancel(id: timer.id) }
            do {
                try await self.schedule(timer)
                if timer.isPaused {
                    try AlarmManager.shared.pause(id: timer.id)
                }
            } catch {
                logger.error("AlarmKit reschedule failed: \(error.localizedDescription)")
            }
        }
    }

    func cancel(_ id: UUID) {
        self.tasks.enqueue { [self] in
            guard self.alarmIDs.remove(id) != nil else { return self.fallback.cancel(id) }
            self.seen[id] = nil
            self.run("cancel") { try AlarmManager.shared.cancel(id: id) }
        }
    }

    /// AlarmKit keeps the alarms but not their metadata; the alarm's Live Activity
    /// holds both the metadata and the countdown, so the timer is rebuilt from it.
    /// An alarm without an activity (Live Activities turned off) still rings but
    /// is not shown in the app.
    func restore() async -> [CookingTimer] {
        await self.tasks.drain()
        let alarms: [Alarm]
        do {
            alarms = try AlarmManager.shared.alarms
        } catch {
            logger.error("AlarmKit alarms unavailable: \(error.localizedDescription)")
            alarms = []
        }
        let activities = Activity<AlarmAttributes<CookingTimerMetadata>>.activities
        var restored: [CookingTimer] = []
        for alarm in alarms {
            guard let activity = activities.first(where: { $0.content.state.alarmID == alarm.id }),
                  let metadata = activity.attributes.metadata
            else { continue }
            restored.append(Self.timer(id: alarm.id, metadata: metadata, mode: activity.content.state.mode))
            self.alarmIDs.insert(alarm.id)
            self.seen[alarm.id] = alarm.state
        }
        return await restored + self.fallback.restore()
    }

    // MARK: - Private

    private func schedule(_ timer: CookingTimer) async throws {
        let attributes = AlarmAttributes(
            presentation: Self.presentation(label: timer.label),
            metadata: CookingTimerMetadata(
                recipeId: timer.recipeId,
                recipeName: timer.recipeName,
                stepNumber: timer.stepNumber,
                label: timer.label,
                sourceText: timer.sourceText,
                duration: timer.duration
            ),
            // The system draws alarm UI on black; deep green disappears there.
            tintColor: Color.mcLime
        )
        let configuration = AlarmManager.AlarmConfiguration(
            countdownDuration: .init(
                preAlert: max(1, timer.remaining(at: Date())),
                postAlert: Self.restartSeconds
            ),
            attributes: attributes
        )
        _ = try await AlarmManager.shared.schedule(id: timer.id, configuration: configuration)
    }

    private static func presentation(label: String) -> AlarmPresentation {
        AlarmPresentation(
            alert: .init(
                title: "\(label) is done",
                secondaryButton: AlarmButton(text: "+1 min", textColor: Color.mcAccentDark, systemImageName: "plus"),
                secondaryButtonBehavior: .countdown
            ),
            countdown: .init(
                // The label is recipe data, already in the recipe's language.
                title: LocalizedStringResource(stringLiteral: label),
                pauseButton: AlarmButton(text: "Pause", textColor: Color.mcAccentDark, systemImageName: "pause.fill")
            ),
            paused: .init(
                title: "\(label) paused",
                resumeButton: AlarmButton(text: "Resume", textColor: Color.mcAccentDark, systemImageName: "play.fill")
            )
        )
    }

    static func timer(
        id: UUID,
        metadata: CookingTimerMetadata,
        mode: AlarmPresentationState.Mode,
        now: Date = Date()
    ) -> CookingTimer {
        var timer = CookingTimer(
            id: id,
            recipeId: metadata.recipeId,
            recipeName: metadata.recipeName,
            stepNumber: metadata.stepNumber,
            label: metadata.label,
            sourceText: metadata.sourceText,
            duration: metadata.duration,
            endDate: now
        )
        switch mode {
        case let .countdown(countdown):
            timer.endDate = countdown.fireDate
        case let .paused(paused):
            timer.endDate = nil
            timer.pausedRemaining = max(0, paused.totalCountdownDuration - paused.previouslyElapsedDuration)
        case .alert:
            break
        @unknown default:
            break
        }
        return timer
    }

    private func reconcile(_ alarms: [Alarm]) {
        let current = Dictionary(
            alarms.filter { self.alarmIDs.contains($0.id) }.map { ($0.id, $0.state) },
            uniquingKeysWith: { _, new in new }
        )
        var changes: [SystemTimerChange] = []
        for id in self.seen.keys where current[id] == nil {
            self.alarmIDs.remove(id)
            changes.append(.stopped(id))
        }
        for (id, state) in current {
            switch (self.seen[id], state) {
            case (.countdown?, .paused): changes.append(.paused(id))
            case (.paused?, .countdown): changes.append(.resumed(id))
            case (.alerting?, .countdown): changes.append(.restarted(id, seconds: Self.restartSeconds))
            default: break
            }
        }
        self.seen = current
        for change in changes {
            logger.info("System changed timer: \(String(describing: change), privacy: .public)")
            self.onSystemChange?(change)
        }
    }

    private func run(_ action: String, _ call: () throws -> Void) {
        do {
            try call()
        } catch {
            logger.error("AlarmKit \(action) failed: \(error.localizedDescription)")
        }
    }
}

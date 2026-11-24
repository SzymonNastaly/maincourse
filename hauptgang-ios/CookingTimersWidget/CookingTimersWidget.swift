import ActivityKit
import AlarmKit
import AppIntents
import SwiftUI
import WidgetKit

@main
struct CookingTimersWidgetBundle: WidgetBundle {
    var body: some Widget {
        CookingTimerLiveActivity()
    }
}

/// The lock screen and Dynamic Island for a running recipe timer. AlarmKit starts
/// and updates the activity; this only draws `context.state`.
struct CookingTimerLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AlarmAttributes<CookingTimerMetadata>.self) { context in
            LockScreenTimerView(timer: TimerSnapshot(context))
                .activityBackgroundTint(Color.mcSurface)
                .activitySystemActionForegroundColor(Color.mcAccent)
        } dynamicIsland: { context in
            let timer = TimerSnapshot(context)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    TimerHeading(timer: timer, onDark: true)
                        .frame(maxHeight: .infinity)
                        .padding(.leading, 6)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    CountdownText(mode: timer.mode)
                        .font(.mcMono(.title2, weight: .medium))
                        .foregroundStyle(timer.isPaused ? Color.white.opacity(0.6) : Color.mcLime)
                        .frame(maxHeight: .infinity)
                        .padding(.trailing, 6)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 8) {
                        TimerDetail(timer: timer, onDark: true)
                        TimerControls(timer: timer, onDark: true)
                    }
                    .padding(.horizontal, 6)
                }
            } compactLeading: {
                Image(systemName: timer.isPaused ? "pause.fill" : "timer")
                    .foregroundStyle(Color.mcLime)
            } compactTrailing: {
                CountdownText(mode: timer.mode)
                    .font(.mcMono(.subheadline, weight: .medium))
                    .foregroundStyle(timer.isPaused ? Color.white.opacity(0.6) : Color.mcLime)
                    .frame(maxWidth: 52)
            } minimal: {
                TimerRing(timer: timer)
            }
            .keylineTint(Color.mcLime)
        }
    }
}

/// The parts of the activity context the views need, in one value.
private struct TimerSnapshot {
    let id: UUID
    let mode: AlarmPresentationState.Mode
    let metadata: CookingTimerMetadata?

    init(_ context: ActivityViewContext<AlarmAttributes<CookingTimerMetadata>>) {
        self.id = context.state.alarmID
        self.mode = context.state.mode
        self.metadata = context.attributes.metadata
    }

    var label: String {
        self.metadata?.label ?? String(localized: "Timer")
    }

    var isPaused: Bool {
        if case .paused = self.mode {
            return true
        }
        return false
    }

    var isAlerting: Bool {
        if case .alert = self.mode {
            return true
        }
        return false
    }

    var detail: String? {
        guard let metadata else { return nil }
        return String(localized: "\(metadata.recipeName) · Step \(metadata.stepNumber)")
    }
}

private struct LockScreenTimerView: View {
    let timer: TimerSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                TimerHeading(timer: self.timer, onDark: false)
                Spacer(minLength: 0)
                CountdownText(mode: self.timer.mode)
                    .font(.mcMono(.title, weight: .medium))
                    .foregroundStyle(self.timer.isPaused ? Color.mcBody : Color.mcAccent)
            }
            TimerDetail(timer: self.timer, onDark: false)
            TimerControls(timer: self.timer, onDark: false)
                .padding(.top, 6)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }
}

/// The timer's name ("Simmer"), with a Paused tag while paused.
private struct TimerHeading: View {
    let timer: TimerSnapshot
    let onDark: Bool

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: self.timer.isPaused ? "pause.circle.fill" : "timer")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(self.onDark ? Color.mcLime : Color.mcAccent)
            Text(self.timer.isAlerting ? "\(self.timer.label) is done" : self.timer.label)
                .font(.headline)
                .foregroundStyle(self.onDark ? Color.white : Color.mcInk)
            if self.timer.isPaused {
                Text("Paused")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(self.onDark ? Color.white.opacity(0.6) : Color.mcBody)
            }
        }
        .lineLimit(1)
    }
}

/// Recipe and step, on its own row so the countdown can't squeeze it.
private struct TimerDetail: View {
    let timer: TimerSnapshot
    let onDark: Bool

    var body: some View {
        if let detail = self.timer.detail {
            Text(detail)
                .font(.footnote)
                .foregroundStyle(self.onDark ? Color.white.opacity(0.6) : Color.mcBody)
                .lineLimit(1)
        }
    }
}

/// Progress bar with pause/resume and stop beside it.
private struct TimerControls: View {
    let timer: TimerSnapshot
    let onDark: Bool

    var body: some View {
        HStack(spacing: 8) {
            TimerProgressBar(mode: self.timer.mode)
                .tint(self.onDark ? Color.mcLime : Color.mcAccent)
            if self.timer.isPaused {
                Button(intent: ResumeCookingTimerIntent(alarmID: self.timer.id)) {
                    ControlIcon(systemName: "play.fill", prominent: true, onDark: self.onDark)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Resume")
            } else if !self.timer.isAlerting {
                Button(intent: PauseCookingTimerIntent(alarmID: self.timer.id)) {
                    ControlIcon(systemName: "pause.fill", prominent: true, onDark: self.onDark)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Pause")
            }
            Button(intent: StopCookingTimerIntent(alarmID: self.timer.id)) {
                ControlIcon(systemName: "xmark", prominent: false, onDark: self.onDark)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Stop")
        }
    }
}

private struct ControlIcon: View {
    let systemName: String
    let prominent: Bool
    let onDark: Bool

    var body: some View {
        Image(systemName: self.systemName)
            .font(.subheadline.weight(.bold))
            .foregroundStyle(self.foreground)
            .frame(width: 36, height: 36)
            .background(self.background, in: Circle())
    }

    private var foreground: Color {
        switch (self.onDark, self.prominent) {
        case (true, true): Color.mcAccentDark
        case (true, false): Color.white
        case (false, true): Color.white
        case (false, false): Color.mcInk
        }
    }

    private var background: Color {
        switch (self.onDark, self.prominent) {
        case (true, true): Color.mcLime
        case (true, false): Color.white.opacity(0.18)
        case (false, true): Color.mcAccent
        case (false, false): Color.mcSunken
        }
    }
}

/// Ticks by itself: the system renders `Text(timerInterval:)` without waking the widget.
private struct CountdownText: View {
    let mode: AlarmPresentationState.Mode

    var body: some View {
        switch self.mode {
        case let .countdown(countdown):
            Text(timerInterval: countdown.startDate ... max(countdown.startDate, countdown.fireDate), countsDown: true)
                .monospacedDigit()
                .multilineTextAlignment(.trailing)
        case let .paused(paused):
            Text(CookingTimerClock.string(paused.totalCountdownDuration - paused.previouslyElapsedDuration))
        case .alert:
            Text("0:00")
        @unknown default:
            Text(verbatim: "")
        }
    }
}

private struct TimerProgressBar: View {
    let mode: AlarmPresentationState.Mode

    var body: some View {
        Group {
            switch self.mode {
            case let .countdown(countdown):
                ProgressView(
                    timerInterval: Self.overall(countdown),
                    countsDown: false,
                    label: { EmptyView() },
                    currentValueLabel: { EmptyView() }
                )
            case let .paused(paused):
                ProgressView(value: paused.previouslyElapsedDuration, total: max(1, paused.totalCountdownDuration))
            case .alert:
                ProgressView(value: 1)
            @unknown default:
                ProgressView(value: 0)
            }
        }
        .progressViewStyle(.linear)
    }

    /// The whole countdown, including time elapsed before a pause, so the bar
    /// doesn't restart from empty on resume.
    static func overall(_ countdown: AlarmPresentationState.Mode.Countdown) -> ClosedRange<Date> {
        let start = countdown.startDate.addingTimeInterval(-countdown.previouslyElapsedDuration)
        return start ... max(start, countdown.fireDate)
    }
}

/// Minimal Dynamic Island: a ring that empties as the timer runs.
private struct TimerRing: View {
    let timer: TimerSnapshot

    var body: some View {
        switch self.timer.mode {
        case let .countdown(countdown):
            ProgressView(
                timerInterval: TimerProgressBar.overall(countdown),
                countsDown: true,
                label: { EmptyView() },
                currentValueLabel: { Image(systemName: "timer").font(.caption2) }
            )
            .progressViewStyle(.circular)
            .tint(Color.mcLime)
            .foregroundStyle(Color.mcLime)
        default:
            Image(systemName: self.timer.isPaused ? "pause.fill" : "bell.fill")
                .foregroundStyle(Color.mcLime)
        }
    }
}

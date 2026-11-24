import SwiftUI

/// One extra tap at most: pick a time within a range ("6 min / 7 min / 8 min"),
/// or control the timer already running for this duration.
struct StepTimerPopover: View {
    @Environment(CookingTimerStore.self) private var store

    let timer: InstructionTimer
    let running: CookingTimer?
    let onStart: (Int) -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Spacing.sm) {
            if let running {
                TimelineView(TimerTick.everySecond) { context in
                    self.controls(for: running, at: context.date)
                }
            } else {
                Text(verbatim: self.timer.timerLabel)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.mcInk)
                HStack(spacing: Theme.Spacing.sm) {
                    ForEach(self.timer.choices, id: \.self) { seconds in
                        Button {
                            self.onStart(seconds)
                        } label: {
                            Text(verbatim: TimerFormat.short(seconds))
                                .font(.mcMono(.subheadline, weight: .medium))
                                .frame(minWidth: 52, minHeight: 30)
                        }
                        .buttonStyle(.bordered)
                        .tint(Color.mcAccent)
                        .accessibilityLabel(Text("Start \(TimerFormat.spoken(seconds)) timer"))
                    }
                }
            }
        }
        .padding(.horizontal, Theme.Spacing.md)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private func controls(for running: CookingTimer, at date: Date) -> some View {
        let finished = running.isFinished(at: date)
        HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.sm) {
            Text(verbatim: running.label)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.mcInk)
            Spacer(minLength: Theme.Spacing.md)
            if finished {
                Text("done", comment: "A timer whose time is up, as in \"1 minute · done\"")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.mcAccent)
            } else {
                Text(verbatim: TimerFormat.clock(running.remaining(at: date)))
                    .font(.mcMono(.subheadline, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(running.isPaused ? Color.mcBody : Color.mcInk)
            }
        }
        HStack(spacing: Theme.Spacing.sm) {
            if !finished {
                Button {
                    running.isPaused ? self.store.resume(running.id) : self.store.pause(running.id)
                } label: {
                    Image(systemName: running.isPaused ? "play.fill" : "pause.fill")
                        .frame(minWidth: 28, minHeight: 30)
                }
                .accessibilityLabel(running.isPaused ? Text("Resume") : Text("Pause"))
            }
            Button {
                self.store.add(seconds: 60, to: running.id)
            } label: {
                Text("+1 min", comment: "Adds one minute to a timer")
                    .font(.mcMono(.subheadline, weight: .medium))
                    .frame(minHeight: 30)
            }
            .accessibilityLabel(Text("Add 1 minute"))
            Button {
                self.store.cancel(running.id)
                self.onClose()
            } label: {
                Group {
                    if finished {
                        Text("Done")
                    } else {
                        Image(systemName: "stop.fill")
                    }
                }
                .frame(minWidth: 28, minHeight: 30)
            }
            .accessibilityLabel(finished ? Text("Done") : Text("Stop"))
        }
        .buttonStyle(.bordered)
        .tint(Color.mcAccent)
        .font(.subheadline.weight(.medium))
    }
}

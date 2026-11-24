import SwiftUI

/// Running timers docked above the tab bar, like Music's mini player. Shows the
/// timer that needs the cook next; a tap opens the full list.
struct RunningTimersDock: View {
    @Environment(CookingTimerStore.self) private var store

    /// The tab bar has collapsed into its inline form, which leaves room for one line.
    var isInline = false
    let onOpen: () -> Void

    var body: some View {
        TimelineView(TimerTick.everySecond) { context in
            let timers = self.store.sortedForDisplay(at: context.date)
            if let first = timers.first {
                Group {
                    if first.isFinished(at: context.date) {
                        self.finished(first, others: timers.count - 1)
                    } else {
                        self.running(first, others: timers.count - 1, at: context.date)
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture(perform: self.onOpen)
                .accessibilityElement(children: .contain)
                .accessibilityAction(named: Text("Show all timers"), self.onOpen)
            }
        }
    }

    private func running(_ timer: CookingTimer, others: Int, at date: Date) -> some View {
        HStack(spacing: 10) {
            TimerProgressPie(progress: timer.progress(at: date), isPaused: timer.isPaused)
                .frame(width: 20, height: 20)

            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(verbatim: timer.label)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.mcInk)
                        .lineLimit(1)
                    Text(verbatim: TimerFormat.clock(timer.remaining(at: date)))
                        .font(.mcMono(.subheadline, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(timer.isPaused ? Color.mcBody : Color.mcAccent)
                        .layoutPriority(1)
                }
                if !self.isInline {
                    self.detail(timer)
                }
            }

            Spacer(minLength: 0)

            self.othersBadge(others)

            if !self.isInline {
                Button {
                    timer.isPaused ? self.store.resume(timer.id) : self.store.pause(timer.id)
                } label: {
                    Image(systemName: timer.isPaused ? "play.fill" : "pause.fill")
                        .font(.body)
                        .foregroundStyle(Color.mcInk)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle().inset(by: -6))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(timer.isPaused ? Text("Resume") : Text("Pause"))
            }
        }
    }

    private func finished(_ timer: CookingTimer, others: Int) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "bell.fill")
                .font(.subheadline)
                .foregroundStyle(Color.mcAccent)
                .symbolEffect(.wiggle, options: .repeat(.periodic(delay: 1)))
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 0) {
                // The label gives way first, so "done" always shows.
                HStack(spacing: 4) {
                    Text(verbatim: timer.label)
                        .lineLimit(1)
                    Text("— done", comment: "After a timer's name in the timer dock once its time is up")
                        .fixedSize()
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.mcAccent)
                if !self.isInline {
                    self.detail(timer)
                }
            }

            Spacer(minLength: 0)

            self.othersBadge(others)

            // The inline accessory is too narrow for both actions.
            if !self.isInline {
                Button {
                    self.store.add(seconds: 60, to: timer.id)
                } label: {
                    Text("+1 min", comment: "Adds one minute to a timer")
                        .font(.mcMono(.caption, weight: .medium))
                }
                .buttonStyle(.bordered)
                .tint(Color.mcAccent)
                .fixedSize()
                .accessibilityLabel(Text("Add 1 minute"))
            }

            Button {
                self.store.cancel(timer.id)
            } label: {
                Text("Done").font(.caption.weight(.semibold))
            }
            .buttonStyle(.borderedProminent)
            .tint(Color.mcAccent)
            .fixedSize()
        }
        .controlSize(.small)
    }

    private func detail(_ timer: CookingTimer) -> some View {
        Text("Step \(timer.stepNumber) · \(timer.recipeName)")
            .font(.caption)
            .foregroundStyle(Color.mcBody)
            .lineLimit(1)
    }

    @ViewBuilder
    private func othersBadge(_ others: Int) -> some View {
        if others > 0 {
            Text(verbatim: "+\(others)")
                .font(.mcMono(.caption, weight: .medium))
                .foregroundStyle(Color.mcAccent)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.mcAccentLine, in: Capsule())
                .accessibilityLabel(Text("\(others) more timers"))
        }
    }
}

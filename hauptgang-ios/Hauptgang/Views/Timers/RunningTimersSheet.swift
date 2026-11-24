import SwiftUI

/// Every timer, grouped by recipe, as dense list rows.
struct RunningTimersSheet: View {
    @Environment(CookingTimerStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    /// Tall enough for the rows, so a couple of timers don't open a half-empty sheet.
    private var fittedHeight: CGFloat {
        let recipes = Set(self.store.timers.map(\.recipeId)).count
        return min(560, 110 + CGFloat(recipes) * 52 + CGFloat(self.store.timers.count) * 64)
    }

    var body: some View {
        NavigationStack {
            TimelineView(TimerTick.everySecond) { context in
                let timers = self.store.sortedForDisplay(at: context.date)
                List {
                    ForEach(Self.recipes(of: timers), id: \.id) { recipe in
                        Section {
                            ForEach(timers.filter { $0.recipeId == recipe.id }) { timer in
                                self.row(timer, at: context.date)
                            }
                        } header: {
                            Text(verbatim: recipe.name)
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .overlay {
                    if timers.isEmpty {
                        ContentUnavailableView("No Timers", systemImage: "timer")
                    }
                }
            }
            .navigationTitle("Timers")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { self.dismiss() }
                }
            }
        }
        .tint(Color.mcAccent)
        .presentationDetents([.height(self.fittedHeight), .large])
    }

    /// Recipes in the order their first timer appears.
    private static func recipes(of timers: [CookingTimer]) -> [(id: Int, name: String)] {
        timers.reduce(into: []) { list, timer in
            if !list.contains(where: { $0.id == timer.recipeId }) {
                list.append((timer.recipeId, timer.recipeName))
            }
        }
    }

    private func row(_ timer: CookingTimer, at date: Date) -> some View {
        let finished = timer.isFinished(at: date)
        return HStack(spacing: Theme.Spacing.sm) {
            if finished {
                Image(systemName: "bell.fill")
                    .foregroundStyle(Color.mcAccent)
                    .frame(width: 20, height: 20)
                    .accessibilityHidden(true)
            } else {
                TimerProgressPie(progress: timer.progress(at: date), isPaused: timer.isPaused)
                    .frame(width: 20, height: 20)
            }

            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: timer.label)
                    .font(.body.weight(.medium))
                    .foregroundStyle(Color.mcInk)
                    .lineLimit(1)
                Text("Step \(timer.stepNumber)")
                    .font(.caption)
                    .foregroundStyle(Color.mcBody)
            }

            Spacer(minLength: Theme.Spacing.xs)

            if finished {
                Text("done", comment: "A timer whose time is up, as in \"1 minute · done\"")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.mcAccent)
            } else {
                Text(verbatim: TimerFormat.clock(timer.remaining(at: date)))
                    .font(.mcMono(.body, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(timer.isPaused ? Color.mcBody : Color.mcInk)
            }

            self.controls(timer, finished: finished)
        }
        .swipeActions {
            Button("Stop", role: .destructive) { self.store.cancel(timer.id) }
        }
    }

    private func controls(_ timer: CookingTimer, finished: Bool) -> some View {
        HStack(spacing: 2) {
            Button {
                self.store.add(seconds: 60, to: timer.id)
            } label: {
                Text(verbatim: "+1")
                    .font(.mcMono(.subheadline, weight: .medium))
                    .frame(width: 34, height: 30)
            }
            .accessibilityLabel(Text("Add 1 minute"))

            if finished {
                Button {
                    self.store.cancel(timer.id)
                } label: {
                    Image(systemName: "checkmark")
                        .frame(width: 34, height: 30)
                }
                .accessibilityLabel(Text("Done"))
            } else {
                Button {
                    timer.isPaused ? self.store.resume(timer.id) : self.store.pause(timer.id)
                } label: {
                    Image(systemName: timer.isPaused ? "play.fill" : "pause.fill")
                        .frame(width: 34, height: 30)
                }
                .accessibilityLabel(timer.isPaused ? Text("Resume") : Text("Pause"))

                Button(role: .destructive) {
                    self.store.cancel(timer.id)
                } label: {
                    Image(systemName: "xmark")
                        .frame(width: 34, height: 30)
                }
                .accessibilityLabel(Text("Stop"))
            }
        }
        .buttonStyle(.borderless)
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(Color.mcAccent)
    }
}

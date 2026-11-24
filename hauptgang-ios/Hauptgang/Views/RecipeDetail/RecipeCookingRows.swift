import SwiftUI

/// An ingredient the cook can tick off. Parsed rows show the scaled quantity
/// in Plex Mono; unparsed rows fall back to the raw string.
struct IngredientRow: View {
    @ScaledMetric(relativeTo: .body) private var bulletSize: CGFloat = 6

    let ingredient: StructuredIngredient
    let scale: Decimal
    let isChecked: Bool
    let onToggle: () -> Void

    private var textColor: Color {
        self.isChecked ? .mcBody : .mcInk
    }

    var body: some View {
        Button(action: self.onToggle) {
            HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.sm) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.body)
                    .hidden()
                    .overlay {
                        if self.isChecked {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.body)
                                .foregroundStyle(Color.mcAccent)
                        } else {
                            Circle()
                                .fill(Color.mcMuted)
                                .frame(width: self.bulletSize, height: self.bulletSize)
                        }
                    }
                    .accessibilityHidden(true)

                self.text
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 3)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityValue(self.isChecked ? Text("Checked") : Text(verbatim: ""))
    }

    private var text: Text {
        guard self.ingredient.hasStructuredFields else {
            return Text(self.ingredient.raw)
                .font(.body)
                .foregroundColor(self.textColor)
                .strikethrough(self.isChecked)
        }

        let quantityText = IngredientFormatter.formatQuantity(
            amount: self.ingredient.amount,
            amountMax: self.ingredient.amountMax,
            unit: self.ingredient.unit,
            scale: self.scale
        )

        return Text(verbatim: quantityText.isEmpty ? "" : "\(quantityText) ")
            .font(.mcMono(.body, weight: .medium))
            .foregroundColor(self.textColor)
            .strikethrough(self.isChecked)
            + Text(self.ingredient.name ?? self.ingredient.raw)
            .font(.body)
            .foregroundColor(self.textColor)
            .strikethrough(self.isChecked)
            + Text(verbatim: self.ingredient.note.map { ", \($0)" } ?? "")
            .font(.body)
            .foregroundColor(.mcBody)
            .strikethrough(self.isChecked)
    }
}

/// A numbered step the cook can mark done. Done steps are not struck
/// through, because a cook rereads them. Durations the server found in the step
/// are timer chips; a tap anywhere else on the row marks the step done.
struct StepRow: View {
    let number: Int
    let instruction: String
    let isDone: Bool
    var timers: [LocatedTimer] = []
    var recipeId = 0
    var recipeName = ""
    let onToggle: () -> Void

    var body: some View {
        if self.timers.isEmpty {
            Button(action: self.onToggle) {
                StepRowLayout(number: self.number, isDone: self.isDone) {
                    Text(self.instruction)
                        .font(.body)
                        .foregroundColor(self.isDone ? .mcBody : .mcInk)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Step \(self.number): \(self.instruction)"))
            .accessibilityValue(self.isDone ? Text("Done") : Text(verbatim: ""))
        } else {
            TimedStepRow(
                number: self.number,
                instruction: self.instruction,
                isDone: self.isDone,
                timers: self.timers,
                recipeId: self.recipeId,
                recipeName: self.recipeName,
                onToggle: self.onToggle
            )
        }
    }
}

/// The step number badge beside the step's text.
private struct StepRowLayout<Content: View>: View {
    @ScaledMetric(relativeTo: .body) private var badgeSize: CGFloat = 24

    let number: Int
    let isDone: Bool
    @ViewBuilder let content: Content

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Spacing.md) {
            Text(self.number, format: .number.grouping(.never))
                .font(.mcMono(.caption, weight: .medium))
                .foregroundColor(Color.mcAccent)
                .opacity(self.isDone ? 0 : 1)
                .frame(width: self.badgeSize, height: self.badgeSize)
                .background(
                    self.isDone ? Color.mcAccent : Color.mcAccentTint,
                    in: RoundedRectangle(cornerRadius: 6)
                )
                .overlay {
                    if self.isDone {
                        Image(systemName: "checkmark")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Color.mcSurface)
                    }
                }
                .accessibilityHidden(true)

            self.content
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, Theme.Spacing.xs)
    }
}

/// A step with timer chips. The chips are buttons inside the row, so the row
/// marks itself done with a tap gesture rather than being a button itself.
private struct TimedStepRow: View {
    @Environment(CookingTimerStore.self) private var store
    @State private var openTimer: Int?

    let number: Int
    let instruction: String
    let isDone: Bool
    let timers: [LocatedTimer]
    let recipeId: Int
    let recipeName: String
    let onToggle: () -> Void

    var body: some View {
        StepRowLayout(number: self.number, isDone: self.isDone) {
            StepTimerText(
                instruction: self.instruction,
                located: self.timers,
                isDone: self.isDone,
                recipeId: self.recipeId,
                recipeName: self.recipeName,
                stepNumber: self.number,
                openTimer: self.$openTimer
            )
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: self.onToggle)
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(Text("Step \(self.number): \(self.instruction)"))
        .accessibilityValue(self.isDone ? Text("Done") : Text(verbatim: ""))
        .accessibilityAction(.default, self.onToggle)
        .accessibilityActions {
            ForEach(Array(self.timers.enumerated()), id: \.offset) { index, located in
                self.timerAction(index, located.timer)
            }
        }
    }

    /// VoiceOver reads the step as one element, so each timer is an action on it:
    /// a single duration starts at once; a range or a running timer opens the chip's
    /// popover, as a tap would.
    private func timerAction(_ index: Int, _ timer: InstructionTimer) -> some View {
        let running = self.store.timer(recipeId: self.recipeId, stepNumber: self.number, text: timer.text)
        let name: Text = running == nil
            ? Text("Start \(timer.timerLabel) timer, \(Self.spokenDuration(timer))")
            : Text("Show \(timer.timerLabel) timer")
        return Button {
            if running == nil, !timer.isRange {
                HapticManager.shared.lightTap()
                self.store.start(
                    recipeId: self.recipeId,
                    recipeName: self.recipeName,
                    stepNumber: self.number,
                    label: timer.timerLabel,
                    sourceText: timer.text,
                    seconds: timer.minSeconds
                )
            } else {
                self.openTimer = index
            }
        } label: {
            name
        }
    }

    /// "10 minutes", or "10 minutes to 12 minutes" for a range.
    private static func spokenDuration(_ timer: InstructionTimer) -> String {
        guard timer.isRange else { return TimerFormat.spoken(timer.minSeconds) }
        return String(localized: "\(TimerFormat.spoken(timer.minSeconds)) to \(TimerFormat.spoken(timer.maxSeconds))")
    }
}

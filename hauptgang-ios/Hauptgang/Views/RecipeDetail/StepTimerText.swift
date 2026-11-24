import SwiftUI
import UIKit

/// A step's text with each server timer drawn as a chip inside the sentence. A tap
/// on a single duration starts it; a range or a running timer opens a popover.
/// Chips are buttons, so a tap on one does not reach the row's mark-done gesture.
struct StepTimerText: View {
    @Environment(CookingTimerStore.self) private var store
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    let instruction: String
    let located: [LocatedTimer]
    let isDone: Bool
    let recipeId: Int
    let recipeName: String
    let stepNumber: Int
    /// The chip whose popover is open, shared with the row's accessibility actions.
    @Binding var openTimer: Int?

    private var words: [StepWord] {
        StepTimerLocator.words(in: self.instruction, located: self.located)
    }

    /// Words are separate views, so the space between words and lines is measured
    /// from the body font at the current text size, matching a plain step's `Text`.
    private var bodyFont: UIFont {
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(self.dynamicTypeSize))
        return UIFont.preferredFont(forTextStyle: .body, compatibleWith: traits)
    }

    var body: some View {
        let font = self.bodyFont
        WordFlowLayout(
            wordSpacing: (" " as NSString).size(withAttributes: [.font: font]).width,
            lineSpacing: font.leading
        ) {
            ForEach(Array(self.words.enumerated()), id: \.offset) { _, word in
                self.wordView(word)
            }
        }
        .font(.body)
        .foregroundStyle(self.isDone ? Color.mcBody : Color.mcInk)
    }

    @ViewBuilder
    private func wordView(_ word: StepWord) -> some View {
        if word.pieces.count == 1, case let .text(text) = word.pieces[0] {
            Text(verbatim: text)
        } else {
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                ForEach(Array(word.pieces.enumerated()), id: \.offset) { _, piece in
                    switch piece {
                    case let .text(text):
                        Text(verbatim: text)
                    case let .timer(index):
                        self.chip(index)
                    }
                }
            }
            // Above its neighbours, so the chip's enlarged hit area wins over them.
            .zIndex(1)
        }
    }

    private func chip(_ index: Int) -> some View {
        let instruction = self.located[index].timer
        let running = self.store.timer(recipeId: self.recipeId, stepNumber: self.stepNumber, text: instruction.text)
        return StepTimerChip(timer: instruction, running: running) {
            self.tapped(index)
        }
        .popover(
            isPresented: Binding(
                get: { self.openTimer == index },
                set: {
                    if !$0, self.openTimer == index {
                        self.openTimer = nil
                    }
                }
            ),
            attachmentAnchor: .rect(.bounds)
        ) {
            StepTimerPopover(
                timer: instruction,
                running: self.store.timer(
                    recipeId: self.recipeId,
                    stepNumber: self.stepNumber,
                    text: instruction.text
                ),
                onStart: { seconds in
                    self.openTimer = nil
                    self.start(instruction, seconds: seconds)
                },
                onClose: { self.openTimer = nil }
            )
            .presentationCompactAdaptation(.popover)
        }
    }

    private func tapped(_ index: Int) {
        let instruction = self.located[index].timer
        let running = self.store.timer(recipeId: self.recipeId, stepNumber: self.stepNumber, text: instruction.text)
        if running == nil, !instruction.isRange {
            self.start(instruction, seconds: instruction.minSeconds)
        } else {
            HapticManager.shared.selection()
            self.openTimer = index
        }
    }

    private func start(_ instruction: InstructionTimer, seconds: Int) {
        HapticManager.shared.lightTap()
        self.store.start(
            recipeId: self.recipeId,
            recipeName: self.recipeName,
            stepNumber: self.stepNumber,
            label: instruction.timerLabel,
            sourceText: instruction.text,
            seconds: seconds
        )
    }
}

// MARK: - Chip

/// The duration as an accent chip: a timer glyph while idle, a progress pie and
/// the countdown while running, solid accent with a bell when done. Its hit area
/// reaches past the drawn chip, so a wet finger finds it.
private struct StepTimerChip: View {
    @ScaledMetric(relativeTo: .body) private var glyphSize: CGFloat = 15

    let timer: InstructionTimer
    let running: CookingTimer?
    let action: () -> Void

    var body: some View {
        Button(action: self.action) {
            Group {
                if let running {
                    TimelineView(TimerTick.everySecond) { context in
                        self.content(running, at: context.date)
                    }
                } else {
                    self.content(nil, at: .now)
                }
            }
            .contentShape(Rectangle().inset(by: -11))
        }
        .buttonStyle(.plain)
        .accessibilityHidden(true)
    }

    private func content(_ running: CookingTimer?, at date: Date) -> some View {
        let finished = running?.isFinished(at: date) ?? false
        return HStack(alignment: .center, spacing: 4) {
            self.glyph(running, finished: finished, at: date)
                .frame(width: self.glyphSize, height: self.glyphSize)
            self.label(running, finished: finished, at: date)
        }
        .padding(.horizontal, 5)
        .background {
            let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
            ZStack {
                shape.fill(finished ? Color.mcAccent : running == nil ? Color.mcAccentTint : Color.mcAccentLine)
                if running == nil {
                    shape.strokeBorder(Color.mcAccentLine, lineWidth: 1)
                }
            }
            // Drawn past the line's height without making the line taller.
            .padding(.vertical, -2)
        }
    }

    @ViewBuilder
    private func glyph(_ running: CookingTimer?, finished: Bool, at date: Date) -> some View {
        if finished {
            Image(systemName: "bell.fill")
                .resizable()
                .scaledToFit()
                .foregroundStyle(Color.mcSurface)
                .padding(1)
        } else if let running {
            TimerProgressPie(progress: running.progress(at: date), isPaused: running.isPaused)
        } else {
            Image(systemName: "timer")
                .resizable()
                .scaledToFit()
                .fontWeight(.medium)
                .foregroundStyle(Color.mcAccent)
        }
    }

    private func label(_ running: CookingTimer?, finished: Bool, at date: Date) -> Text {
        let text = Text(verbatim: self.timer.text.replacingOccurrences(of: " ", with: "\u{00A0}"))
        guard let running else {
            return text.fontWeight(.medium).foregroundColor(Color.mcAccent)
        }
        let separator = Text(verbatim: "\u{00A0}·\u{00A0}")
        if finished {
            return (text + separator + Text("done", comment: "A timer whose time is up, as in \"1 minute · done\""))
                .fontWeight(.semibold)
                .foregroundColor(Color.mcSurface)
        }
        let clock = Text(verbatim: TimerFormat.clock(running.remaining(at: date)))
            .font(.mcMono(.body, weight: .medium))
            .monospacedDigit()
        return (text + separator + clock)
            .fontWeight(.medium)
            .foregroundColor(running.isPaused ? Color.mcBody : Color.mcAccent)
    }
}

import Foundation

/// A server timer found in its step's text.
struct LocatedTimer: Hashable {
    let timer: InstructionTimer
    let range: Range<String.Index>
}

/// One unbreakable run of a step's text: a word, or a timer with the characters
/// touching it ("(10 minutes)."), so punctuation never wraps away from a chip.
struct StepWord: Hashable {
    enum Piece: Hashable {
        case text(String)
        /// Index into the located timers.
        case timer(Int)
    }

    let pieces: [Piece]
    /// The step's text has a line break before this word.
    var startsLine = false
}

/// Finds each server timer's `text` in its step: the first occurrence after the
/// previous timer's match, preferring one that is not part of a longer word or
/// number ("1 minute" in "11 minutes"). A timer whose text is missing is skipped.
enum StepTimerLocator {
    static func locate(_ timers: [InstructionTimer], in step: String) -> [LocatedTimer] {
        var cursor = step.startIndex
        var located: [LocatedTimer] = []
        for timer in timers where !timer.text.isEmpty {
            guard let range = self.find(timer.text, in: step, from: cursor) else { continue }
            located.append(LocatedTimer(timer: timer, range: range))
            cursor = range.upperBound
        }
        return located
    }

    /// Splits the step at whitespace, keeping each located timer inside one word.
    static func words(in step: String, located: [LocatedTimer]) -> [StepWord] {
        var words: [StepWord] = []
        var pieces: [StepWord.Piece] = []
        var lineBreak = false

        func endWord() {
            if !pieces.isEmpty {
                words.append(StepWord(pieces: pieces, startsLine: lineBreak))
                lineBreak = false
            }
            pieces = []
        }

        func addText(_ text: Substring) {
            var buffer = ""
            for character in text {
                if character.isWhitespace {
                    if !buffer.isEmpty {
                        pieces.append(.text(buffer))
                    }
                    buffer = ""
                    endWord()
                    if character.isNewline, !words.isEmpty {
                        lineBreak = true
                    }
                } else {
                    buffer.append(character)
                }
            }
            if !buffer.isEmpty {
                pieces.append(.text(buffer))
            }
        }

        var cursor = step.startIndex
        for (index, timer) in located.enumerated() where timer.range.lowerBound >= cursor {
            addText(step[cursor ..< timer.range.lowerBound])
            pieces.append(.timer(index))
            cursor = timer.range.upperBound
        }
        addText(step[cursor...])
        endWord()
        return words
    }

    private static func find(_ text: String, in step: String, from start: String.Index) -> Range<String.Index>? {
        var firstMatch: Range<String.Index>?
        var searchStart = start
        while searchStart < step.endIndex,
              let range = step.range(of: text, range: searchStart ..< step.endIndex) {
            if self.standsAlone(range, in: step) {
                return range
            }
            firstMatch = firstMatch ?? range
            searchStart = step.index(after: range.lowerBound)
        }
        return firstMatch
    }

    private static func standsAlone(_ range: Range<String.Index>, in step: String) -> Bool {
        func joins(_ character: Character?) -> Bool {
            guard let character else { return false }
            return character.isLetter || character.isNumber
        }
        let before = range.lowerBound > step.startIndex ? step[step.index(before: range.lowerBound)] : nil
        let after = range.upperBound < step.endIndex ? step[range.upperBound] : nil
        return !joins(before) && !joins(after)
    }
}

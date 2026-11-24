@testable import Hauptgang
import XCTest

final class StepTimerLocatorTests: XCTestCase {
    func testFindsEachTimerInOrder() {
        let step = "Cook for 6–8 minutes, then simmer for 10 minutes."
        let located = StepTimerLocator.locate([self.timer("6–8 minutes"), self.timer("10 minutes")], in: step)

        XCTAssertEqual(located.map { String(step[$0.range]) }, ["6–8 minutes", "10 minutes"])
    }

    func testRepeatedTextMatchesTheNextOccurrence() {
        let step = "Fry for 3 minutes, flip, then fry for 3 minutes more."
        let located = StepTimerLocator.locate([self.timer("3 minutes"), self.timer("3 minutes")], in: step)

        XCTAssertEqual(located.count, 2)
        XCTAssertEqual(step.distance(from: step.startIndex, to: located[0].range.lowerBound), 8)
        XCTAssertEqual(step.distance(from: step.startIndex, to: located[1].range.lowerBound), 38)
    }

    func testMissingTextIsSkippedWithoutLosingLaterTimers() {
        let step = "Bake for 25 minutes, then rest for 5 minutes."
        let located = StepTimerLocator.locate(
            [self.timer("25 minutes"), self.timer("1 hour"), self.timer("5 minutes")],
            in: step
        )

        XCTAssertEqual(located.map(\.timer.text), ["25 minutes", "5 minutes"])
        XCTAssertEqual(String(step[located[1].range]), "5 minutes")
    }

    func testTextIsSearchedOnlyAfterThePreviousMatch() {
        let step = "Rest 5 minutes, bake 20 minutes."
        let located = StepTimerLocator.locate([self.timer("20 minutes"), self.timer("5 minutes")], in: step)

        XCTAssertEqual(located.map(\.timer.text), ["20 minutes"], "the earlier 5 minutes comes before the cursor")
    }

    func testPrefersAMatchThatIsNotPartOfALongerNumber() {
        let step = "Cook for 11 minutes, then 1 minute more."
        let located = StepTimerLocator.locate([self.timer("1 minute")], in: step)

        XCTAssertEqual(step.distance(from: step.startIndex, to: located[0].range.lowerBound), 26)
    }

    func testFallsBackToAnEmbeddedMatchWhenNothingStandsAlone() {
        let step = "Stir for 2 minutes."
        let located = StepTimerLocator.locate([self.timer("2 minute")], in: step)

        XCTAssertEqual(located.map { String(step[$0.range]) }, ["2 minute"])
    }

    func testEmojiAndAccentsBeforeTheTextKeepTheRangeAligned() {
        let step = "🔥🍝 Köcheln lassen: 10–12 Minuten, dann abgießen."
        let located = StepTimerLocator.locate([self.timer("10–12 Minuten")], in: step)

        XCTAssertEqual(located.map { String(step[$0.range]) }, ["10–12 Minuten"])
    }

    func testWordsKeepPunctuationWithTheChip() {
        let step = "Simmer (10 minutes). Serve  hot"
        let located = StepTimerLocator.locate([self.timer("10 minutes")], in: step)

        XCTAssertEqual(StepTimerLocator.words(in: step, located: located), [
            StepWord(pieces: [.text("Simmer")]),
            StepWord(pieces: [.text("("), .timer(0), .text(").")]),
            StepWord(pieces: [.text("Serve")]),
            StepWord(pieces: [.text("hot")])
        ])
    }

    private func timer(_ text: String) -> InstructionTimer {
        InstructionTimer(text: text, minSeconds: 60, maxSeconds: 60, label: "Cook")
    }
}

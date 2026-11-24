import Foundation
@testable import Hauptgang
import XCTest

@MainActor
final class CookingTimerStoreTests: XCTestCase {
    private var now = Date(timeIntervalSinceReferenceDate: 800_000_000)
    private var alerting: FakeTimerAlerting!
    private var store: CookingTimerStore!

    override func setUp() async throws {
        self.alerting = FakeTimerAlerting()
        self.alerting.now = { [unowned self] in self.now }
        self.store = CookingTimerStore(alerting: self.alerting, now: { [unowned self] in self.now })
    }

    // MARK: - Arithmetic

    func testStartCountsDownFromTheChosenSeconds() {
        let timer = self.startSimmer(seconds: 600)

        XCTAssertEqual(timer.remaining(at: self.now), 600)
        self.advance(150)
        XCTAssertEqual(self.current(timer).remaining(at: self.now), 450)
        XCTAssertEqual(self.current(timer).progress(at: self.now), 0.25, accuracy: 0.0001)
        XCTAssertEqual(self.alerting.calls, [.start(timer.id)])
    }

    func testPauseFreezesTheRemainingTimeAndResumeContinuesFromIt() {
        let timer = self.startSimmer(seconds: 600)
        self.advance(100)

        self.store.pause(timer.id)
        self.advance(1000)
        XCTAssertTrue(self.current(timer).isPaused)
        XCTAssertEqual(self.current(timer).remaining(at: self.now), 500)
        XCTAssertFalse(self.current(timer).isFinished(at: self.now))

        self.store.resume(timer.id)
        self.advance(200)
        XCTAssertEqual(self.current(timer).remaining(at: self.now), 300)
        XCTAssertEqual(self.alerting.calls, [.start(timer.id), .pause(timer.id), .resume(timer.id)])
    }

    func testPauseAndResumeIgnoreTimersAlreadyInThatState() {
        let timer = self.startSimmer(seconds: 60)
        self.store.resume(timer.id)
        self.store.pause(timer.id)
        self.store.pause(timer.id)
        self.advance(120)
        self.store.resume(timer.id)
        self.store.resume(timer.id)

        XCTAssertEqual(self.alerting.calls, [.start(timer.id), .pause(timer.id), .resume(timer.id)])
    }

    func testAddingAMinuteExtendsARunningTimerAndItsLength() {
        let timer = self.startSimmer(seconds: 300)
        self.advance(240)

        self.store.add(seconds: 60, to: timer.id)

        XCTAssertEqual(self.current(timer).remaining(at: self.now), 120)
        XCTAssertEqual(self.current(timer).duration, 360)
        XCTAssertEqual(self.alerting.calls.last, .reschedule(timer.id, remaining: 120))
    }

    func testAddingAMinuteToAPausedTimerKeepsItPaused() {
        let timer = self.startSimmer(seconds: 300)
        self.advance(100)
        self.store.pause(timer.id)

        self.store.add(seconds: 60, to: timer.id)
        self.advance(500)

        XCTAssertTrue(self.current(timer).isPaused)
        XCTAssertEqual(self.current(timer).remaining(at: self.now), 260)
    }

    func testAddingAMinuteToAFinishedTimerStartsItAgain() {
        let timer = self.startSimmer(seconds: 60)
        self.advance(90)
        XCTAssertTrue(self.current(timer).isFinished(at: self.now))

        self.store.add(seconds: 60, to: timer.id)

        XCTAssertFalse(self.current(timer).isFinished(at: self.now))
        XCTAssertEqual(self.current(timer).remaining(at: self.now), 60)
    }

    func testCancelRemovesTheTimerAndTheSystemTimer() {
        let timer = self.startSimmer(seconds: 60)

        self.store.cancel(timer.id)
        self.store.cancel(timer.id)

        XCTAssertTrue(self.store.timers.isEmpty)
        XCTAssertEqual(self.alerting.calls, [.start(timer.id), .cancel(timer.id)])
    }

    func testStartingTheSameDurationAgainReplacesItsTimer() {
        let first = self.startSimmer(seconds: 600)
        let second = self.startSimmer(seconds: 720)

        XCTAssertEqual(self.store.timers.map(\.id), [second.id])
        XCTAssertEqual(self.alerting.calls, [.start(first.id), .cancel(first.id), .start(second.id)])
    }

    func testTimerIsFoundByRecipeStepAndText() {
        let timer = self.startSimmer(seconds: 600)

        XCTAssertEqual(self.store.timer(recipeId: 7, stepNumber: 3, text: "10–12 minutes")?.id, timer.id)
        XCTAssertNil(self.store.timer(recipeId: 7, stepNumber: 2, text: "10–12 minutes"))
        XCTAssertNil(self.store.timer(recipeId: 8, stepNumber: 3, text: "10–12 minutes"))
    }

    // MARK: - Finishing

    func testNewlyFinishedReportsEachFinishOnce() {
        _ = self.startSimmer(seconds: 60)
        let other = self.store.start(
            recipeId: 7, recipeName: "Orzo", stepNumber: 1, label: "Cook gently",
            sourceText: "2 minutes", seconds: 120
        )

        XCTAssertFalse(self.store.takeNewlyFinished())
        self.advance(60)
        XCTAssertTrue(self.store.takeNewlyFinished())
        XCTAssertFalse(self.store.takeNewlyFinished())
        self.advance(60)
        XCTAssertTrue(self.store.takeNewlyFinished())

        self.store.add(seconds: 60, to: other.id)
        self.advance(60)
        XCTAssertTrue(self.store.takeNewlyFinished(), "a timer extended after finishing can finish again")
    }

    func testDisplayOrderPutsFinishedFirstThenSoonestThenPaused() {
        let long = self.startSimmer(seconds: 900)
        let paused = self.store.start(
            recipeId: 7, recipeName: "Orzo", stepNumber: 4, label: "Rest", sourceText: "1 minute", seconds: 60
        )
        let short = self.store.start(
            recipeId: 7, recipeName: "Orzo", stepNumber: 2, label: "Cook", sourceText: "6 minutes", seconds: 360
        )
        let done = self.store.start(
            recipeId: 9, recipeName: "Soup", stepNumber: 1, label: "Boil", sourceText: "30 seconds", seconds: 30
        )
        self.store.pause(paused.id)
        self.advance(45)

        XCTAssertEqual(self.store.sortedForDisplay(at: self.now).map(\.id), [done.id, short.id, long.id, paused.id])
    }

    // MARK: - System changes

    func testFollowsChangesMadeOutsideTheAppWithoutCallingBack() {
        let timer = self.startSimmer(seconds: 600)
        self.advance(100)

        self.alerting.systemChanged(.paused(timer.id, remaining: nil))
        XCTAssertEqual(self.current(timer).remaining(at: self.now), 500)
        self.advance(50)
        self.alerting.systemChanged(.resumed(timer.id, endDate: nil))
        self.advance(500)
        XCTAssertTrue(self.current(timer).isFinished(at: self.now))

        self.alerting.systemChanged(.restarted(
            timer.id, endDate: self.now.addingTimeInterval(60), addedSeconds: 60
        ))
        XCTAssertEqual(self.current(timer).remaining(at: self.now), 60)
        XCTAssertEqual(self.current(timer).duration, 660)

        self.alerting.systemChanged(.stopped(timer.id))
        XCTAssertTrue(self.store.timers.isEmpty)
        XCTAssertEqual(self.alerting.calls, [.start(timer.id)])
    }

    func testSystemTimingsWinOverWhenTheAppHeardOfTheChange() {
        let timer = self.startSimmer(seconds: 600)
        self.advance(300)

        // Paused from the lock screen at 400 seconds left; the app woke later.
        self.alerting.systemChanged(.paused(timer.id, remaining: 400))
        XCTAssertEqual(self.current(timer).remaining(at: self.now), 400)

        let resumedEnd = self.now.addingTimeInterval(380)
        self.alerting.systemChanged(.resumed(timer.id, endDate: resumedEnd))
        XCTAssertEqual(self.current(timer).endDate, resumedEnd)
        XCTAssertFalse(self.current(timer).isPaused)
    }

    // MARK: - Rebuild after the app was killed

    func testRestoreRebuildsTheTimersTheSystemStillHolds() async {
        let running = self.systemTimer(label: "Simmer", endingIn: 300)
        var paused = self.systemTimer(label: "Cook", endingIn: 0)
        paused.endDate = nil
        paused.pausedRemaining = 120
        self.alerting.systemTimers = [running, paused]

        await self.store.restoreFromSystem()

        XCTAssertEqual(self.store.timers.map(\.id), [running.id, paused.id])
        XCTAssertEqual(self.store.timer(recipeId: 7, stepNumber: 3, text: "Simmer text")?.remaining(at: self.now), 300)
        XCTAssertTrue(self.current(paused).isPaused)
        XCTAssertEqual(self.alerting.calls, [], "restoring must not schedule the timers again")
    }

    func testRestoreKeepsTimersAlreadyInTheListAndIsIdempotent() async {
        let started = self.startSimmer(seconds: 600)
        let restored = self.systemTimer(label: "Cook", endingIn: 60)
        self.alerting.systemTimers = [started, restored]

        await self.store.restoreFromSystem()
        await self.store.restoreFromSystem()

        XCTAssertEqual(self.store.timers.map(\.id), [started.id, restored.id])
    }

    func testRestoredTimerThatAlreadyRangDoesNotSignalAgain() async {
        self.alerting.systemTimers = [self.systemTimer(label: "Boil", endingIn: 0)]

        await self.store.restoreFromSystem()

        XCTAssertTrue(self.store.timers[0].isFinished(at: self.now))
        XCTAssertFalse(self.store.takeNewlyFinished())
    }

    // MARK: - Helpers

    @discardableResult
    private func startSimmer(seconds: Int) -> CookingTimer {
        self.store.start(
            recipeId: 7,
            recipeName: "Orzo",
            stepNumber: 3,
            label: "Simmer",
            sourceText: "10–12 minutes",
            seconds: seconds
        )
    }

    private func systemTimer(label: String, endingIn seconds: TimeInterval) -> CookingTimer {
        CookingTimer(
            id: UUID(),
            recipeId: 7,
            recipeName: "Orzo",
            stepNumber: 3,
            label: label,
            sourceText: "\(label) text",
            duration: max(seconds, 60),
            endDate: self.now.addingTimeInterval(seconds)
        )
    }

    private func current(_ timer: CookingTimer) -> CookingTimer {
        self.store.timers.first { $0.id == timer.id }!
    }

    private func advance(_ seconds: TimeInterval) {
        self.now = self.now.addingTimeInterval(seconds)
    }
}

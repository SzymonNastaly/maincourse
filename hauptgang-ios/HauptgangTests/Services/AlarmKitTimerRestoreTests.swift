import AlarmKit
@testable import Hauptgang
import XCTest

/// How a Live Activity's alarm state turns back into a timer after the app was killed.
@MainActor
final class AlarmKitTimerRestoreTests: XCTestCase {
    private let now = Date(timeIntervalSinceReferenceDate: 800_000_000)
    private let id = UUID()

    func testCountdownRestoresARunningTimerEndingAtTheFireDate() throws {
        guard #available(iOS 26.1, *) else { throw XCTSkip("AlarmKit needs iOS 26.1") }
        let fireDate = self.now.addingTimeInterval(200)
        let timer = AlarmKitTimerAlerting.timer(
            id: self.id,
            metadata: self.metadata(),
            mode: .countdown(.init(
                totalCountdownDuration: 600,
                previouslyElapsedDuration: 100,
                startDate: self.now.addingTimeInterval(-300),
                fireDate: fireDate
            )),
            now: self.now
        )

        XCTAssertEqual(timer.id, self.id)
        XCTAssertEqual(timer.remaining(at: self.now), 200)
        XCTAssertEqual(timer.duration, 660)
        XCTAssertEqual(timer.sourceText, "10–12 minutes")
        XCTAssertEqual(timer.stepNumber, 3)
        XCTAssertFalse(timer.isPaused)
    }

    func testPausedRestoresTheTimeLeft() throws {
        guard #available(iOS 26.1, *) else { throw XCTSkip("AlarmKit needs iOS 26.1") }
        let timer = AlarmKitTimerAlerting.timer(
            id: self.id,
            metadata: self.metadata(),
            mode: .paused(.init(totalCountdownDuration: 600, previouslyElapsedDuration: 240)),
            now: self.now
        )

        XCTAssertTrue(timer.isPaused)
        XCTAssertEqual(timer.remaining(at: self.now.addingTimeInterval(999)), 360)
    }

    func testAlertingRestoresAFinishedTimer() throws {
        guard #available(iOS 26.1, *) else { throw XCTSkip("AlarmKit needs iOS 26.1") }
        let timer = AlarmKitTimerAlerting.timer(
            id: self.id,
            metadata: self.metadata(),
            mode: .alert(.init(time: .init(hour: 12, minute: 0))),
            now: self.now
        )

        XCTAssertTrue(timer.isFinished(at: self.now))
    }

    @available(iOS 26.1, *)
    private func metadata() -> CookingTimerMetadata {
        CookingTimerMetadata(
            recipeId: 7,
            recipeName: "Orzo",
            stepNumber: 3,
            label: "Simmer",
            sourceText: "10–12 minutes",
            duration: 660
        )
    }
}

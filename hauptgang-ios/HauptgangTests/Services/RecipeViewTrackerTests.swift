@testable import Hauptgang
import XCTest

final class RecipeViewTrackerTests: XCTestCase {
    private var defaults: UserDefaults!
    private var suiteName: String!

    override func setUp() {
        super.setUp()
        self.suiteName = "RecipeViewTrackerTests.\(UUID().uuidString)"
        self.defaults = UserDefaults(suiteName: self.suiteName)
    }

    override func tearDown() {
        self.defaults.removePersistentDomain(forName: self.suiteName)
        super.tearDown()
    }

    func testFlushSendsRecordedViewsAndClearsTheQueue() async throws {
        let sink = MockRecipeViewSink()
        nonisolated(unsafe) let defaults = try XCTUnwrap(self.defaults)
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        await tracker.record(recipeId: 7, at: Date(timeIntervalSince1970: 1000))
        await tracker.record(recipeId: 9, at: Date(timeIntervalSince1970: 2000))
        await tracker.flush()

        XCTAssertEqual(sink.batches.count, 1)
        XCTAssertEqual(sink.batches.first?.map(\.recipeId), [7, 9])

        // A second flush with nothing queued must not hit the network at all.
        await tracker.flush()
        XCTAssertEqual(sink.batches.count, 1)
    }

    func testAFailedFlushKeepsTheViewsForNextTime() async throws {
        let sink = MockRecipeViewSink()
        sink.errorToThrow = APIError.networkError(URLError(.notConnectedToInternet))
        nonisolated(unsafe) let defaults = try XCTUnwrap(self.defaults)
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        await tracker.record(recipeId: 7, at: Date(timeIntervalSince1970: 1000))
        await tracker.flush()
        XCTAssertEqual(sink.batches.count, 1)

        sink.errorToThrow = nil
        await tracker.flush()

        XCTAssertEqual(sink.batches.count, 2)
        XCTAssertEqual(sink.batches.last?.map(\.recipeId), [7], "the view must survive a failed flush")
    }

    func testQueueSurvivesRelaunch() async throws {
        let sink = MockRecipeViewSink()
        nonisolated(unsafe) let defaults = try XCTUnwrap(self.defaults)
        let first = RecipeViewTracker(sink: sink, defaults: defaults)
        await first.record(recipeId: 42, at: Date(timeIntervalSince1970: 3000))

        // A fresh instance backed by the same defaults stands in for the next launch.
        let second = RecipeViewTracker(sink: sink, defaults: defaults)
        await second.flush()

        XCTAssertEqual(sink.batches.first?.map(\.recipeId), [42])
    }

    func testQueueIsCappedAndKeepsTheNewest() async throws {
        let sink = MockRecipeViewSink()
        nonisolated(unsafe) let defaults = try XCTUnwrap(self.defaults)
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        for id in 1 ... (RecipeViewTracker.maxQueued + 10) {
            await tracker.record(recipeId: id, at: Date(timeIntervalSince1970: TimeInterval(id)))
        }
        await tracker.flush()

        let sent = try XCTUnwrap(sink.batches.first)
        XCTAssertEqual(sent.count, RecipeViewTracker.maxQueued)
        XCTAssertEqual(sent.last?.recipeId, RecipeViewTracker.maxQueued + 10, "newest views must be kept")
        XCTAssertEqual(sent.first?.recipeId, 11, "oldest views are dropped first")
    }

    func testAViewRecordedDuringAnInFlightFlushSurvives() async throws {
        // Simulates a slow sink: while `send` is awaiting, a new view (a duplicate of one
        // already in the batch) is recorded. The post-flush reconciliation must drop only
        // the entries that were actually sent, not the newest matching-by-content entry.
        let sink = SlowMockRecipeViewSink()
        nonisolated(unsafe) let defaults = try XCTUnwrap(self.defaults)
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        await tracker.record(recipeId: 7, at: Date(timeIntervalSince1970: 1000))

        let flushTask = Task { await tracker.flush() }
        await sink.waitUntilSendStarted()
        await tracker.record(recipeId: 7, at: Date(timeIntervalSince1970: 1000))
        await sink.releaseSend()
        await flushTask.value

        var batches = await sink.batches
        XCTAssertEqual(batches.count, 1)
        XCTAssertEqual(batches.first?.map(\.recipeId), [7])

        await tracker.flush()

        batches = await sink.batches
        XCTAssertEqual(batches.count, 2, "the view recorded mid-flush must still be flushed")
        XCTAssertEqual(batches.last?.map(\.recipeId), [7])
    }

    func testResetClearsTheQueue() async throws {
        let sink = MockRecipeViewSink()
        nonisolated(unsafe) let defaults = try XCTUnwrap(self.defaults)
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        await tracker.record(recipeId: 7)
        await tracker.reset()
        await tracker.flush()

        XCTAssertTrue(sink.batches.isEmpty, "a signed-out user's views must not be sent under the next account")
    }

    func testAPIRecipeViewSinkPostsTheExpectedWireBody() async throws {
        let apiClient = MockAPIClient()
        let sink = APIRecipeViewSink(api: apiClient)
        let viewedAt = Date(timeIntervalSince1970: 1_700_000_000)

        try await sink.send([RecipeView(recipeId: 42, viewedAt: viewedAt)])

        let recorded = await apiClient.recorded
        let request = try XCTUnwrap(recorded.first)
        XCTAssertEqual(request.endpoint, "recipe_views")
        XCTAssertEqual(request.method, .post)

        let views = try XCTUnwrap(request.bodyArray("views"))
        XCTAssertEqual(views.count, 1)
        let view = try XCTUnwrap(views.first)
        XCTAssertEqual(view["recipe_id"] as? Int, 42)
        XCTAssertEqual(view["viewed_at"] as? String, ISO8601DateFormatter().string(from: viewedAt))
    }
}

import Foundation
@testable import Hauptgang

/// A `RecipeViewSink` whose `send` suspends until the test explicitly releases it, so a
/// test can record a new view while a flush is still in flight.
actor SlowMockRecipeViewSink: RecipeViewSink {
    private(set) var batches: [[RecipeView]] = []

    private var hasStarted = false
    private var canProceed = false
    private var sendStartedContinuation: CheckedContinuation<Void, Never>?
    private var releaseContinuation: CheckedContinuation<Void, Never>?

    func send(_ views: [RecipeView]) async throws {
        self.batches.append(views)
        self.hasStarted = true
        self.sendStartedContinuation?.resume()
        self.sendStartedContinuation = nil

        if !self.canProceed {
            await withCheckedContinuation { continuation in
                self.releaseContinuation = continuation
            }
        }
    }

    /// Suspends until `send` has been called at least once.
    func waitUntilSendStarted() async {
        if self.hasStarted { return }
        await withCheckedContinuation { continuation in
            self.sendStartedContinuation = continuation
        }
    }

    /// Lets an in-flight (or future) `send` return.
    func releaseSend() {
        self.canProceed = true
        self.releaseContinuation?.resume()
        self.releaseContinuation = nil
    }
}

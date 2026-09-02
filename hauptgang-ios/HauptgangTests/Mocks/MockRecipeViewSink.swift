import Foundation
@testable import Hauptgang

final class MockRecipeViewSink: RecipeViewSink, @unchecked Sendable {
    private let lock = NSLock()
    private var _batches: [[RecipeView]] = []
    private var _errorToThrow: Error?

    var batches: [[RecipeView]] {
        self.lock.withLock { self._batches }
    }

    var errorToThrow: Error? {
        get { self.lock.withLock { self._errorToThrow } }
        set { self.lock.withLock { self._errorToThrow = newValue } }
    }

    func send(_ views: [RecipeView]) async throws {
        self.lock.withLock { self._batches.append(views) }
        if let error = self.errorToThrow {
            throw error
        }
    }
}

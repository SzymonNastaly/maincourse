import Foundation
import os

private let logger = Logger(subsystem: "app.hauptgang.ios", category: "RecipeViewTracker")

/// One recorded "the user opened this recipe" event.
struct RecipeView: Codable, Equatable, Sendable {
    let recipeId: Int
    let viewedAt: Date
}

/// Where flushed views go. Split out so tests never touch the network.
protocol RecipeViewSink: Sendable {
    func send(_ views: [RecipeView]) async throws
}

/// Posts batches to `POST /api/v1/recipe_views`.
struct APIRecipeViewSink: RecipeViewSink {
    private let api: any APIClientProtocol

    init(api: any APIClientProtocol = APIClient.shared) {
        self.api = api
    }

    func send(_ views: [RecipeView]) async throws {
        try await self.api.requestVoid(
            endpoint: "recipe_views",
            method: .post,
            body: RecipeViewsRequest(views: views),
            queryItems: nil,
            authenticated: true
        )
    }
}

private struct RecipeViewsRequest: Encodable {
    let views: [RecipeView]
}

/// Buffers recipe views and flushes them to the backend opportunistically.
///
/// Views are the input to the Resurface campaign, which decides a recipe is forgotten
/// from the *absence* of a view. A dropped view therefore causes a wrong notification,
/// so the queue is persisted: a recipe opened offline, or opened just before the app is
/// killed, still gets reported on the next successful flush.
///
/// UserDefaults is the right store at this app's scale — the queue is a handful of
/// small structs, bounded by `maxQueued`.
actor RecipeViewTracker {
    static let shared = RecipeViewTracker()

    /// The server caps a batch at 500. Staying under that keeps a flush to one request.
    static let maxQueued = 200

    private static let queueKey = "recipeViews.pending"

    private let sink: any RecipeViewSink
    private let defaults: UserDefaults
    private var isFlushing = false

    init(sink: any RecipeViewSink = APIRecipeViewSink(), defaults: UserDefaults = .standard) {
        self.sink = sink
        self.defaults = defaults
    }

    /// Remember that the user opened a recipe. Cheap and non-throwing: call sites are
    /// UI code that must not care whether this succeeded.
    func record(recipeId: Int, at viewedAt: Date = Date()) {
        var queue = self.loadQueue()
        queue.append(RecipeView(recipeId: recipeId, viewedAt: viewedAt))
        if queue.count > Self.maxQueued {
            queue.removeFirst(queue.count - Self.maxQueued)
        }
        self.saveQueue(queue)
    }

    /// Send everything queued. On failure the queue is left intact for the next attempt.
    func flush() async {
        guard !self.isFlushing else { return }
        let queue = self.loadQueue()
        guard !queue.isEmpty else { return }

        self.isFlushing = true
        defer { isFlushing = false }

        do {
            try await self.sink.send(queue)
        } catch {
            logger.error("Failed to flush \(queue.count) recipe views: \(error.localizedDescription)")
            return
        }

        // Re-read rather than clearing outright: a view recorded while the request was
        // in flight would otherwise be lost.
        let remaining = Array(self.loadQueue().dropFirst(queue.count))
        self.saveQueue(remaining)
        logger.info("Flushed \(queue.count) recipe views")
    }

    /// Drop everything. Called on sign-out so one account's views never post under another.
    func reset() {
        self.defaults.removeObject(forKey: Self.queueKey)
    }

    // MARK: - Private

    private func loadQueue() -> [RecipeView] {
        guard let data = self.defaults.data(forKey: Self.queueKey) else { return [] }
        do {
            return try JSONDecoder().decode([RecipeView].self, from: data)
        } catch {
            logger.error("Discarding unreadable recipe view queue: \(error.localizedDescription)")
            self.defaults.removeObject(forKey: Self.queueKey)
            return []
        }
    }

    private func saveQueue(_ queue: [RecipeView]) {
        guard !queue.isEmpty else {
            self.defaults.removeObject(forKey: Self.queueKey)
            return
        }
        do {
            self.defaults.set(try JSONEncoder().encode(queue), forKey: Self.queueKey)
        } catch {
            logger.error("Failed to persist recipe view queue: \(error.localizedDescription)")
        }
    }
}

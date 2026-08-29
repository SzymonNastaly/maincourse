# Lifecycle Notifications — iOS Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the iOS half of lifecycle notifications — view pings, device time zone, notification tap routing, the opened/action callback, and a settings switch — so the backend's campaigns fire at the right local hour, and so the Resurface campaign can finally be turned on.

**Architecture:** Four independent client capabilities against endpoints that already exist and are deployed. View pings are buffered in a `UserDefaults`-backed queue inside an actor and flushed opportunistically (app foreground, background, next launch), so a recipe opened offline is still counted. Notification taps are parsed into a value type by a `nonisolated static` function (mirroring `DeepLinkRouter.extractInvitationToken`), handed to an `@Observable` router, and consumed by `RecipesView`'s existing `NavigationPath`. The settings switch reuses the existing `PATCH /api/v1/account` path that already accepts the field.

**Tech Stack:** Swift 6 (strict concurrency `complete`), SwiftUI, XCTest, XcodeGen, Rails 8.1 for the two small backend tasks.

**Spec:** `docs/superpowers/specs/2026-08-29-lifecycle-notifications-design.md`

**Backend state this plan builds on:** `main` @ `872dd98`, deployed. Every endpoint below is live.

## Global Constraints

- **Scale rule (AGENTS.md).** This app is used by a handful of people. Prefer pragmatic, working, sensible solutions for that scale — avoid architecture, abstraction, or infrastructure justified only by large user counts. A `UserDefaults` array is the right store for the view queue here; a SwiftData model is not.
- **Swift 6 strict concurrency is `complete`** (`hauptgang-ios/project.yml`, `SWIFT_STRICT_CONCURRENCY: complete`). Every new type crossing an isolation boundary must be `Sendable`. Follow the existing pattern: services are `actor` or `final class ... @unchecked Sendable`, view models are `@MainActor @Observable`.
- **iOS deployment target 18.0**, `SWIFT_VERSION 5.9`, Xcode 16.
- **XcodeGen globs source directories** (`sources: - path: Hauptgang`). New `.swift` files under `Hauptgang/` and `HauptgangTests/` need **no** `project.yml` edit. Do not add one.
- **JSON casing is automatic.** `APIClient` sets `keyDecodingStrategy = .convertFromSnakeCase` and `keyEncodingStrategy = .convertToSnakeCase` (`APIClient.swift:20,41`). Write Swift properties in camelCase; never add `CodingKeys` to map snake_case.
- **Dates are ISO8601 on the wire** (`dateEncodingStrategy = .iso8601`).
- **No new SPM dependencies.** The only packages are RevenueCat, GRDB, Sentry.
- **iOS tests:** XCTest, `@testable import Hauptgang`, hand-written mocks conforming to a protocol in `HauptgangTests/Mocks/`. Run with `bin/ios-test` (auto-finds a simulator, regenerates the project first). macOS only.
- **Rails tests:** `bin/rails test`. **Do NOT run `bin/ci`** — its "Tests: System" step needs Chrome/chromedriver, which is not installed on this machine, and it will hang. The gate for Rails tasks is `bin/rails test` + `bin/rubocop`.
- **Ruby style** is rubocop-rails-omakase.
- Commit after each task with a conventional-commit message.

## Endpoint contracts (already deployed — do not change them)

| Endpoint | Body | Response |
|---|---|---|
| `POST /api/v1/device_tokens` | `{token, environment, time_zone}` — `time_zone` is an IANA identifier; unknown values are silently ignored server-side | `201 {id, token, environment}` |
| `POST /api/v1/recipe_views` | `{views: [{recipe_id, viewed_at}]}` — array required, capped at 500 server-side, entries for inaccessible recipes are skipped | `204` |
| `POST /api/v1/notification_deliveries/:id/opened` | `{action_taken?}` — optional string | `204`, or `404` if the delivery is not this user's |
| `PATCH /api/v1/account` | `{user: {name?, lifecycle_notifications_enabled?}}` | `200 {user: {id, name, email, lifecycle_notifications_enabled}}` |

**APNs custom payload** sent by `Notifications::Deliver` (`app/models/notifications/deliver.rb:70-77`), alongside the standard `aps` alert. Keys are omitted when nil (`.compact`):

```json
{ "campaign": "import_follow_up", "delivery_id": 42, "recipe_id": 7, "cookbook_id": 3 }
```

`campaign` is one of `"import_follow_up"`, `"stale_shopping_list"`, `"resurface"`. `recipe_id` is absent for `stale_shopping_list`.

## File Structure

**Create:**
- `hauptgang-ios/Hauptgang/Services/RecipeViewTracker.swift` — actor owning the pending-view queue and its flush. One responsibility: "remember which recipes were opened, and get that to the server eventually."
- `hauptgang-ios/Hauptgang/Models/LifecycleNotificationPayload.swift` — value type + parser for the APNs custom payload. Pure, no I/O, so it is trivially testable.
- `hauptgang-ios/Hauptgang/Services/NotificationDeliveryService.swift` — the `opened` callback. Separate from `PushNotificationService`, which owns registration only.
- `hauptgang-ios/Hauptgang/ViewModels/NotificationRouter.swift` — `@MainActor @Observable` holder for a pending route, consumed by the view layer. Mirrors `DeepLinkRouter`.
- `hauptgang-ios/HauptgangTests/Services/RecipeViewTrackerTests.swift`
- `hauptgang-ios/HauptgangTests/Models/LifecycleNotificationPayloadTests.swift`
- `hauptgang-ios/HauptgangTests/ViewModels/NotificationRouterTests.swift`
- `hauptgang-ios/HauptgangTests/Mocks/MockRecipeViewSink.swift`
- `hauptgang-ios/HauptgangTests/Mocks/MockNotificationDeliveryService.swift`

**Modify:**
- `app/controllers/api/v1/sessions_controller.rb`, `app/controllers/api/v1/registrations_controller.rb` — add the pref to the user JSON (Task 1).
- `hauptgang-ios/Hauptgang/Services/PushNotificationService.swift` — add `timeZone` to `RegisterRequest` (Task 2).
- `hauptgang-ios/Hauptgang/ViewModels/RecipeDetailViewModel.swift` — record a view on successful load (Task 4).
- `hauptgang-ios/Hauptgang/App/HauptgangApp.swift` — flush on scene phase change (Task 4).
- `hauptgang-ios/Hauptgang/App/AppDelegate.swift` — become `UNUserNotificationCenterDelegate` (Task 6).
- `hauptgang-ios/Hauptgang/Views/MainTabView.swift`, `hauptgang-ios/Hauptgang/Views/RecipesView.swift` — consume the pending route (Task 6).
- `hauptgang-ios/Hauptgang/Models/User.swift`, `hauptgang-ios/Hauptgang/Services/AuthService.swift`, `hauptgang-ios/Hauptgang/Services/AuthServiceProtocol.swift`, `hauptgang-ios/Hauptgang/ViewModels/AuthManager.swift`, `hauptgang-ios/Hauptgang/Views/SettingsView.swift`, `hauptgang-ios/HauptgangTests/Mocks/MockAuthService.swift` — the settings switch (Task 7).
- `app/jobs/evaluate_lifecycle_notifications_job.rb`, `app/models/notifications/resurface_campaign.rb`, `test/jobs/evaluate_lifecycle_notifications_job_test.rb` — enable Resurface (Task 8, **gated on the release actually shipping**).

---

### Task 1: Expose the notification preference on login and signup

The settings switch in Task 7 needs to render the *current* value. Today `lifecycle_notifications_enabled` is returned only by `PATCH /api/v1/account` — the write path. A user who signs in on a new device has no way to learn their own setting.

**Files:**
- Modify: `app/controllers/api/v1/sessions_controller.rb:22`
- Modify: `app/controllers/api/v1/registrations_controller.rb:26`
- Test: `test/controllers/api/v1/sessions_controller_test.rb`, `test/controllers/api/v1/registrations_controller_test.rb`

**Interfaces:**
- Produces: `POST /api/v1/session` and `POST /api/v1/registration` both return `user: {id, name, email, lifecycle_notifications_enabled}`.

- [ ] **Step 1: Write the failing tests**

Append to `test/controllers/api/v1/sessions_controller_test.rb` (inside the existing class):

```ruby
test "login returns the lifecycle notification preference" do
  users(:one).update!(lifecycle_notifications_enabled: false)

  post api_v1_session_url, params: { email: users(:one).email_address, password: "password" }, as: :json

  assert_response :created
  assert_equal false, response.parsed_body.dig("user", "lifecycle_notifications_enabled")
end
```

Append to `test/controllers/api/v1/registrations_controller_test.rb`:

```ruby
test "signup returns the lifecycle notification preference" do
  post api_v1_registration_url, params: {
    name: "New", email: "new-pref@example.com", password: "password123", password_confirmation: "password123"
  }, as: :json

  assert_response :created
  assert_equal true, response.parsed_body.dig("user", "lifecycle_notifications_enabled")
end
```

Check the fixture password first: `grep -rn "password" test/fixtures/users.yml`. If sign-in tests elsewhere in the file use a different literal, use theirs.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `bin/rails test test/controllers/api/v1/sessions_controller_test.rb test/controllers/api/v1/registrations_controller_test.rb`
Expected: FAIL — `Expected: false / Actual: nil` (the key is absent).

- [ ] **Step 3: Add the field to both responses**

In `sessions_controller.rb`, change the render block's user hash to:

```ruby
            user: {
              id: user.id,
              name: user.name,
              email: user.email_address,
              lifecycle_notifications_enabled: user.lifecycle_notifications_enabled
            }
```

Make the identical change in `registrations_controller.rb`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `bin/rails test test/controllers/api/v1/sessions_controller_test.rb test/controllers/api/v1/registrations_controller_test.rb`
Expected: PASS.

- [ ] **Step 5: Run the full Rails gate**

Run: `bin/rails test && bin/rubocop`
Expected: 0 failures, no offenses. (Do not run `bin/ci`.)

- [ ] **Step 6: Commit**

```bash
git add app/controllers/api/v1/sessions_controller.rb app/controllers/api/v1/registrations_controller.rb test/controllers/api/v1/sessions_controller_test.rb test/controllers/api/v1/registrations_controller_test.rb
git commit -m "feat: return the lifecycle notification preference on login and signup"
```

---

### Task 2: Send the device time zone when registering a push token

Until this ships, every user's `time_zone` is the `"UTC"` column default, so the backend's 17:00 local send window is 17:00 UTC for everyone. This is the highest-value task in the plan and the smallest.

**Files:**
- Modify: `hauptgang-ios/Hauptgang/Services/PushNotificationService.swift:114-145`
- Test: `hauptgang-ios/HauptgangTests/Services/PushNotificationServiceTests.swift` (create)

**Interfaces:**
- Produces: `RegisterRequest { token, environment, timeZone }`, encoded as `{"token":…,"environment":…,"time_zone":"Europe/Zurich"}`.

- [ ] **Step 1: Write the failing test**

`PushNotificationService` currently takes `api: any APIClientProtocol`, so it is already injectable. Create `hauptgang-ios/HauptgangTests/Mocks/MockAPIClient.swift`:

```swift
import Foundation
@testable import Hauptgang

/// Records outgoing requests and returns canned responses. Encodes the body with the
/// same strategy as APIClient so tests can assert on the real wire format.
actor MockAPIClient: APIClientProtocol {
    struct Recorded {
        let endpoint: String
        let method: HTTPMethod
        let bodyJSON: [String: Any]?
    }

    private(set) var recorded: [Recorded] = []
    var responseData: Data = Data("{}".utf8)
    var errorToThrow: Error?

    func request<T: Decodable>(
        endpoint: String,
        method: HTTPMethod,
        body: Encodable?,
        queryItems _: [URLQueryItem]?,
        authenticated _: Bool
    ) async throws -> T {
        self.record(endpoint: endpoint, method: method, body: body)
        if let error = self.errorToThrow { throw error }
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(T.self, from: self.responseData)
    }

    func requestVoid(
        endpoint: String,
        method: HTTPMethod,
        body: Encodable?,
        queryItems _: [URLQueryItem]?,
        authenticated _: Bool
    ) async throws {
        self.record(endpoint: endpoint, method: method, body: body)
        if let error = self.errorToThrow { throw error }
    }

    func uploadMultipart<T: Decodable>(
        endpoint: String,
        method: HTTPMethod,
        file _: MultipartFile,
        authenticated _: Bool
    ) async throws -> T {
        self.record(endpoint: endpoint, method: method, body: nil)
        if let error = self.errorToThrow { throw error }
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(T.self, from: self.responseData)
    }

    private func record(endpoint: String, method: HTTPMethod, body: Encodable?) {
        var json: [String: Any]?
        if let body {
            let encoder = JSONEncoder()
            encoder.keyEncodingStrategy = .convertToSnakeCase
            encoder.dateEncodingStrategy = .iso8601
            if let data = try? encoder.encode(body) {
                json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            }
        }
        self.recorded.append(Recorded(endpoint: endpoint, method: method, bodyJSON: json))
    }
}
```

Create `hauptgang-ios/HauptgangTests/Services/PushNotificationServiceTests.swift`:

```swift
@testable import Hauptgang
import XCTest

final class PushNotificationServiceTests: XCTestCase {
    private var defaults: UserDefaults!
    private var suiteName: String!

    override func setUp() {
        super.setUp()
        self.suiteName = "PushNotificationServiceTests.\(UUID().uuidString)"
        self.defaults = UserDefaults(suiteName: self.suiteName)
    }

    override func tearDown() {
        self.defaults.removePersistentDomain(forName: self.suiteName)
        super.tearDown()
    }

    func testRegistrationIncludesTheDeviceTimeZone() async throws {
        let api = MockAPIClient()
        await api.setResponse(#"{"id":1,"token":"abc","environment":"sandbox"}"#)
        let service = PushNotificationService(api: api, defaults: self.defaults)

        await service.setAuthenticated(true)
        await service.handleDeviceToken(Data([0xAB, 0xCD]))

        let recorded = await api.recorded
        let register = try XCTUnwrap(recorded.first(where: { $0.endpoint == "device_tokens" }))
        XCTAssertEqual(register.bodyJSON?["time_zone"] as? String, TimeZone.current.identifier)
        XCTAssertEqual(register.bodyJSON?["token"] as? String, "abcd")
    }
}
```

Add this helper to `MockAPIClient` so the test can seed a response:

```swift
    func setResponse(_ json: String) {
        self.responseData = Data(json.utf8)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `bin/ios-test`
Expected: FAIL — `XCTAssertEqual failed: ("nil") is not equal to ("Europe/Zurich")`, because `RegisterRequest` has no `timeZone`.

- [ ] **Step 3: Add the field**

In `PushNotificationService.swift`, change the wire format struct at the bottom of the file:

```swift
private struct RegisterRequest: Encodable {
    let token: String
    let environment: String
    let timeZone: String
}
```

And in `uploadIfNeeded(token:)`, change the body construction:

```swift
        let body = RegisterRequest(
            token: token,
            environment: environment,
            timeZone: TimeZone.current.identifier
        )
```

- [ ] **Step 4: Handle the re-registration short-circuit**

`uploadIfNeeded` returns early when the cached token and environment both match, which would pin a user's time zone to wherever they first registered — they would never re-send after moving or travelling. Add the time zone to the cache key. Add the key constant beside the other two:

```swift
    private static let lastUploadedTimeZoneKey = "push.lastUploadedTimeZone"
```

Change the short-circuit and the cache write in `uploadIfNeeded`:

```swift
        let timeZone = TimeZone.current.identifier

        let cachedToken = self.defaults.string(forKey: Self.lastUploadedTokenKey)
        let cachedEnvironment = self.defaults.string(forKey: Self.lastUploadedEnvironmentKey)
        let cachedTimeZone = self.defaults.string(forKey: Self.lastUploadedTimeZoneKey)
        if cachedToken == token, cachedEnvironment == environment, cachedTimeZone == timeZone {
            return
        }

        let body = RegisterRequest(token: token, environment: environment, timeZone: timeZone)
```

…and after a successful upload:

```swift
            self.defaults.set(timeZone, forKey: Self.lastUploadedTimeZoneKey)
```

Also add it to the `unregister()` cleanup, beside the other two `removeObject` calls:

```swift
            self.defaults.removeObject(forKey: Self.lastUploadedTimeZoneKey)
```

- [ ] **Step 5: Write the re-registration test**

```swift
    func testTimeZoneChangeTriggersReRegistration() async throws {
        let api = MockAPIClient()
        await api.setResponse(#"{"id":1,"token":"abcd","environment":"sandbox"}"#)
        let service = PushNotificationService(api: api, defaults: self.defaults)

        await service.setAuthenticated(true)
        await service.handleDeviceToken(Data([0xAB, 0xCD]))
        await service.handleDeviceToken(Data([0xAB, 0xCD]))

        // Same token, same environment, same zone: the second call is a no-op.
        let afterTwo = await api.recorded.filter { $0.endpoint == "device_tokens" }.count
        XCTAssertEqual(afterTwo, 1)

        // Simulate the device moving to another zone.
        self.defaults.set("Pacific/Auckland", forKey: "push.lastUploadedTimeZone")
        await service.handleDeviceToken(Data([0xAB, 0xCD]))

        let afterMove = await api.recorded.filter { $0.endpoint == "device_tokens" }.count
        XCTAssertEqual(afterMove, 2, "a changed time zone must re-register")
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `bin/ios-test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add hauptgang-ios/Hauptgang/Services/PushNotificationService.swift hauptgang-ios/HauptgangTests/Services/PushNotificationServiceTests.swift hauptgang-ios/HauptgangTests/Mocks/MockAPIClient.swift
git commit -m "feat: send the device time zone when registering a push token"
```

---

### Task 3: RecipeViewTracker — a durable queue for view pings

The backend's Resurface campaign infers "forgotten" from `last_viewed_at IS NULL`. Getting that wrong in the *silent* direction (a view we failed to report) means telling someone to rediscover a recipe they read yesterday. So a view opened offline must survive until the next successful flush, and must survive app termination.

**Files:**
- Create: `hauptgang-ios/Hauptgang/Services/RecipeViewTracker.swift`
- Create: `hauptgang-ios/HauptgangTests/Mocks/MockRecipeViewSink.swift`
- Test: `hauptgang-ios/HauptgangTests/Services/RecipeViewTrackerTests.swift`

**Interfaces:**
- Consumes: `APIClientProtocol` (existing).
- Produces:
  - `protocol RecipeViewSink: Sendable { func send(_ views: [RecipeView]) async throws }`
  - `struct RecipeView: Codable, Equatable, Sendable { let recipeId: Int; let viewedAt: Date }`
  - `actor RecipeViewTracker` with `static let shared`, `init(sink:defaults:)`, `func record(recipeId: Int, at: Date = Date()) async`, `func flush() async`, `func reset() async`.

- [ ] **Step 1: Write the failing tests**

Create `hauptgang-ios/HauptgangTests/Mocks/MockRecipeViewSink.swift`:

```swift
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
        if let error = self.errorToThrow { throw error }
    }
}
```

Create `hauptgang-ios/HauptgangTests/Services/RecipeViewTrackerTests.swift`:

```swift
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
        let tracker = RecipeViewTracker(sink: sink, defaults: self.defaults)

        await tracker.record(recipeId: 7, at: Date(timeIntervalSince1970: 1_000))
        await tracker.record(recipeId: 9, at: Date(timeIntervalSince1970: 2_000))
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
        let tracker = RecipeViewTracker(sink: sink, defaults: self.defaults)

        await tracker.record(recipeId: 7, at: Date(timeIntervalSince1970: 1_000))
        await tracker.flush()
        XCTAssertEqual(sink.batches.count, 1)

        sink.errorToThrow = nil
        await tracker.flush()

        XCTAssertEqual(sink.batches.count, 2)
        XCTAssertEqual(sink.batches.last?.map(\.recipeId), [7], "the view must survive a failed flush")
    }

    func testQueueSurvivesRelaunch() async throws {
        let sink = MockRecipeViewSink()
        let first = RecipeViewTracker(sink: sink, defaults: self.defaults)
        await first.record(recipeId: 42, at: Date(timeIntervalSince1970: 3_000))

        // A fresh instance backed by the same defaults stands in for the next launch.
        let second = RecipeViewTracker(sink: sink, defaults: self.defaults)
        await second.flush()

        XCTAssertEqual(sink.batches.first?.map(\.recipeId), [42])
    }

    func testQueueIsCappedAndKeepsTheNewest() async throws {
        let sink = MockRecipeViewSink()
        let tracker = RecipeViewTracker(sink: sink, defaults: self.defaults)

        for id in 1...(RecipeViewTracker.maxQueued + 10) {
            await tracker.record(recipeId: id, at: Date(timeIntervalSince1970: TimeInterval(id)))
        }
        await tracker.flush()

        let sent = try XCTUnwrap(sink.batches.first)
        XCTAssertEqual(sent.count, RecipeViewTracker.maxQueued)
        XCTAssertEqual(sent.last?.recipeId, RecipeViewTracker.maxQueued + 10, "newest views must be kept")
        XCTAssertEqual(sent.first?.recipeId, 11, "oldest views are dropped first")
    }

    func testResetClearsTheQueue() async throws {
        let sink = MockRecipeViewSink()
        let tracker = RecipeViewTracker(sink: sink, defaults: self.defaults)

        await tracker.record(recipeId: 7)
        await tracker.reset()
        await tracker.flush()

        XCTAssertTrue(sink.batches.isEmpty, "a signed-out user's views must not be sent under the next account")
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `bin/ios-test`
Expected: FAIL to compile — `cannot find 'RecipeViewTracker' in scope`.

- [ ] **Step 3: Implement the tracker**

Create `hauptgang-ios/Hauptgang/Services/RecipeViewTracker.swift`:

```swift
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `bin/ios-test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add hauptgang-ios/Hauptgang/Services/RecipeViewTracker.swift hauptgang-ios/HauptgangTests/Services/RecipeViewTrackerTests.swift hauptgang-ios/HauptgangTests/Mocks/MockRecipeViewSink.swift
git commit -m "feat: add a durable queue for recipe view pings"
```

---

### Task 4: Record a view when a recipe is opened, and flush on app lifecycle

**Files:**
- Modify: `hauptgang-ios/Hauptgang/ViewModels/RecipeDetailViewModel.swift:17-23` (init), `:30` (`loadRecipe`)
- Modify: `hauptgang-ios/Hauptgang/App/HauptgangApp.swift`
- Modify: `hauptgang-ios/Hauptgang/ViewModels/AuthManager.swift:70` (`signOut`)
- Test: `hauptgang-ios/HauptgangTests/ViewModels/RecipeDetailViewModelTests.swift` (existing file — append)

**Interfaces:**
- Consumes: `RecipeViewTracker.record(recipeId:at:)`, `RecipeViewTracker.flush()`, `RecipeViewTracker.reset()` from Task 3.

- [ ] **Step 1: Write the failing test**

Read the top of `hauptgang-ios/HauptgangTests/ViewModels/RecipeDetailViewModelTests.swift` first to match its existing setup (it already builds a `RecipeDetailViewModel` with `MockRecipeService` and `MockRecipeRepository`). Append:

```swift
    func testOpeningARecipeRecordsAView() async throws {
        let suiteName = "RecipeDetailViewModelTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let sink = MockRecipeViewSink()
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        let service = MockRecipeService()
        service.detailResult = Self.sampleDetail(id: 7)
        let viewModel = RecipeDetailViewModel(
            recipeService: service,
            repository: MockRecipeRepository(),
            viewTracker: tracker
        )

        await viewModel.loadRecipe(id: 7)
        await tracker.flush()

        XCTAssertEqual(sink.batches.first?.map(\.recipeId), [7])
    }

    func testAFailedLoadDoesNotRecordAView() async throws {
        let suiteName = "RecipeDetailViewModelTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let sink = MockRecipeViewSink()
        let tracker = RecipeViewTracker(sink: sink, defaults: defaults)

        let service = MockRecipeService()
        service.shouldThrow = true
        let viewModel = RecipeDetailViewModel(
            recipeService: service,
            repository: MockRecipeRepository(),
            viewTracker: tracker
        )

        await viewModel.loadRecipe(id: 7)
        await tracker.flush()

        XCTAssertTrue(sink.batches.isEmpty, "a recipe that failed to load was not read")
    }
```

`Self.sampleDetail(id:)` and the mock's property names must match what the existing tests in this file already use — read them and reuse, do not invent new ones. If the file has no such helper, build the `RecipeDetail` inline the same way its neighbouring tests do.

- [ ] **Step 2: Run the test to verify it fails**

Run: `bin/ios-test`
Expected: FAIL to compile — `extra argument 'viewTracker' in call`.

- [ ] **Step 3: Inject the tracker and record the view**

In `RecipeDetailViewModel.swift`, add the stored property and init parameter:

```swift
    private let viewTracker: RecipeViewTracker

    init(
        recipeService: RecipeServiceProtocol = RecipeService.shared,
        repository: RecipeRepositoryProtocol? = nil,
        viewTracker: RecipeViewTracker = .shared
    ) {
        self.recipeService = recipeService
        self.repository = repository ?? RecipeRepository()
        self.viewTracker = viewTracker
    }
```

In `loadRecipe(id:)`, record the view immediately after the staleness guard passes and the recipe has been assigned from the API — that is, right after `self.recipe = apiRecipe`:

```swift
            self.recipe = apiRecipe
            self.logger.info("Successfully loaded recipe from API: \(apiRecipe.name)")

            // Fire-and-forget: the tracker persists and retries on its own, and a view
            // ping must never delay or fail the screen the user is looking at.
            Task { await self.viewTracker.record(recipeId: id) }
```

Record on the *API-loaded* path only. A cache-only render after a failed fetch is ambiguous, and the campaigns tolerate a missed view far better than a fabricated one.

- [ ] **Step 4: Flush on app lifecycle**

Read `hauptgang-ios/Hauptgang/App/HauptgangApp.swift`. Add `@Environment(\.scenePhase) private var scenePhase` to the `App` struct if it is not already there, and attach to the top-level `WindowGroup`'s content:

```swift
            .onChange(of: self.scenePhase) { _, newPhase in
                // Flush when the app is put away and again when it comes back: those are
                // the two moments a network is most likely to be available and the user
                // is not waiting on us.
                guard newPhase == .background || newPhase == .active else { return }
                Task { await RecipeViewTracker.shared.flush() }
            }
```

- [ ] **Step 5: Clear the queue on sign-out**

In `AuthManager.swift`'s `signOut()`, alongside the existing cleanup calls, add:

```swift
        await RecipeViewTracker.shared.reset()
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `bin/ios-test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add hauptgang-ios/Hauptgang/ViewModels/RecipeDetailViewModel.swift hauptgang-ios/Hauptgang/App/HauptgangApp.swift hauptgang-ios/Hauptgang/ViewModels/AuthManager.swift hauptgang-ios/HauptgangTests/ViewModels/RecipeDetailViewModelTests.swift
git commit -m "feat: record a recipe view when a detail screen loads"
```

---

### Task 5: Parse the notification payload and report deliveries as opened

**Files:**
- Create: `hauptgang-ios/Hauptgang/Models/LifecycleNotificationPayload.swift`
- Create: `hauptgang-ios/Hauptgang/Services/NotificationDeliveryService.swift`
- Create: `hauptgang-ios/HauptgangTests/Mocks/MockNotificationDeliveryService.swift`
- Test: `hauptgang-ios/HauptgangTests/Models/LifecycleNotificationPayloadTests.swift`

**Interfaces:**
- Produces:
  - `struct LifecycleNotificationPayload: Equatable, Sendable { let campaign: String; let deliveryId: Int; let recipeId: Int?; let cookbookId: Int? }`
  - `static func parse(_ userInfo: [AnyHashable: Any]) -> LifecycleNotificationPayload?`
  - `protocol NotificationDeliveryServiceProtocol: Sendable { func markOpened(deliveryId: Int, actionTaken: String?) async }`
  - `final class NotificationDeliveryService: NotificationDeliveryServiceProtocol` with `static let shared`.

- [ ] **Step 1: Write the failing tests**

Create `hauptgang-ios/HauptgangTests/Models/LifecycleNotificationPayloadTests.swift`:

```swift
@testable import Hauptgang
import XCTest

final class LifecycleNotificationPayloadTests: XCTestCase {
    func testParsesAFullPayload() {
        let userInfo: [AnyHashable: Any] = [
            "campaign": "import_follow_up",
            "delivery_id": 42,
            "recipe_id": 7,
            "cookbook_id": 3
        ]

        let payload = LifecycleNotificationPayload.parse(userInfo)

        XCTAssertEqual(payload, LifecycleNotificationPayload(
            campaign: "import_follow_up", deliveryId: 42, recipeId: 7, cookbookId: 3
        ))
    }

    func testParsesAPayloadWithoutARecipe() {
        // stale_shopping_list carries no recipe_id — the key is omitted, not null.
        let userInfo: [AnyHashable: Any] = [
            "campaign": "stale_shopping_list",
            "delivery_id": 43,
            "cookbook_id": 3
        ]

        let payload = LifecycleNotificationPayload.parse(userInfo)

        XCTAssertEqual(payload?.campaign, "stale_shopping_list")
        XCTAssertEqual(payload?.deliveryId, 43)
        XCTAssertNil(payload?.recipeId)
        XCTAssertEqual(payload?.cookbookId, 3)
    }

    func testParsesNumbersDeliveredAsStrings() {
        // APNs JSON round-trips through NSNumber, but be forgiving: a value that arrives
        // as a string must not silently drop the whole notification.
        let userInfo: [AnyHashable: Any] = [
            "campaign": "resurface",
            "delivery_id": "44",
            "recipe_id": "9"
        ]

        let payload = LifecycleNotificationPayload.parse(userInfo)

        XCTAssertEqual(payload?.deliveryId, 44)
        XCTAssertEqual(payload?.recipeId, 9)
    }

    func testRejectsANonLifecycleNotification() {
        XCTAssertNil(LifecycleNotificationPayload.parse(["aps": ["alert": "hi"]]))
        XCTAssertNil(LifecycleNotificationPayload.parse(["campaign": "import_follow_up"]))
        XCTAssertNil(LifecycleNotificationPayload.parse(["delivery_id": 42]))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `bin/ios-test`
Expected: FAIL to compile — `cannot find 'LifecycleNotificationPayload' in scope`.

- [ ] **Step 3: Implement the payload**

Create `hauptgang-ios/Hauptgang/Models/LifecycleNotificationPayload.swift`:

```swift
import Foundation

/// The custom keys `Notifications::Deliver` attaches to a lifecycle push, alongside `aps`.
///
/// Keys are omitted server-side when nil (`.compact`), so `recipeId` is absent for the
/// stale-shopping-list campaign. Parsing is total: anything that is not a lifecycle
/// notification returns nil rather than throwing.
struct LifecycleNotificationPayload: Equatable, Sendable {
    let campaign: String
    let deliveryId: Int
    let recipeId: Int?
    let cookbookId: Int?

    static func parse(_ userInfo: [AnyHashable: Any]) -> LifecycleNotificationPayload? {
        guard let campaign = userInfo["campaign"] as? String, !campaign.isEmpty else { return nil }
        guard let deliveryId = Self.int(userInfo["delivery_id"]) else { return nil }

        return LifecycleNotificationPayload(
            campaign: campaign,
            deliveryId: deliveryId,
            recipeId: Self.int(userInfo["recipe_id"]),
            cookbookId: Self.int(userInfo["cookbook_id"])
        )
    }

    private static func int(_ value: Any?) -> Int? {
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String { return Int(string) }
        return nil
    }
}
```

- [ ] **Step 4: Implement the delivery service**

Create `hauptgang-ios/Hauptgang/Services/NotificationDeliveryService.swift`:

```swift
import Foundation
import os

/// Reports what happened to a delivered lifecycle notification.
protocol NotificationDeliveryServiceProtocol: Sendable {
    func markOpened(deliveryId: Int, actionTaken: String?) async
}

/// Posts to `POST /api/v1/notification_deliveries/:id/opened`.
///
/// Best-effort by design: this is analytics, and a failure here must never surface to
/// the user or block the navigation the tap requested. Nothing retries.
final class NotificationDeliveryService: NotificationDeliveryServiceProtocol, @unchecked Sendable {
    static let shared = NotificationDeliveryService()

    private let api: any APIClientProtocol
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "NotificationDeliveryService")

    init(api: any APIClientProtocol = APIClient.shared) {
        self.api = api
    }

    func markOpened(deliveryId: Int, actionTaken: String? = nil) async {
        do {
            try await self.api.requestVoid(
                endpoint: "notification_deliveries/\(deliveryId)/opened",
                method: .post,
                body: OpenedRequest(actionTaken: actionTaken),
                queryItems: nil,
                authenticated: true
            )
        } catch {
            self.logger.error("Failed to mark delivery \(deliveryId) opened: \(error.localizedDescription)")
        }
    }
}

private struct OpenedRequest: Encodable {
    let actionTaken: String?
}
```

Create `hauptgang-ios/HauptgangTests/Mocks/MockNotificationDeliveryService.swift`:

```swift
import Foundation
@testable import Hauptgang

final class MockNotificationDeliveryService: NotificationDeliveryServiceProtocol, @unchecked Sendable {
    private let lock = NSLock()
    private var _opened: [(deliveryId: Int, actionTaken: String?)] = []

    var opened: [(deliveryId: Int, actionTaken: String?)] {
        self.lock.withLock { self._opened }
    }

    func markOpened(deliveryId: Int, actionTaken: String?) async {
        self.lock.withLock { self._opened.append((deliveryId, actionTaken)) }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `bin/ios-test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add hauptgang-ios/Hauptgang/Models/LifecycleNotificationPayload.swift hauptgang-ios/Hauptgang/Services/NotificationDeliveryService.swift hauptgang-ios/HauptgangTests/Models/LifecycleNotificationPayloadTests.swift hauptgang-ios/HauptgangTests/Mocks/MockNotificationDeliveryService.swift
git commit -m "feat: parse lifecycle notification payloads and report deliveries as opened"
```

---

### Task 6: Route a notification tap to the right screen

Tapping a notification must open the recipe it names — switching cookbooks first if the recipe lives in one that is not active — or the shopping list for the stale-list campaign.

**Files:**
- Create: `hauptgang-ios/Hauptgang/ViewModels/NotificationRouter.swift`
- Test: `hauptgang-ios/HauptgangTests/ViewModels/NotificationRouterTests.swift`
- Modify: `hauptgang-ios/Hauptgang/App/AppDelegate.swift`
- Modify: `hauptgang-ios/Hauptgang/App/HauptgangApp.swift`
- Modify: `hauptgang-ios/Hauptgang/Views/MainTabView.swift`
- Modify: `hauptgang-ios/Hauptgang/Views/RecipesView.swift:42,233`

**Interfaces:**
- Consumes: `LifecycleNotificationPayload.parse(_:)`, `NotificationDeliveryServiceProtocol` (Task 5).
- Produces: `@MainActor @Observable final class NotificationRouter` with `static let shared`, `private(set) var pendingRoute: NotificationRoute?`, `func handle(_ userInfo: [AnyHashable: Any])`, `func consumeRoute() -> NotificationRoute?`; and `enum NotificationRoute: Equatable, Sendable { case recipe(id: Int, cookbookId: Int?); case shoppingList }`.

- [ ] **Step 1: Write the failing tests**

Create `hauptgang-ios/HauptgangTests/ViewModels/NotificationRouterTests.swift`:

```swift
@testable import Hauptgang
import XCTest

@MainActor
final class NotificationRouterTests: XCTestCase {
    func testARecipeCampaignRoutesToThatRecipe() async {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle([
            "campaign": "import_follow_up", "delivery_id": 42, "recipe_id": 7, "cookbook_id": 3
        ])

        XCTAssertEqual(router.pendingRoute, .recipe(id: 7, cookbookId: 3))
    }

    func testTheStaleListCampaignRoutesToTheShoppingList() async {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle(["campaign": "stale_shopping_list", "delivery_id": 43, "cookbook_id": 3])

        XCTAssertEqual(router.pendingRoute, .shoppingList)
    }

    func testHandlingReportsTheDeliveryAsOpened() async throws {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle(["campaign": "resurface", "delivery_id": 44, "recipe_id": 9])

        // handle() fires the report off the main actor; give it a turn to land.
        try await Task.sleep(for: .milliseconds(50))
        XCTAssertEqual(deliveries.opened.map(\.deliveryId), [44])
    }

    func testANonLifecycleNotificationIsIgnored() async {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle(["aps": ["alert": "hi"]])

        XCTAssertNil(router.pendingRoute)
    }

    func testConsumingClearsTheRoute() async {
        let deliveries = MockNotificationDeliveryService()
        let router = NotificationRouter(deliveryService: deliveries)

        router.handle(["campaign": "resurface", "delivery_id": 44, "recipe_id": 9])

        XCTAssertEqual(router.consumeRoute(), .recipe(id: 9, cookbookId: nil))
        XCTAssertNil(router.pendingRoute, "a route must not be delivered twice")
        XCTAssertNil(router.consumeRoute())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `bin/ios-test`
Expected: FAIL to compile — `cannot find 'NotificationRouter' in scope`.

- [ ] **Step 3: Implement the router**

Create `hauptgang-ios/Hauptgang/ViewModels/NotificationRouter.swift`:

```swift
import Foundation
import os

/// Where a tapped lifecycle notification should take the user.
enum NotificationRoute: Equatable, Sendable {
    case recipe(id: Int, cookbookId: Int?)
    case shoppingList
}

/// Holds the destination of a tapped notification until the view layer is ready to
/// navigate. Mirrors `DeepLinkRouter`: the tap can arrive before the UI exists (cold
/// launch), so the route is parked rather than acted on immediately.
@MainActor @Observable
final class NotificationRouter {
    static let shared = NotificationRouter()

    private(set) var pendingRoute: NotificationRoute?

    private let deliveryService: any NotificationDeliveryServiceProtocol
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "NotificationRouter")

    init(deliveryService: any NotificationDeliveryServiceProtocol = NotificationDeliveryService.shared) {
        self.deliveryService = deliveryService
    }

    /// Called when the user taps a notification. Parks the destination and reports the
    /// open to the backend.
    func handle(_ userInfo: [AnyHashable: Any]) {
        guard let payload = LifecycleNotificationPayload.parse(userInfo) else {
            self.logger.debug("Ignoring a notification that is not a lifecycle campaign")
            return
        }

        self.logger.info("Tapped \(payload.campaign) notification (delivery \(payload.deliveryId))")

        if let recipeId = payload.recipeId {
            self.pendingRoute = .recipe(id: recipeId, cookbookId: payload.cookbookId)
        } else {
            self.pendingRoute = .shoppingList
        }

        let service = self.deliveryService
        let deliveryId = payload.deliveryId
        Task { await service.markOpened(deliveryId: deliveryId, actionTaken: nil) }
    }

    /// Take the pending route, if any. Clears it so a route is never navigated twice.
    func consumeRoute() -> NotificationRoute? {
        defer { pendingRoute = nil }
        return self.pendingRoute
    }
}
```

- [ ] **Step 4: Run the router tests to verify they pass**

Run: `bin/ios-test`
Expected: PASS.

- [ ] **Step 5: Receive taps in the AppDelegate**

Rewrite `hauptgang-ios/Hauptgang/App/AppDelegate.swift`:

```swift
import UIKit
import UserNotifications

/// Bridges UIKit AppDelegate callbacks (APNs registration and notification taps) into
/// our SwiftUI app. Wired via `@UIApplicationDelegateAdaptor` in `HauptgangApp`.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _: UIApplication,
        didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(
        _: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Task {
            await PushNotificationService.shared.handleDeviceToken(deviceToken)
        }
    }

    func application(
        _: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        Task {
            await PushNotificationService.shared.handleRegistrationFailure(error)
        }
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    /// The user tapped a notification. Fires on cold launch too, after the app is up.
    func userNotificationCenter(
        _: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let userInfo = response.notification.request.content.userInfo
        await MainActor.run {
            NotificationRouter.shared.handle(userInfo)
        }
    }

    /// Show lifecycle notifications even while the app is in the foreground — they are
    /// timed for a moment the user is deciding what to cook, and silently dropping one
    /// because the app happens to be open would lose the nudge entirely.
    func userNotificationCenter(
        _: UNUserNotificationCenter,
        willPresent _: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
}
```

If `HauptgangApp.swift` does not already declare `@UIApplicationDelegateAdaptor(AppDelegate.self)`, check before changing anything — it must, since APNs registration works today.

- [ ] **Step 6: Navigate on the pending route**

In `MainTabView.swift`, add the router and react to it. Add near the other state:

```swift
    @State private var notificationRouter = NotificationRouter.shared
    @State private var pendingRecipeId: Int?
```

Add this modifier to the `TabView`, after the existing `.onChange(of: self.searchQuery)`:

```swift
        .onChange(of: self.notificationRouter.pendingRoute, initial: true) { _, route in
            guard route != nil else { return }
            Task { await self.navigate(to: self.notificationRouter.consumeRoute()) }
        }
```

And add the method to `MainTabView`:

```swift
    /// Send the user where a tapped notification pointed. A recipe may live in a
    /// cookbook that is not the active one, so switch first and let the recipe list
    /// reload before pushing the detail screen.
    private func navigate(to route: NotificationRoute?) async {
        switch route {
        case .shoppingList:
            self.selectedTab = .shoppingList

        case let .recipe(id, cookbookId):
            if let cookbookId,
               cookbookId != self.session.cookbookViewModel.activeCookbook?.id,
               let cookbook = self.session.cookbookViewModel.cookbooks.first(where: { $0.id == cookbookId }) {
                await self.session.switchCookbook(cookbook)
            }
            self.selectedTab = .recipes
            self.pendingRecipeId = id

        case nil:
            break
        }
    }
```

Pass the pending id into `RecipesView`:

```swift
            SwiftUI.Tab("Recipes", systemImage: "fork.knife", value: Tab.recipes) {
                RecipesView(
                    recipeViewModel: self.session.recipeViewModel,
                    suppressTransientUI: !self.session.canDismissStartupSplash,
                    pendingRecipeId: self.$pendingRecipeId
                )
            }
```

Check `CookbookViewModel` exposes a `cookbooks` collection under that name before using it (`grep -n "var cookbooks" hauptgang-ios/Hauptgang/ViewModels/CookbookViewModel.swift`); if it differs, use the real name.

- [ ] **Step 7: Push the detail screen from RecipesView**

`RecipesView` already owns `@State private var navigationPath = NavigationPath()` (line 42) and `.navigationDestination(for: Int.self)` (line 233), so a recipe id appended to the path opens the detail screen. Add the binding property beside the other stored properties:

```swift
    @Binding var pendingRecipeId: Int?
```

…and this modifier on the `NavigationStack`:

```swift
        .onChange(of: self.pendingRecipeId, initial: true) { _, recipeId in
            guard let recipeId else { return }
            self.navigationPath.append(recipeId)
            self.pendingRecipeId = nil
        }
```

`RecipesView` has other call sites and SwiftUI previews. It currently has **no** explicit initializer — it relies on the memberwise one, and `suppressTransientUI` has no default. Adding a `@Binding` would force every call site to pass it, so add an explicit init that defaults only the new parameter and leaves the existing two exactly as they are:

```swift
    init(
        recipeViewModel: RecipeViewModel,
        suppressTransientUI: Bool,
        pendingRecipeId: Binding<Int?> = .constant(nil)
    ) {
        self.recipeViewModel = recipeViewModel
        self.suppressTransientUI = suppressTransientUI
        self._pendingRecipeId = pendingRecipeId
    }
```

Do **not** give `suppressTransientUI` a default it does not have today — that would silently change behaviour at any call site that forgets it.

- [ ] **Step 8: Run the tests**

Run: `bin/ios-test`
Expected: PASS, and the app builds. The navigation wiring itself is not unit-tested — SwiftUI view state is not reachable from XCTest here, and adding a UI-test target for one screen transition is not warranted at this app's scale. Verify it by hand in Step 9.

- [ ] **Step 9: Verify a tap by hand**

Build to a simulator, then deliver a test push with `xcrun simctl`:

```bash
cat > /tmp/lifecycle.apns <<'JSON'
{
  "Simulator Target Bundle": "app.hauptgang.ios",
  "aps": { "alert": { "title": "Hauptgang", "body": "You saved \"Ramen\" a couple of days ago." } },
  "campaign": "import_follow_up",
  "delivery_id": 1,
  "recipe_id": REPLACE_WITH_A_REAL_RECIPE_ID
}
JSON
xcrun simctl push booted app.hauptgang.ios /tmp/lifecycle.apns
```

Expected: tapping the banner opens that recipe's detail screen. Repeat with the `stale_shopping_list` shape (no `recipe_id`) and confirm it opens the Shopping List tab. The `delivery_id` will 404 server-side, which is fine and logged — it does not block navigation.

- [ ] **Step 10: Commit**

```bash
git add hauptgang-ios/Hauptgang/ViewModels/NotificationRouter.swift hauptgang-ios/HauptgangTests/ViewModels/NotificationRouterTests.swift hauptgang-ios/Hauptgang/App/AppDelegate.swift hauptgang-ios/Hauptgang/Views/MainTabView.swift hauptgang-ios/Hauptgang/Views/RecipesView.swift
git commit -m "feat: route a tapped lifecycle notification to its recipe or the shopping list"
```

---

### Task 7: A settings switch for lifecycle notifications

**Files:**
- Modify: `hauptgang-ios/Hauptgang/Models/User.swift`
- Modify: `hauptgang-ios/Hauptgang/Services/AuthServiceProtocol.swift`
- Modify: `hauptgang-ios/Hauptgang/Services/AuthService.swift:69-80`
- Modify: `hauptgang-ios/Hauptgang/ViewModels/AuthManager.swift:64-68`
- Modify: `hauptgang-ios/Hauptgang/Views/SettingsView.swift`
- Modify: `hauptgang-ios/HauptgangTests/Mocks/MockAuthService.swift`
- Test: `hauptgang-ios/HauptgangTests/ViewModels/AuthManagerTests.swift` (existing — append)

**Interfaces:**
- Consumes: the login/signup response field from Task 1.
- Produces: `User.lifecycleNotificationsEnabled: Bool`, `AuthServiceProtocol.updateLifecycleNotifications(_ enabled: Bool) async throws -> User`, `AuthManager.updateLifecycleNotifications(_ enabled: Bool) async throws`.

- [ ] **Step 1: Write the failing test**

Read `hauptgang-ios/HauptgangTests/ViewModels/AuthManagerTests.swift` for its existing setup, then append:

```swift
    func testUpdatingTheNotificationPreferenceUpdatesAuthState() async throws {
        let service = MockAuthService()
        service.currentUser = User(id: 1, email: "a@b.c", name: "A", lifecycleNotificationsEnabled: true)
        service.updateLifecycleResult = User(
            id: 1, email: "a@b.c", name: "A", lifecycleNotificationsEnabled: false
        )
        let manager = AuthManager(authService: service)
        await manager.checkAuthStatus()

        try await manager.updateLifecycleNotifications(false)

        XCTAssertEqual(service.lastLifecycleValue, false)
        guard case let .authenticated(user) = manager.authState else {
            return XCTFail("expected an authenticated state")
        }
        XCTAssertFalse(user.lifecycleNotificationsEnabled)
    }
```

Match `MockAuthService`'s existing property naming and `AuthManager`'s existing initializer — read both first and follow them rather than the names above if they differ.

- [ ] **Step 2: Run the test to verify it fails**

Run: `bin/ios-test`
Expected: FAIL to compile — `extra argument 'lifecycleNotificationsEnabled'`.

- [ ] **Step 3: Add the field to the model**

`hauptgang-ios/Hauptgang/Models/User.swift`:

```swift
import Foundation

struct User: Codable, Identifiable, Equatable {
    let id: Int
    let email: String
    var name: String?
    /// Whether the backend may send this user lifecycle nudges. Defaults to true to
    /// match the server column default, and so a cached user decoded from an older
    /// build (before login returned the field) does not read as opted out.
    var lifecycleNotificationsEnabled: Bool = true
}
```

A defaulted property alone is not enough for `Codable` — a synthesized `init(from:)` still fails on a missing key. Add an explicit decoder:

```swift
extension User {
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(Int.self, forKey: .id)
        self.email = try container.decode(String.self, forKey: .email)
        self.name = try container.decodeIfPresent(String.self, forKey: .name)
        self.lifecycleNotificationsEnabled =
            try container.decodeIfPresent(Bool.self, forKey: .lifecycleNotificationsEnabled) ?? true
    }
}
```

- [ ] **Step 4: Add the service method**

In `AuthServiceProtocol.swift`, add to the protocol:

```swift
    func updateLifecycleNotifications(_ enabled: Bool) async throws -> User
```

In `AuthService.swift`, beside `updateName`, add — and read `updateName`'s existing `AccountUpdateRequest` / `AccountUpdateBody` / `AccountUpdateResponse` definitions first, since this reuses them:

```swift
    func updateLifecycleNotifications(_ enabled: Bool) async throws -> User {
        let request = AccountUpdateRequest(user: AccountUpdateBody(lifecycleNotificationsEnabled: enabled))

        let response: AccountUpdateResponse = try await api.request(
            endpoint: "account",
            method: .patch,
            body: request,
            authenticated: true
        )

        try await self.keychain.saveUser(response.user)
        return response.user
    }
```

`AccountUpdateBody` currently has only `name`. Make both fields optional so each call sends just what it changes — `JSONEncoder` omits nil values, and the Rails `params.expect(user: [...])` accepts a partial hash:

```swift
private struct AccountUpdateBody: Encodable {
    var name: String?
    var lifecycleNotificationsEnabled: Bool?
}
```

Update `updateName`'s call site to the new memberwise shape: `AccountUpdateBody(name: name)` still compiles unchanged.

Add the method to `MockAuthService` too:

```swift
    var updateLifecycleResult: User?
    private(set) var lastLifecycleValue: Bool?

    func updateLifecycleNotifications(_ enabled: Bool) async throws -> User {
        self.lastLifecycleValue = enabled
        if self.shouldThrow { throw self.errorToThrow }
        guard let user = self.updateLifecycleResult else { throw APIError.invalidResponse }
        return user
    }
```

Match `MockAuthService`'s existing error-flag naming (`shouldThrow` / `errorToThrow` or whatever it actually uses) and its `APIError` case.

- [ ] **Step 5: Add the AuthManager method**

In `AuthManager.swift`, beside `updateName`:

```swift
    func updateLifecycleNotifications(_ enabled: Bool) async throws {
        let updated = try await self.authService.updateLifecycleNotifications(enabled)
        self.authState = .authenticated(updated)
    }
```

- [ ] **Step 6: Add the switch to Settings**

In `SettingsView.swift`, add state for the in-flight value:

```swift
    @State private var isUpdatingNotifications = false
```

Add a section, and render it into `body`'s `List` between `self.userSection` and `self.cookbookSection`:

```swift
    @ViewBuilder
    private var notificationsSection: some View {
        if let user = self.authManager.authState.user {
            Section {
                Toggle(isOn: Binding(
                    get: { user.lifecycleNotificationsEnabled },
                    set: { newValue in
                        Task { await self.setLifecycleNotifications(newValue) }
                    }
                )) {
                    HStack {
                        Image(systemName: "bell.badge")
                            .foregroundColor(.hauptgangPrimary)
                        Text("Recipe reminders")
                            .foregroundColor(.hauptgangTextPrimary)
                    }
                }
                .disabled(self.isUpdatingNotifications)
            } footer: {
                Text("Occasional nudges about recipes you saved and shopping lists you left unfinished.")
            }
        }
    }

    private func setLifecycleNotifications(_ enabled: Bool) async {
        self.isUpdatingNotifications = true
        defer { isUpdatingNotifications = false }
        do {
            try await self.authManager.updateLifecycleNotifications(enabled)
        } catch {
            // The toggle reads from authManager, which is unchanged on failure, so it
            // snaps back on its own.
        }
    }
```

Check the footer style against the file's other sections; if none use a `footer:`, drop it and put the explanation in a `Text` row instead.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `bin/ios-test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add hauptgang-ios/Hauptgang/Models/User.swift hauptgang-ios/Hauptgang/Services/AuthService.swift hauptgang-ios/Hauptgang/Services/AuthServiceProtocol.swift hauptgang-ios/Hauptgang/ViewModels/AuthManager.swift hauptgang-ios/Hauptgang/Views/SettingsView.swift hauptgang-ios/HauptgangTests/Mocks/MockAuthService.swift hauptgang-ios/HauptgangTests/ViewModels/AuthManagerTests.swift
git commit -m "feat: add a settings switch for lifecycle notifications"
```

---

### Task 8: Enable the Resurface campaign — GATED ON THE RELEASE SHIPPING

> **Do not execute this task as part of the implementation run.** It is recorded here so the sequence is written down in one place. Everything above must be built, merged, released to the App Store, and adopted by real devices before this becomes safe. An implementer working through this plan should stop after Task 7 and report that Task 8 is deferred.

`ResurfaceCampaign` decides a recipe is forgotten from the *absence* of a view ping. Enabling it before people are running a build that sends them means every recipe looks forgotten, and users get "Cook it this week?" for recipes they opened yesterday.

**Files:**
- Modify: `app/jobs/evaluate_lifecycle_notifications_job.rb:6-13`
- Modify: `app/models/notifications/resurface_campaign.rb:9-17`
- Modify: `test/jobs/evaluate_lifecycle_notifications_job_test.rb` (the `does not send the resurface campaign while view tracking has no client` test asserts the campaign is absent — it must be replaced, not deleted, with one asserting it is present and reachable)

**Preconditions, all required:**
1. Tasks 1–7 are merged and deployed (backend) and shipped in an App Store release (client).
2. That release has been out long enough for the people who use this app to have updated. At this scale, ask them.
3. `RecipeEngagement.where.not(last_viewed_at: nil).count` on production is non-zero — proof that pings are actually arriving. Check with `bin/kamal app exec --reuse 'bin/rails runner "puts RecipeEngagement.where.not(last_viewed_at: nil).count"'`.

**Then:**
1. Set `VIEW_TRACKING_SINCE` to the date that release went out — or, safer, two weeks after it, which forgives the stragglers who updated late. Recipes saved before that date are never resurfaced, which is the intended trade.
2. Add `Notifications::ResurfaceCampaign` back to `CAMPAIGNS`, after `StaleShoppingListCampaign` (priority order matters: it is the lowest-intent nudge of the three).
3. Replace the gate test with one asserting a resurface-eligible user actually receives it, and remove the `assert_not_includes` line.
4. Run `bin/rails test && bin/rubocop`, deploy, then watch `notification_deliveries` for a week: `campaign, count(*), count(opened_at)` grouped by campaign tells you whether the nudges are landing or annoying.

---

## Self-Review

**Spec coverage.** The design doc's iOS-side requirements: view pings (Tasks 3, 4), device time zone (Task 2), notification tap routing (Task 6), delivery-opened callback (Tasks 5, 6), user-facing opt-out (Tasks 1, 7), staged Resurface rollout (Task 8). The `action_taken` follow-up write — posting again after the user acts on a nudge — is deliberately **not** planned: it needs a definition of "acted" that spans screens, the column is descriptive only and feeds no delivery logic, and `opened_at` already answers "did this nudge work". Add it later if the open rates turn out to be uninformative.

**Placeholder scan.** One deliberate `REPLACE_WITH_A_REAL_RECIPE_ID` in Task 6's manual verification payload — a literal value only the operator can know. No TBDs; every code step carries its code.

**Type consistency.** `RecipeView`/`RecipeViewSink`/`RecipeViewTracker` (Task 3) are consumed under those exact names in Task 4. `LifecycleNotificationPayload.parse` and `NotificationDeliveryServiceProtocol.markOpened(deliveryId:actionTaken:)` (Task 5) are consumed under those names in Task 6. `User.lifecycleNotificationsEnabled` (Task 7) matches the Rails key `lifecycle_notifications_enabled` through `convertFromSnakeCase`. `NotificationRoute` is `Equatable` because Task 6's tests compare it and `onChange` requires it.

**Known soft spots, flagged rather than hidden.** Task 6's navigation wiring is verified by hand, not by test — SwiftUI view state is not reachable from this test target, and a UI-test target for one transition is not warranted here. Tasks 6 and 7 both edit files whose exact current shape (initializers, mock property names, section style) the implementer must read before editing; each step says so at the point it matters.

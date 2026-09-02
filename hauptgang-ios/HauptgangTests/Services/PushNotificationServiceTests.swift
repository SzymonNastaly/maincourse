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

    // MARK: - Authorization gating

    func testPromptAsksWhenTheStatusIsUndetermined() async {
        let authorizer = MockNotificationAuthorizer(status: .notDetermined)
        let service = self.makeService(authorizer: authorizer)

        await service.promptForAuthorization()

        let requests = await authorizer.requestCount
        let registrations = await authorizer.registerCount
        XCTAssertEqual(requests, 1)
        XCTAssertEqual(registrations, 1)
    }

    func testPromptDoesNotRegisterWhenTheUserDeclines() async {
        let authorizer = MockNotificationAuthorizer(status: .notDetermined, grants: false)
        let service = self.makeService(authorizer: authorizer)

        await service.promptForAuthorization()

        let requests = await authorizer.requestCount
        let registrations = await authorizer.registerCount
        XCTAssertEqual(requests, 1)
        XCTAssertEqual(registrations, 0)
    }

    func testPromptOnlyRegistersWhenPermissionIsAlreadyGranted() async {
        let authorizer = MockNotificationAuthorizer(status: .authorized)
        let service = self.makeService(authorizer: authorizer)

        await service.promptForAuthorization()

        // iOS shows the dialog once, so an already-answered status must never ask again.
        let requests = await authorizer.requestCount
        let registrations = await authorizer.registerCount
        XCTAssertEqual(requests, 0)
        XCTAssertEqual(registrations, 1)
    }

    func testPromptDoesNothingOnceTheUserHasDenied() async {
        let authorizer = MockNotificationAuthorizer(status: .denied)
        let service = self.makeService(authorizer: authorizer)

        await service.promptForAuthorization()

        let requests = await authorizer.requestCount
        let registrations = await authorizer.registerCount
        XCTAssertEqual(requests, 0)
        XCTAssertEqual(registrations, 0)
    }

    func testRegisterIfAuthorizedNeverPrompts() async {
        let authorizer = MockNotificationAuthorizer(status: .notDetermined)
        let service = self.makeService(authorizer: authorizer)

        await service.registerIfAuthorized()

        // This runs on every login. It must never spend the one system prompt.
        let requests = await authorizer.requestCount
        let registrations = await authorizer.registerCount
        XCTAssertEqual(requests, 0)
        XCTAssertEqual(registrations, 0)
    }

    func testRegisterIfAuthorizedRegistersForAProvisionalStatus() async {
        let authorizer = MockNotificationAuthorizer(status: .provisional)
        let service = self.makeService(authorizer: authorizer)

        await service.registerIfAuthorized()

        let registrations = await authorizer.registerCount
        XCTAssertEqual(registrations, 1)
    }

    func testAThrownRequestDoesNotRegister() async {
        let authorizer = MockNotificationAuthorizer(status: .notDetermined, throwsOnRequest: true)
        let service = self.makeService(authorizer: authorizer)

        await service.promptForAuthorization()

        let registrations = await authorizer.registerCount
        XCTAssertEqual(registrations, 0)
    }

    private func makeService(authorizer: any NotificationAuthorizing) -> PushNotificationService {
        nonisolated(unsafe) let defaults = self.defaults!
        return PushNotificationService(api: MockAPIClient(), defaults: defaults, authorizer: authorizer)
    }

    // MARK: - Token registration

    func testRegistrationIncludesTheDeviceTimeZone() async throws {
        let api = MockAPIClient()
        await api.setResponse(#"{"id":1,"token":"abc","environment":"sandbox"}"#)
        nonisolated(unsafe) let defaults = try XCTUnwrap(self.defaults)
        let service = PushNotificationService(api: api, defaults: defaults)

        await service.setAuthenticated(true)
        await service.handleDeviceToken(Data([0xAB, 0xCD]))

        let recorded = await api.recorded
        let register = try XCTUnwrap(recorded.first(where: { $0.endpoint == "device_tokens" }))
        XCTAssertEqual(register.bodyString("time_zone"), TimeZone.current.identifier)
        XCTAssertEqual(register.bodyString("token"), "abcd")
    }

    func testTimeZoneChangeTriggersReRegistration() async throws {
        let api = MockAPIClient()
        await api.setResponse(#"{"id":1,"token":"abcd","environment":"sandbox"}"#)
        nonisolated(unsafe) let defaults = try XCTUnwrap(self.defaults)
        let service = PushNotificationService(api: api, defaults: defaults)

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
}

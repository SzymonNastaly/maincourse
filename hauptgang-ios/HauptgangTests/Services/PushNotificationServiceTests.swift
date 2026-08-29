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
        nonisolated(unsafe) let defaults = self.defaults!
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
        nonisolated(unsafe) let defaults = self.defaults!
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

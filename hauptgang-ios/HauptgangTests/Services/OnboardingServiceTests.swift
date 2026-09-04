@testable import Hauptgang
import XCTest

final class OnboardingServiceTests: XCTestCase {
    override func tearDown() {
        OnboardingService.clearDeviceIdForAuth()
        super.tearDown()
    }

    func testDeviceIdRemainsAvailableUntilAuthenticationSucceeds() {
        OnboardingService.storeDeviceIdForAuth("onboarding-device")

        XCTAssertEqual(OnboardingService.deviceIdForAuth(), "onboarding-device")
        XCTAssertEqual(OnboardingService.deviceIdForAuth(), "onboarding-device")

        OnboardingService.clearDeviceIdForAuth()
        XCTAssertNil(OnboardingService.deviceIdForAuth())
    }
}

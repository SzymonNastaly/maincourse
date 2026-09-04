import AuthenticationServices
@testable import Hauptgang
import XCTest

@MainActor
final class AppleSignInServiceTests: XCTestCase {
    func testSHA256ProducesLowercaseHexDigest() {
        XCTAssertEqual(
            AppleSignInService.sha256("nonce"),
            "78377b525757b494427f89014f97d79928f3938d14eb51e20fb5dec9834eb304"
        )
    }

    func testCanceledAuthorizationReturnsNil() throws {
        let service = AppleSignInService()

        let credential = try service.credential(from: .failure(ASAuthorizationError(.canceled)))

        XCTAssertNil(credential)
    }

    func testUnknownAuthorizationReturnsHelpfulError() {
        let service = AppleSignInService()

        XCTAssertThrowsError(try service.credential(from: .failure(ASAuthorizationError(.unknown)))) { error in
            guard case OAuthSignInError.appleAccountUnavailable = error else {
                return XCTFail("Expected appleAccountUnavailable, got \(error)")
            }
        }
    }
}

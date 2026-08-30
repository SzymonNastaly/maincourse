@testable import Hauptgang
import XCTest

final class DeepLinkRouterTests: XCTestCase {
    // MARK: - Universal Link Parsing

    func testExtractToken_universalLink_longToken() throws {
        let url = try XCTUnwrap(URL(string: "https://cook.hauptgang.app/invite/a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
        XCTAssertEqual(DeepLinkRouter.extractInvitationToken(from: url), "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    }

    func testExtractToken_universalLink_newHost() throws {
        let url = try XCTUnwrap(URL(string: "https://app.getmaincourse.com/invite/abc123"))
        XCTAssertEqual(DeepLinkRouter.extractInvitationToken(from: url), "abc123")
    }

    func testExtractToken_universalLink_legacyHostStillAccepted() throws {
        let url = try XCTUnwrap(URL(string: "https://cook.hauptgang.app/invite/abc123"))
        XCTAssertEqual(DeepLinkRouter.extractInvitationToken(from: url), "abc123")
    }

    func testExtractToken_universalLink_newHost_wrongPath() throws {
        let url = try XCTUnwrap(URL(string: "https://app.getmaincourse.com/recipes/abc123"))
        XCTAssertNil(DeepLinkRouter.extractInvitationToken(from: url))
    }

    func testExtractToken_universalLink_wrongHost() throws {
        let url = try XCTUnwrap(URL(string: "https://evil.com/invite/abc123"))
        XCTAssertNil(DeepLinkRouter.extractInvitationToken(from: url))
    }

    func testExtractToken_universalLink_wrongPath() throws {
        let url = try XCTUnwrap(URL(string: "https://cook.hauptgang.app/recipes/abc123"))
        XCTAssertNil(DeepLinkRouter.extractInvitationToken(from: url))
    }

    func testExtractToken_universalLink_noToken() throws {
        let url = try XCTUnwrap(URL(string: "https://cook.hauptgang.app/invite/"))
        XCTAssertNil(DeepLinkRouter.extractInvitationToken(from: url))
    }

    func testExtractToken_universalLink_tooManyPathComponents() throws {
        let url = try XCTUnwrap(URL(string: "https://cook.hauptgang.app/invite/abc123/extra"))
        XCTAssertNil(DeepLinkRouter.extractInvitationToken(from: url))
    }

    // MARK: - Custom Scheme Parsing

    func testExtractToken_customScheme() throws {
        let url = try XCTUnwrap(URL(string: "hauptgang://invite/abc123"))
        XCTAssertEqual(DeepLinkRouter.extractInvitationToken(from: url), "abc123")
    }

    func testExtractToken_customScheme_wrongHost() throws {
        let url = try XCTUnwrap(URL(string: "hauptgang://recipes/abc123"))
        XCTAssertNil(DeepLinkRouter.extractInvitationToken(from: url))
    }

    // MARK: - Edge Cases

    func testExtractToken_httpScheme() throws {
        let url = try XCTUnwrap(URL(string: "http://cook.hauptgang.app/invite/abc123"))
        XCTAssertEqual(DeepLinkRouter.extractInvitationToken(from: url), "abc123")
    }

    func testExtractToken_ftpScheme() throws {
        let url = try XCTUnwrap(URL(string: "ftp://cook.hauptgang.app/invite/abc123"))
        XCTAssertNil(DeepLinkRouter.extractInvitationToken(from: url))
    }

    func testExtractToken_rootPath() throws {
        let url = try XCTUnwrap(URL(string: "https://cook.hauptgang.app/"))
        XCTAssertNil(DeepLinkRouter.extractInvitationToken(from: url))
    }

    // MARK: - Handle & Pending Token

    @MainActor
    func testHandle_setsAndClearsPendingToken() throws {
        let router = DeepLinkRouter()

        try router.handle(XCTUnwrap(URL(string: "https://cook.hauptgang.app/invite/test-token")))
        XCTAssertEqual(router.pendingInvitationToken, "test-token")

        router.clearPendingInvitation()
        XCTAssertNil(router.pendingInvitationToken)
    }

    @MainActor
    func testHandle_unrecognizedURL_doesNotSetToken() throws {
        let router = DeepLinkRouter()

        try router.handle(XCTUnwrap(URL(string: "https://example.com/something")))
        XCTAssertNil(router.pendingInvitationToken)
    }

    // MARK: - Stored Token (UserDefaults)

    @MainActor
    func testStoreAndConsumeToken() {
        let router = DeepLinkRouter()
        UserDefaults.standard.removeObject(forKey: "pendingInvitationToken")

        router.storePendingToken("stored-token")
        XCTAssertEqual(UserDefaults.standard.string(forKey: "pendingInvitationToken"), "stored-token")

        let consumed = router.consumeStoredToken()
        XCTAssertEqual(consumed, "stored-token")
        XCTAssertNil(UserDefaults.standard.string(forKey: "pendingInvitationToken"))
    }

    @MainActor
    func testConsumeToken_whenNoneStored_returnsNil() {
        let router = DeepLinkRouter()
        UserDefaults.standard.removeObject(forKey: "pendingInvitationToken")

        XCTAssertNil(router.consumeStoredToken())
    }
}

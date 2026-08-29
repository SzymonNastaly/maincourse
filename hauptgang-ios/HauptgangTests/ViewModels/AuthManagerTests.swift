@testable import Hauptgang
import XCTest

@MainActor
final class AuthManagerTests: XCTestCase {
    private var sut: AuthManager!
    private var mockAuthService: MockAuthService!

    override func setUp() {
        super.setUp()
        self.mockAuthService = MockAuthService()
        self.sut = AuthManager(authService: self.mockAuthService)
    }

    override func tearDown() {
        self.sut = nil
        self.mockAuthService = nil
        super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState_isUnknown() {
        XCTAssertEqual(self.sut.authState, .unknown)
    }

    // MARK: - Check Auth Status Tests

    func testCheckAuthStatus_withUser_becomesAuthenticated() async {
        let user = User(id: 1, email: "user@example.com")
        self.mockAuthService.currentUser = user

        await self.sut.checkAuthStatus()

        XCTAssertEqual(self.sut.authState, .authenticated(user))
        XCTAssertTrue(self.sut.authState.isAuthenticated)
        XCTAssertEqual(self.sut.authState.user, user)
    }

    func testCheckAuthStatus_noUser_becomesUnauthenticated() async {
        self.mockAuthService.currentUser = nil

        await self.sut.checkAuthStatus()

        XCTAssertEqual(self.sut.authState, .unauthenticated)
        XCTAssertFalse(self.sut.authState.isAuthenticated)
        XCTAssertNil(self.sut.authState.user)
    }

    // MARK: - Sign In Tests

    func testSignIn_updatesStateToAuthenticated() {
        let user = User(id: 42, email: "newuser@example.com")

        self.sut.signIn(user: user)

        XCTAssertEqual(self.sut.authState, .authenticated(user))
        XCTAssertTrue(self.sut.authState.isAuthenticated)
        XCTAssertEqual(self.sut.authState.user?.id, 42)
        XCTAssertEqual(self.sut.authState.user?.email, "newuser@example.com")
    }

    func testSignIn_fromUnauthenticated_becomesAuthenticated() async {
        await self.sut.checkAuthStatus() // Sets to .unauthenticated
        XCTAssertEqual(self.sut.authState, .unauthenticated)

        let user = User(id: 1, email: "user@example.com")
        self.sut.signIn(user: user)

        XCTAssertEqual(self.sut.authState, .authenticated(user))
    }

    // MARK: - Sign Out Tests

    func testSignOut_clearsStateToUnauthenticated() async {
        let user = User(id: 1, email: "user@example.com")
        self.sut.signIn(user: user)
        XCTAssertTrue(self.sut.authState.isAuthenticated)

        await self.sut.signOut()

        XCTAssertEqual(self.sut.authState, .unauthenticated)
        XCTAssertFalse(self.sut.authState.isAuthenticated)
        XCTAssertNil(self.sut.authState.user)
    }

    func testSignOut_callsLogoutOnService() async {
        self.sut.signIn(user: User(id: 1, email: "user@example.com"))

        await self.sut.signOut()

        XCTAssertTrue(self.mockAuthService.logoutCalled)
    }

    // MARK: - AuthState Equatable Tests

    func testAuthState_unknownEquality() {
        let state1 = AuthManager.AuthState.unknown
        let state2 = AuthManager.AuthState.unknown

        XCTAssertEqual(state1, state2)
    }

    func testAuthState_unauthenticatedEquality() {
        let state1 = AuthManager.AuthState.unauthenticated
        let state2 = AuthManager.AuthState.unauthenticated

        XCTAssertEqual(state1, state2)
    }

    func testAuthState_authenticatedEquality_sameUser() {
        let user = User(id: 1, email: "user@example.com")
        let state1 = AuthManager.AuthState.authenticated(user)
        let state2 = AuthManager.AuthState.authenticated(user)

        XCTAssertEqual(state1, state2)
    }

    func testAuthState_authenticatedEquality_differentUsers() {
        let user1 = User(id: 1, email: "user1@example.com")
        let user2 = User(id: 2, email: "user2@example.com")
        let state1 = AuthManager.AuthState.authenticated(user1)
        let state2 = AuthManager.AuthState.authenticated(user2)

        XCTAssertNotEqual(state1, state2)
    }

    func testAuthState_differentStatesNotEqual() {
        let user = User(id: 1, email: "user@example.com")

        XCTAssertNotEqual(AuthManager.AuthState.unknown, AuthManager.AuthState.unauthenticated)
        XCTAssertNotEqual(AuthManager.AuthState.unknown, AuthManager.AuthState.authenticated(user))
        XCTAssertNotEqual(AuthManager.AuthState.unauthenticated, AuthManager.AuthState.authenticated(user))
    }

    // MARK: - Update Lifecycle Notifications Tests

    func testUpdatingTheNotificationPreferenceUpdatesAuthState() async throws {
        self.mockAuthService.currentUser = User(id: 1, email: "a@b.c", name: "A", lifecycleNotificationsEnabled: true)
        self.mockAuthService.updateLifecycleResult = User(
            id: 1, email: "a@b.c", name: "A", lifecycleNotificationsEnabled: false
        )
        await self.sut.checkAuthStatus()

        try await self.sut.updateLifecycleNotifications(false)

        XCTAssertEqual(self.mockAuthService.lastLifecycleValue, false)
        guard case let .authenticated(user) = self.sut.authState else {
            return XCTFail("expected an authenticated state")
        }
        XCTAssertFalse(user.lifecycleNotificationsEnabled)
    }
}

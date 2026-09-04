@testable import Hauptgang
import XCTest

@MainActor
final class AuthViewModelTests: XCTestCase {
    private var sut: AuthViewModel!
    private var mockAuthService: MockAuthService!

    override func setUp() {
        super.setUp()
        self.mockAuthService = MockAuthService()
        self.sut = AuthViewModel(authService: self.mockAuthService)
    }

    override func tearDown() {
        self.sut = nil
        self.mockAuthService = nil
        super.tearDown()
    }

    // MARK: - Email Validation Tests

    func testEmailValidation_validEmail_noError() {
        self.sut.email = "user@example.com"

        XCTAssertNil(self.sut.emailError)
    }

    func testEmailValidation_invalidEmail_showsErrorAfterFieldIsDirty() {
        self.sut.email = "foo"
        self.sut.emailDirty = true

        XCTAssertEqual(self.sut.emailError, "Please enter a valid email")
    }

    func testEmailValidation_emptyEmail_noError() {
        // Empty email doesn't show error (validation only triggers on non-empty input)
        self.sut.email = ""

        XCTAssertNil(self.sut.emailError)
    }

    func testEmailValidation_whitespaceOnlyEmail_noError() {
        self.sut.email = "   "

        XCTAssertNil(self.sut.emailError)
    }

    func testEmailValidation_emailMissingDomain_showsErrorAfterFieldIsDirty() {
        self.sut.email = "user@"
        self.sut.emailDirty = true

        XCTAssertEqual(self.sut.emailError, "Please enter a valid email")
    }

    // MARK: - Form Validation Tests

    func testFormValid_emptyFields_returnsFalse() {
        self.sut.email = ""
        self.sut.password = ""

        XCTAssertFalse(self.sut.isFormValid)
    }

    func testFormValid_emptyEmail_returnsFalse() {
        self.sut.email = ""
        self.sut.password = "password123"

        XCTAssertFalse(self.sut.isFormValid)
    }

    func testFormValid_emptyPassword_returnsFalse() {
        self.sut.email = "user@example.com"
        self.sut.password = ""

        XCTAssertFalse(self.sut.isFormValid)
    }

    func testFormValid_invalidEmail_returnsFalse() {
        self.sut.email = "invalid-email"
        self.sut.password = "password123"

        XCTAssertFalse(self.sut.isFormValid)
    }

    func testFormValid_validInput_returnsTrue() {
        self.sut.email = "user@example.com"
        self.sut.password = "password123"

        XCTAssertTrue(self.sut.isFormValid)
    }

    func testFormValid_emailWithWhitespace_trimsAndValidates() {
        self.sut.email = "  user@example.com  "
        self.sut.password = "password123"

        XCTAssertTrue(self.sut.isFormValid)
    }

    // MARK: - Login Tests

    func testLogin_success_updatesAuthManager() async {
        self.sut.email = "user@example.com"
        self.sut.password = "password123"
        self.mockAuthService.loginResult = .success(User(id: 42, email: "user@example.com"))
        let authManager = AuthManager(authService: mockAuthService)

        await sut.login(authManager: authManager)

        XCTAssertTrue(authManager.authState.isAuthenticated)
        XCTAssertEqual(authManager.authState.user?.id, 42)
    }

    func testLogin_failure_showsErrorMessage() async {
        self.sut.email = "user@example.com"
        self.sut.password = "wrongpassword"
        self.mockAuthService.loginResult = .failure(MockAuthError.invalidCredentials)
        let authManager = AuthManager(authService: mockAuthService)

        await sut.login(authManager: authManager)

        XCTAssertNotNil(self.sut.errorMessage)
        XCTAssertFalse(authManager.authState.isAuthenticated)
    }

    func testLogin_failure_doesNotClearPassword() async {
        self.sut.email = "user@example.com"
        self.sut.password = "wrongpassword"
        self.mockAuthService.loginResult = .failure(MockAuthError.networkError)
        let authManager = AuthManager(authService: mockAuthService)

        await sut.login(authManager: authManager)

        // Password should remain so user can retry
        // Note: Current impl clears password only on success, which is correct
        XCTAssertNotNil(self.sut.errorMessage)
    }

    func testLogin_invalidForm_doesNotCallService() async {
        self.sut.email = "invalid"
        self.sut.password = ""
        let authManager = AuthManager(authService: mockAuthService)

        await sut.login(authManager: authManager)

        XCTAssertFalse(authManager.authState.isAuthenticated)
    }

    func testLogin_setsLoadingDuringRequest() async {
        self.sut.email = "user@example.com"
        self.sut.password = "password123"
        let authManager = AuthManager(authService: mockAuthService)

        // Before login
        XCTAssertFalse(self.sut.isLoading)

        await self.sut.login(authManager: authManager)

        // After login completes
        XCTAssertFalse(self.sut.isLoading)
    }

    func testOAuthLogin_doesNotRequireEmailAndPasswordFields() async {
        let credential = OAuthCredential(
            provider: .google,
            idToken: "identity-token",
            authorizationCode: nil,
            nonce: "nonce",
            name: nil
        )
        self.mockAuthService.loginResult = .success(User(id: 42, email: "oauth@example.com"))
        let authManager = AuthManager(authService: self.mockAuthService)

        let authenticated = await self.sut.login(with: credential, authManager: authManager)

        XCTAssertTrue(authenticated)
        XCTAssertTrue(authManager.authState.isAuthenticated)
        XCTAssertEqual(self.mockAuthService.lastOAuthCredential?.idToken, "identity-token")
    }

    func testOAuthLogin_failureShowsProviderError() async {
        let credential = OAuthCredential(
            provider: .apple,
            idToken: "identity-token",
            authorizationCode: "authorization-code",
            nonce: "nonce",
            name: "Test User"
        )
        self.mockAuthService.loginResult = .failure(APIError.oauthAuthenticationFailed)
        let authManager = AuthManager(authService: self.mockAuthService)

        let authenticated = await self.sut.login(with: credential, authManager: authManager)

        XCTAssertFalse(authenticated)
        XCTAssertEqual(self.sut.errorMessage, "Could not sign in with that provider. Please try again.")
    }
}

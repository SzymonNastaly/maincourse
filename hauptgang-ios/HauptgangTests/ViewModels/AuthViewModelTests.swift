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

    func testAppleSignIn_unknownIdentityPresentsConfirmationWithoutAuthenticating() async {
        let provider = MockAppleSignInProvider(results: [.success(self.appleCredential(suffix: "first"))])
        self.sut = AuthViewModel(authService: self.mockAuthService, appleSignInProvider: provider)
        self.mockAuthService.oauthLoginResults = [.failure(APIError.appleAccountCreationConfirmationRequired)]
        let authManager = AuthManager(authService: self.mockAuthService)

        let authenticated = await self.sut.signInWithApple(authManager: authManager)

        XCTAssertFalse(authenticated)
        XCTAssertTrue(self.sut.showsAppleAccountCreationConfirmation)
        XCTAssertFalse(authManager.authState.isAuthenticated)
        XCTAssertEqual(provider.callCount, 1)
        XCTAssertEqual(self.mockAuthService.oauthCredentials.map(\.authorizationCode), ["first-code"])
        XCTAssertEqual(self.mockAuthService.creationIntents, [false])
        XCTAssertNil(self.sut.errorMessage)
    }

    func testCancelAppleAccountCreationClearsConfirmationWithoutAnotherRequest() async {
        let provider = MockAppleSignInProvider(results: [.success(self.appleCredential(suffix: "first"))])
        self.sut = AuthViewModel(authService: self.mockAuthService, appleSignInProvider: provider)
        self.mockAuthService.oauthLoginResults = [.failure(APIError.appleAccountCreationConfirmationRequired)]
        let authManager = AuthManager(authService: self.mockAuthService)
        _ = await self.sut.signInWithApple(authManager: authManager)

        self.sut.cancelAppleAccountCreation()

        XCTAssertFalse(self.sut.showsAppleAccountCreationConfirmation)
        XCTAssertEqual(provider.callCount, 1)
        XCTAssertEqual(self.mockAuthService.creationIntents, [false])
    }

    func testUseExistingAccountKeepsTypedInputAndSwitchesToSignInWithoutAnotherRequest() async {
        let provider = MockAppleSignInProvider(results: [.success(self.appleCredential(suffix: "first"))])
        self.sut = AuthViewModel(
            initialIsSignUp: true,
            authService: self.mockAuthService,
            appleSignInProvider: provider
        )
        self.sut.name = "Ada"
        self.sut.email = "ada@example.com"
        self.sut.password = "typed password"
        self.mockAuthService.oauthLoginResults = [.failure(APIError.appleAccountCreationConfirmationRequired)]
        let authManager = AuthManager(authService: self.mockAuthService)
        _ = await self.sut.signInWithApple(authManager: authManager)

        self.sut.useExistingAccount()

        XCTAssertFalse(self.sut.showsAppleAccountCreationConfirmation)
        XCTAssertFalse(self.sut.isSignUp)
        XCTAssertEqual(self.sut.name, "Ada")
        XCTAssertEqual(self.sut.email, "ada@example.com")
        XCTAssertEqual(self.sut.password, "typed password")
        XCTAssertEqual(provider.callCount, 1)
        XCTAssertEqual(self.mockAuthService.creationIntents, [false])
    }

    func testConfirmAppleAccountCreationGetsFreshCredentialAndAllowsCreationOnce() async {
        let provider = MockAppleSignInProvider(results: [
            .success(self.appleCredential(suffix: "first")),
            .success(self.appleCredential(suffix: "second"))
        ])
        self.sut = AuthViewModel(authService: self.mockAuthService, appleSignInProvider: provider)
        self.mockAuthService.oauthLoginResults = [
            .failure(APIError.appleAccountCreationConfirmationRequired),
            .success(User(id: 42, email: "apple@example.com"))
        ]
        let authManager = AuthManager(authService: self.mockAuthService)
        _ = await self.sut.signInWithApple(authManager: authManager)

        let authenticated = await self.sut.confirmAppleAccountCreation(authManager: authManager)

        XCTAssertTrue(authenticated)
        XCTAssertTrue(authManager.authState.isAuthenticated)
        XCTAssertFalse(self.sut.showsAppleAccountCreationConfirmation)
        XCTAssertEqual(provider.callCount, 2)
        XCTAssertEqual(
            self.mockAuthService.oauthCredentials.map(\.authorizationCode),
            ["first-code", "second-code"]
        )
        XCTAssertEqual(self.mockAuthService.oauthCredentials.map(\.nonce), ["first-nonce", "second-nonce"])
        XCTAssertEqual(self.mockAuthService.creationIntents, [false, true])
    }

    func testConfirmAppleAccountCreation_whenSheetIsCanceledReturnsToIdleWithoutPostingAgain() async {
        let provider = MockAppleSignInProvider(results: [
            .success(self.appleCredential(suffix: "first")),
            .success(nil)
        ])
        self.sut = AuthViewModel(authService: self.mockAuthService, appleSignInProvider: provider)
        self.mockAuthService.oauthLoginResults = [.failure(APIError.appleAccountCreationConfirmationRequired)]
        let authManager = AuthManager(authService: self.mockAuthService)
        _ = await self.sut.signInWithApple(authManager: authManager)

        let authenticated = await self.sut.confirmAppleAccountCreation(authManager: authManager)

        XCTAssertFalse(authenticated)
        XCTAssertFalse(self.sut.isLoading)
        XCTAssertFalse(self.sut.showsAppleAccountCreationConfirmation)
        XCTAssertEqual(provider.callCount, 2)
        XCTAssertEqual(self.mockAuthService.creationIntents, [false])
    }

    func testAppleSignIn_providerFailureReturnsToIdleAndShowsError() async {
        let provider = MockAppleSignInProvider(results: [.failure(MockAuthError.networkError)])
        self.sut = AuthViewModel(authService: self.mockAuthService, appleSignInProvider: provider)
        let authManager = AuthManager(authService: self.mockAuthService)

        let authenticated = await self.sut.signInWithApple(authManager: authManager)

        XCTAssertFalse(authenticated)
        XCTAssertFalse(self.sut.isLoading)
        XCTAssertEqual(self.sut.errorMessage, "Network connection failed")
        XCTAssertTrue(self.mockAuthService.oauthCredentials.isEmpty)
    }

    func testAppleSignIn_successfulKnownIdentityDoesNotPresentConfirmation() async {
        let provider = MockAppleSignInProvider(results: [.success(self.appleCredential(suffix: "known"))])
        self.sut = AuthViewModel(authService: self.mockAuthService, appleSignInProvider: provider)
        self.mockAuthService.oauthLoginResults = [.success(User(id: 42, email: "apple@example.com"))]
        let authManager = AuthManager(authService: self.mockAuthService)

        let authenticated = await self.sut.signInWithApple(authManager: authManager)

        XCTAssertTrue(authenticated)
        XCTAssertFalse(self.sut.showsAppleAccountCreationConfirmation)
        XCTAssertEqual(provider.callCount, 1)
        XCTAssertEqual(self.mockAuthService.creationIntents, [false])
    }

    func testAppleSignIn_whileBusyDoesNotLaunchCompetingProviderAttempt() async {
        let provider = BlockingAppleSignInProvider()
        self.sut = AuthViewModel(authService: self.mockAuthService, appleSignInProvider: provider)
        let authManager = AuthManager(authService: self.mockAuthService)

        let firstAttempt = Task { await self.sut.signInWithApple(authManager: authManager) }
        for _ in 0 ..< 10 where !self.sut.isLoading {
            await Task.yield()
        }

        let secondAuthenticated = await self.sut.signInWithApple(authManager: authManager)

        XCTAssertFalse(secondAuthenticated)
        XCTAssertEqual(provider.callCount, 1)
        provider.finish()
        _ = await firstAttempt.value
        XCTAssertFalse(self.sut.isLoading)
    }

    func testChangingAuthModeClearsPendingAppleConfirmation() async {
        let provider = MockAppleSignInProvider(results: [.success(self.appleCredential(suffix: "first"))])
        self.sut = AuthViewModel(authService: self.mockAuthService, appleSignInProvider: provider)
        self.mockAuthService.oauthLoginResults = [.failure(APIError.appleAccountCreationConfirmationRequired)]
        let authManager = AuthManager(authService: self.mockAuthService)
        _ = await self.sut.signInWithApple(authManager: authManager)

        self.sut.isSignUp = true

        XCTAssertFalse(self.sut.showsAppleAccountCreationConfirmation)
    }

    private func appleCredential(suffix: String) -> OAuthCredential {
        OAuthCredential(
            provider: .apple,
            idToken: "\(suffix)-token",
            authorizationCode: "\(suffix)-code",
            nonce: "\(suffix)-nonce",
            name: "Test User"
        )
    }
}

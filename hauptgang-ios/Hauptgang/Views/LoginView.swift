import SwiftUI

struct LoginView: View {
    @EnvironmentObject var authManager: AuthManager
    @StateObject private var viewModel: AuthViewModel
    @StateObject private var appleSignInService = AppleSignInService()
    @FocusState private var focusedField: Field?
    @State private var isProviderFlowActive = false
    @State private var isPasswordFlowActive = false

    private let isEmbeddedInOnboarding: Bool
    private let onAuthenticated: (() -> Void)?

    private enum Field {
        case name, email, password
    }

    init(
        isEmbeddedInOnboarding: Bool = false,
        startsInSignUpMode: Bool = false,
        onAuthenticated: (() -> Void)? = nil
    ) {
        self.isEmbeddedInOnboarding = isEmbeddedInOnboarding
        self.onAuthenticated = onAuthenticated
        self._viewModel = StateObject(wrappedValue: AuthViewModel(initialIsSignUp: startsInSignUpMode))
    }

    var body: some View {
        Group {
            if self.isEmbeddedInOnboarding {
                self.scrollingContent
            } else {
                ZStack {
                    Color.mcCanvas
                        .ignoresSafeArea()
                    self.scrollingContent
                }
            }
        }
        .onChange(of: self.focusedField) { old, _ in
            switch old {
            case .name: self.viewModel.nameDirty = true
            case .email: self.viewModel.emailDirty = true
            case .password: self.viewModel.passwordDirty = true
            case nil: break
            }
        }
    }

    private var scrollingContent: some View {
        GeometryReader { geometry in
            ScrollView {
                self.content
                    .frame(minHeight: geometry.size.height)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .simultaneousGesture(TapGesture().onEnded { self.focusedField = nil })
        }
    }

    private var content: some View {
        VStack(alignment: self.isEmbeddedInOnboarding ? .leading : .center, spacing: Theme.Spacing.xl) {
            if !self.isEmbeddedInOnboarding {
                Spacer()
            }

            self.logoHeader
            self.form
            self.modeToggle
                .frame(maxWidth: .infinity, alignment: .center)

            if !self.isEmbeddedInOnboarding {
                Spacer()
            }
        }
        .padding(.horizontal, self.isEmbeddedInOnboarding ? 0 : Theme.Spacing.lg)
        .padding(.top, self.isEmbeddedInOnboarding ? Theme.Spacing.xl : 0)
    }

    @ViewBuilder
    private var logoHeader: some View {
        if self.isEmbeddedInOnboarding {
            Text(self.viewModel.isSignUp ? "Create your account" : "Welcome back")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.mcInk)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            VStack(spacing: Theme.Spacing.md) {
                Image("LoginLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 80, height: 80)

                (Text("Cook something ")
                    .foregroundColor(.mcInk)
                    + Text("delicious")
                    .foregroundColor(.mcAccent)
                    + Text(" today")
                    .foregroundColor(.mcInk))
                    .font(.title2)
                    .fontWeight(.semibold)
            }
        }
    }

    private var form: some View {
        VStack(spacing: Theme.Spacing.md) {
            self.providerButtons
            self.authDivider

            VStack(spacing: 0) {
                if self.viewModel.isSignUp {
                    self.nameField
                    Divider()
                        .padding(.leading, Theme.Spacing.md)
                }
                self.emailField
                Divider()
                    .padding(.leading, Theme.Spacing.md)
                self.passwordField
            }
            .background(Color.mcSurface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.panel))
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Radius.panel)
                    .stroke(Color.mcHairline, lineWidth: 1)
            )

            self.errorSection

            self.submitButton
                .padding(.top, Theme.Spacing.xs)
        }
        .id(self.viewModel.isSignUp)
    }

    private var providerButtons: some View {
        VStack(spacing: Theme.Spacing.sm) {
            ContinueWithAppleButton(action: self.beginAppleSignIn)
                .disabled(self.isAuthBusy)

            if Constants.OAuth.isGoogleConfigured {
                ContinueWithGoogleButton(action: self.beginGoogleSignIn)
                    .disabled(self.isAuthBusy)
            }
        }
    }

    private var authDivider: some View {
        HStack(spacing: Theme.Spacing.sm) {
            Rectangle()
                .fill(Color.mcLine)
                .frame(height: 1)
            Text("or")
                .font(.caption.weight(.medium))
                .foregroundStyle(Color.mcMuted)
                .textCase(.uppercase)
            Rectangle()
                .fill(Color.mcLine)
                .frame(height: 1)
        }
        .accessibilityHidden(true)
    }

    private var nameField: some View {
        TextField("First name", text: self.$viewModel.name)
            .themeTextField(isError: self.viewModel.nameError != nil, isGrouped: true)
            .textContentType(.givenName)
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled()
            .focused(self.$focusedField, equals: .name)
            .submitLabel(.next)
            .onSubmit { self.focusedField = .email }
            .onChange(of: self.viewModel.name) { old, new in
                guard self.focusedField == .name else { return }
                if self.looksLikeAutofillJump(old: old, new: new),
                   !new.trimmingCharacters(in: .whitespaces).isEmpty {
                    self.focusedField = .email
                }
            }
            .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private var emailField: some View {
        TextField("Email", text: self.$viewModel.email)
            .themeTextField(isError: self.viewModel.emailError != nil, isGrouped: true)
            .textContentType(.emailAddress)
            .keyboardType(.emailAddress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .focused(self.$focusedField, equals: .email)
            .submitLabel(.next)
            .onSubmit { self.focusedField = .password }
            .onChange(of: self.viewModel.email) { old, new in
                guard self.focusedField == .email else { return }
                let trimmed = new.trimmingCharacters(in: .whitespaces)
                if self.looksLikeAutofillJump(old: old, new: new),
                   self.isCompleteEmail(trimmed) {
                    self.focusedField = .password
                }
            }
    }

    private var passwordField: some View {
        SecureField("Password", text: self.$viewModel.password)
            .themeTextField(isError: self.showPasswordLengthError, isGrouped: true)
            .textContentType(.password)
            .focused(self.$focusedField, equals: .password)
            .submitLabel(.go)
            .onSubmit(self.submitForm)
    }

    @ViewBuilder
    private var errorSection: some View {
        let hasErrors = self.viewModel.nameError != nil ||
            self.viewModel.emailError != nil ||
            self.showPasswordLengthError ||
            self.viewModel.errorMessage != nil

        if hasErrors {
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                if let error = self.viewModel.nameError {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(Color.mcDanger)
                }
                if let error = self.viewModel.emailError {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(Color.mcDanger)
                }
                if self.showPasswordLengthError {
                    Text("Password must be at least 12 characters")
                        .font(.caption)
                        .foregroundStyle(Color.mcDanger)
                }
                if let errorMessage = self.viewModel.errorMessage {
                    Label(errorMessage, systemImage: "exclamationmark.circle.fill")
                        .font(.subheadline)
                        .foregroundColor(.mcDanger)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Theme.Spacing.xs)
        }
    }

    private var submitButton: some View {
        Button(action: self.submitForm) {
            HStack(spacing: Theme.Spacing.sm) {
                if self.isPasswordFlowActive {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                }
                Text(self.buttonLabel)
                if !self.viewModel.isLoading {
                    Image(systemName: "arrow.right")
                        .font(.system(size: 14, weight: .semibold))
                }
            }
        }
        .primaryButton()
        .disabled(!self.viewModel.isFormValid || self.isAuthBusy)
    }

    private var modeToggle: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.25)) {
                self.viewModel.isSignUp.toggle()
            }
        } label: {
            if self.viewModel.isSignUp {
                (Text("Already have an account? ")
                    .foregroundColor(.mcBody)
                    + Text("Sign In")
                    .foregroundColor(.mcAccent)
                    .bold())
                    .font(.subheadline)
            } else {
                (Text("Don't have an account? ")
                    .foregroundColor(.mcBody)
                    + Text("Sign Up")
                    .foregroundColor(.mcAccent)
                    .bold())
                    .font(.subheadline)
            }
        }
    }

    private var showPasswordLengthError: Bool {
        self.viewModel.isSignUp && self.viewModel.passwordDirty &&
            !self.viewModel.password.isEmpty && self.viewModel.password.count < 12
    }

    private var buttonLabel: String {
        if self.viewModel.isSignUp {
            return self.isPasswordFlowActive ? "Creating Account…" : "Create Account"
        }
        return self.isPasswordFlowActive ? "Signing in…" : "Sign In"
    }

    private var isAuthBusy: Bool {
        self.isProviderFlowActive || self.viewModel.isLoading
    }

    /// Heuristic: a single change that adds more than one character at once
    /// is almost certainly autofill / paste rather than typing.
    private func looksLikeAutofillJump(old: String, new: String) -> Bool {
        new.count - old.count > 1
    }

    private func isCompleteEmail(_ value: String) -> Bool {
        let regex = #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"#
        return value.range(of: regex, options: .regularExpression) != nil
    }

    private func submitForm() {
        self.viewModel.markAllDirty()
        guard self.viewModel.isFormValid, !self.isAuthBusy else { return }
        self.focusedField = nil
        self.isPasswordFlowActive = true

        Task { @MainActor in
            defer { self.isPasswordFlowActive = false }
            let didAuthenticate: Bool = if self.viewModel.isSignUp {
                await self.viewModel.signup(authManager: self.authManager)
            } else {
                await self.viewModel.login(authManager: self.authManager)
            }

            if didAuthenticate {
                self.onAuthenticated?()
            }
        }
    }

    private func beginAppleSignIn() {
        guard !self.isAuthBusy else { return }
        self.isProviderFlowActive = true

        Task { @MainActor in
            defer { self.isProviderFlowActive = false }

            do {
                guard let credential = try await self.appleSignInService.signIn() else { return }
                await self.authenticate(with: credential)
            } catch {
                self.viewModel.present(error)
            }
        }
    }

    private func beginGoogleSignIn() {
        guard !self.isAuthBusy else { return }
        self.isProviderFlowActive = true

        Task { @MainActor in
            defer { self.isProviderFlowActive = false }

            do {
                guard let credential = try await GoogleSignInService.shared.signIn() else { return }
                await self.authenticate(with: credential)
            } catch {
                self.viewModel.present(error)
            }
        }
    }

    private func authenticate(with credential: OAuthCredential) async {
        let didAuthenticate = await self.viewModel.login(with: credential, authManager: self.authManager)
        if didAuthenticate {
            self.onAuthenticated?()
        }
    }
}

#Preview {
    LoginView()
        .environmentObject(AuthManager())
}

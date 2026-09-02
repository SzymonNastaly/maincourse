import SwiftUI

/// Root view that handles authentication routing
struct RootView: View {
    @EnvironmentObject var authManager: AuthManager
    @EnvironmentObject var subscriptionManager: SubscriptionManager
    @Environment(DeepLinkRouter.self) private var deepLinkRouter
    @State private var session = AuthenticatedSessionViewModel()
    @State private var showingInvitation = false
    @State private var invitationToken: String?

    /// Onboarding flag — set to a non-zero timestamp once the user finishes (or skips)
    /// the pre-signup onboarding flow. We use a TimeInterval rather than a Bool so we
    /// can debug exactly when each install completed if needed.
    @AppStorage(OnboardingService.completedAtDefaultsKey) private var onboardingCompletedAt: Double = 0

    var body: some View {
        Group {
            switch self.authManager.authState {
            case .unknown:
                SplashView()
            case .unauthenticated:
                if self.onboardingCompletedAt > 0 {
                    LoginView()
                } else {
                    OnboardingFlowView(onFinished: {
                        // @AppStorage reads from UserDefaults; the view model wrote the
                        // timestamp directly, but we re-read it here to trigger the swap.
                        self.onboardingCompletedAt = UserDefaults.standard
                            .double(forKey: OnboardingService.completedAtDefaultsKey)
                    })
                }
            case let .authenticated(user):
                AuthenticatedAppShell(user: user, session: self.session)
            }
        }
        // Note: animation removed to prevent iOS 26 Liquid Glass tab bar background initialization bug
        // .animation(.easeInOut(duration: 0.3), value: self.authManager.authState)
        .task {
            await self.authManager.checkAuthStatus()
        }
        .onChange(of: self.authManager.authState) { _, newValue in
            Task {
                switch newValue {
                case let .authenticated(user):
                    // Cookbook + recipe startup is owned by AuthenticatedSessionViewModel via
                    // AuthenticatedAppShell.task. Here we only handle authenticated-user
                    // concerns that aren't cookbook-scoped.
                    await self.subscriptionManager.identify(userId: String(user.id))
                    await self.subscriptionManager.refreshStatus()
                    await PushNotificationService.shared.setAuthenticated(true)
                    // Deliberately does not prompt — asking for notifications before the
                    // user has anything to be notified about spends the one system
                    // prompt iOS grants. The contextual triggers do the asking; this
                    // only refreshes the token and time zone for users who already said
                    // yes.
                    await PushNotificationService.shared.registerIfAuthorized()

                    // Check for invitation stored while unauthenticated
                    if let storedToken = self.deepLinkRouter.consumeStoredToken() {
                        self.invitationToken = storedToken
                        self.showingInvitation = true
                    }
                case .unauthenticated:
                    await self.session.reset()
                    await self.subscriptionManager.reset()
                case .unknown:
                    break
                }
            }
        }
        .onChange(of: self.deepLinkRouter.pendingInvitationToken) { _, token in
            guard let token else { return }
            self.deepLinkRouter.clearPendingInvitation()

            if self.authManager.authState.isAuthenticated {
                self.invitationToken = token
                self.showingInvitation = true
            } else {
                // Not logged in — store token and present after login
                self.deepLinkRouter.storePendingToken(token)
            }
        }
        .sheet(isPresented: self.$showingInvitation) {
            if let token = self.invitationToken {
                InvitationView(token: token) {
                    self.showingInvitation = false
                    self.invitationToken = nil
                }
                .environment(self.session)
                .environment(self.session.cookbookViewModel)
            }
        }
    }
}

// MARK: - Splash View

struct SplashView: View {
    var body: some View {
        ZStack {
            Color.hauptgangBackground

            Image("LaunchLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 96, height: 96)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea()
    }
}

#Preview("Splash") {
    SplashView()
}

#Preview("Root - Authenticated") {
    let authManager = AuthManager()
    let subscriptionManager = SubscriptionManager()
    return RootView()
        .environmentObject(authManager)
        .environmentObject(subscriptionManager)
        .environment(DeepLinkRouter())
}

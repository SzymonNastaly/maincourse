import SwiftData
import SwiftUI

/// Authenticated-user container that owns the startup splash overlay and starts the session.
/// All authenticated tabs are rendered inside this shell so that startup readiness has a
/// single, explicit owner.
struct AuthenticatedAppShell: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(OnboardingCoordinator.self) private var onboarding

    let user: User
    let session: AuthenticatedSessionViewModel

    @State private var showsStartupSplash = true
    @State private var retryAttempt = 0

    var body: some View {
        @Bindable var onboarding = self.onboarding
        ZStack {
            MainTabView()
                .environment(self.session)
                .environment(self.session.cookbookViewModel)
                .accessibilityHidden(self.showsStartupSplash || self.onboarding.showsContinuation)

            if self.onboarding.showsContinuation {
                OnboardingSaveView(onRetry: { self.retryAttempt += 1 })
                    .accessibilityAddTraits(.isModal)
                    .zIndex(1)
            }

            if self.showsStartupSplash {
                SplashView()
                    .zIndex(2)
            }
        }
        .fullScreenCover(isPresented: $onboarding.isDemoPresented) {
            AuthenticatedImportDemoView()
        }
        .task(id: self.continuationIdentity) {
            guard self.session.canDismissStartupSplash else { return }
            await self.resumeSave(refreshCookbooks: self.retryAttempt > 0)
        }
        .onChange(of: self.user.id) { _, _ in
            self.showsStartupSplash = true
        }
        .task(id: self.user.id) {
            await self.session.start(user: self.user, modelContext: self.modelContext)
        }
        .onChange(of: self.session.canDismissStartupSplash, initial: true) { _, canDismiss in
            guard canDismiss, self.showsStartupSplash else { return }
            // Animation kept minimal to avoid iOS 26 Liquid Glass tab bar background init glitch.
            withAnimation(.easeOut(duration: 0.08)) {
                self.showsStartupSplash = false
            }
        }
    }

    private var continuationIdentity: String {
        let requestId = self.onboarding.pending?.requestId.uuidString ?? "none"
        return "\(self.user.id)-\(requestId)-\(self.session.canDismissStartupSplash)-\(self.retryAttempt)"
    }

    private func resumeSave(refreshCookbooks: Bool = false) async {
        guard let requestId = self.onboarding.pending?.requestId else { return }
        if refreshCookbooks {
            await self.session.cookbookViewModel.refresh()
        }
        guard !Task.isCancelled, self.session.currentUser?.id == self.user.id,
              self.onboarding.pending?.requestId == requestId else { return }
        await self.onboarding.resume(
            userId: self.user.id,
            personalCookbookId: self.session.cookbookViewModel.cookbooks.first(where: \.personal)?.id
        )
    }
}

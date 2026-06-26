import RevenueCatUI
import SwiftUI

/// Settings screen with user info and sign out
struct SettingsView: View {
    @EnvironmentObject var authManager: AuthManager
    @EnvironmentObject var subscriptionManager: SubscriptionManager
    @State private var showingLogoutConfirmation = false
    @State private var showingPaywall = false
    @State private var showingCustomerCenter = false
    @State private var showingEditName = false

    var body: some View {
        NavigationStack {
            List {
                self.userSection
                self.cookbookSection
                self.subscriptionSection
                self.accountActionsSection
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.large)
            .sheet(isPresented: self.$showingPaywall) {
                PaywallView()
                    .onPurchaseCompleted { _ in
                        Task { await self.subscriptionManager.refreshStatus() }
                    }
                    .onRestoreCompleted { _ in
                        Task { await self.subscriptionManager.refreshStatus() }
                    }
            }
            .sheet(isPresented: self.$showingCustomerCenter) {
                CustomerCenterView()
            }
            .sheet(isPresented: self.$showingEditName) {
                EditNameView()
                    .environmentObject(self.authManager)
            }
        }
    }

    @ViewBuilder
    private var userSection: some View {
        if let user = self.authManager.authState.user {
            Section {
                HStack {
                    Image(systemName: "person.circle.fill")
                        .font(.title)
                        .foregroundColor(.hauptgangPrimary)

                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        Text("Signed in as")
                            .font(.caption)
                            .foregroundColor(.hauptgangTextSecondary)
                        Text(user.email)
                            .font(.body)
                            .foregroundColor(.hauptgangTextPrimary)
                    }
                }
                .padding(.vertical, Theme.Spacing.xs)

                Button {
                    self.showingEditName = true
                } label: {
                    HStack {
                        Image(systemName: "person.text.rectangle")
                            .foregroundColor(.hauptgangPrimary)
                        Text("Name")
                            .foregroundColor(.hauptgangTextPrimary)
                        Spacer()
                        Text(user.name?.isEmpty == false ? user.name! : "Add your name")
                            .foregroundColor(.hauptgangTextSecondary)
                            .lineLimit(1)
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundColor(.hauptgangTextSecondary)
                    }
                }

                NavigationLink {
                    ManageAccountView()
                        .environmentObject(self.authManager)
                } label: {
                    HStack {
                        Image(systemName: "person.crop.circle.badge.exclamationmark")
                            .foregroundColor(.hauptgangPrimary)
                        Text("Manage Account")
                    }
                }
            }
        }
    }

    private var cookbookSection: some View {
        Section("Cookbooks") {
            NavigationLink {
                CookbookSettingsView()
            } label: {
                HStack {
                    Image(systemName: "book.closed.fill")
                        .foregroundColor(.hauptgangPrimary)
                    Text("Manage Cookbooks")
                }
            }
        }
    }

    private var subscriptionSection: some View {
        Section("Subscription") {
            if self.subscriptionManager.isPro {
                self.proSubscriptionContent
            } else {
                self.freeSubscriptionContent
            }
        }
    }

    private var proSubscriptionContent: some View {
        Group {
            HStack {
                Image(systemName: "crown.fill")
                    .foregroundColor(.yellow)
                Text("MainCourse Pro")
                    .fontWeight(.semibold)
            }
            Button {
                self.showingCustomerCenter = true
            } label: {
                HStack {
                    Image(systemName: "gearshape")
                    Text("Manage Subscription")
                }
            }
        }
    }

    private var freeSubscriptionContent: some View {
        Group {
            HStack {
                Image(systemName: "person")
                    .foregroundColor(.hauptgangTextSecondary)
                Text("Free Plan")
            }
            Button {
                self.showingPaywall = true
            } label: {
                HStack {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                    Text("Upgrade to Pro")
                }
            }
        }
    }

    private var accountActionsSection: some View {
        Section {
            Button(role: .destructive) {
                self.showingLogoutConfirmation = true
            } label: {
                HStack {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                    Text("Sign Out")
                }
            }
            .confirmationDialog(
                "Sign out?",
                isPresented: self.$showingLogoutConfirmation,
                titleVisibility: .visible
            ) {
                Button("Sign out", role: .destructive) {
                    Task { await self.authManager.signOut() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("You'll need to sign in again to access your account.")
            }
        }
    }
}

#Preview {
    let authManager = AuthManager()
    let subscriptionManager = SubscriptionManager()
    return SettingsView()
        .environmentObject(authManager)
        .environmentObject(subscriptionManager)
        .onAppear {
            authManager.signIn(user: User(id: 1, email: "test@example.com"))
        }
}

import SwiftUI

/// Dedicated account-management screen reached from Settings.
/// Keeps destructive actions one level deeper than Sign Out to avoid misclicks.
struct ManageAccountView: View {
    @EnvironmentObject var authManager: AuthManager

    var body: some View {
        List {
            if let user = self.authManager.authState.user {
                Section("Signed in as") {
                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        Text(user.email)
                            .font(.body)
                            .foregroundColor(.mcInk)
                        if let name = user.name, !name.isEmpty {
                            Text(name)
                                .font(.caption)
                                .foregroundColor(.mcBody)
                        }
                    }
                    .padding(.vertical, Theme.Spacing.xs)
                }
            }

            Section {
                NavigationLink {
                    DeleteAccountConfirmationView()
                        .environmentObject(self.authManager)
                } label: {
                    HStack {
                        Image(systemName: "trash")
                            .foregroundColor(.red)
                        Text("Delete Account")
                            .foregroundColor(.red)
                    }
                }
            } footer: {
                Text(
                    """
                    Deleting your account permanently removes your personal cookbook, recipes, \
                    shopping list, and meal plans. Cookbooks you share with others will be \
                    transferred to a collaborator.
                    """
                )
            }
        }
        .navigationTitle("Manage Account")
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    let authManager = AuthManager()
    return NavigationStack {
        ManageAccountView()
            .environmentObject(authManager)
    }
    .onAppear {
        authManager.signIn(user: User(id: 1, email: "test@example.com"))
    }
}

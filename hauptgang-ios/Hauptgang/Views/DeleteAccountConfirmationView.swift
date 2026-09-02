import SwiftUI

/// Final confirmation screen for account deletion. Requires the user to type
/// the confirmation phrase before the destructive action is enabled — this is
/// intentionally heavier than a single alert tap to avoid accidental deletion.
struct DeleteAccountConfirmationView: View {
    @EnvironmentObject var authManager: AuthManager
    @State private var typedConfirmation: String = ""
    @State private var isDeleting = false
    @State private var errorMessage: String?

    private let requiredPhrase = "DELETE"

    private var canDelete: Bool {
        self.typedConfirmation == self.requiredPhrase && !self.isDeleting
    }

    var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: Theme.Spacing.md) {
                    Label("This cannot be undone", systemImage: "exclamationmark.triangle.fill")
                        .font(.headline)
                        .foregroundColor(.red)
                    Text(
                        "Deleting your account will permanently remove:"
                    )
                    .foregroundColor(.mcInk)
                    VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                        self.bulletRow("Your personal cookbook and all its recipes")
                        self.bulletRow("Your shopping list and meal plans")
                        self.bulletRow("Your subscription will not be refunded")
                    }
                    Text(
                        """
                        Cookbooks you share with others will be transferred to a collaborator \
                        so their data is preserved.
                        """
                    )
                    .foregroundColor(.mcBody)
                    .font(.callout)
                }
                .padding(.vertical, Theme.Spacing.xs)
            }

            Section {
                Text("Type **\(self.requiredPhrase)** to confirm")
                    .foregroundColor(.mcBody)
                TextField(self.requiredPhrase, text: self.$typedConfirmation)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .disableAutocorrection(true)
            }

            Section {
                Button(role: .destructive) {
                    Task { await self.performDelete() }
                } label: {
                    HStack {
                        if self.isDeleting {
                            ProgressView()
                        }
                        Text("Delete My Account")
                            .frame(maxWidth: .infinity)
                            .fontWeight(.semibold)
                    }
                }
                .disabled(!self.canDelete)
            }
        }
        .navigationTitle("Delete Account")
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(self.isDeleting)
        .alert(
            "Couldn't delete account",
            isPresented: Binding(
                get: { self.errorMessage != nil },
                set: {
                    if !$0 {
                        self.errorMessage = nil
                    }
                }
            )
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(self.errorMessage ?? "")
        }
    }

    private func bulletRow(_ text: String) -> some View {
        HStack(alignment: .top, spacing: Theme.Spacing.sm) {
            Text("•")
            Text(text)
        }
        .foregroundColor(.mcInk)
    }

    private func performDelete() async {
        self.isDeleting = true
        defer { self.isDeleting = false }
        do {
            try await self.authManager.deleteAccount()
        } catch {
            self.errorMessage = error.localizedDescription
        }
    }
}

#Preview {
    let authManager = AuthManager()
    return NavigationStack {
        DeleteAccountConfirmationView()
            .environmentObject(authManager)
    }
    .onAppear {
        authManager.signIn(user: User(id: 1, email: "test@example.com"))
    }
}

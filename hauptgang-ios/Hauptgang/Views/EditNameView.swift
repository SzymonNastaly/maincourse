import SwiftUI

/// Sheet for editing the current user's display name.
struct EditNameView: View {
    @EnvironmentObject var authManager: AuthManager
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var isSaving = false
    @State private var errorMessage: String?
    @FocusState private var isNameFocused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Your name", text: self.$name)
                        .textContentType(.name)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled()
                        .focused(self.$isNameFocused)
                } header: {
                    Text("Name")
                } footer: {
                    Text("Shown to other members of cookbooks you share, including in push notifications.")
                }

                if let errorMessage = self.errorMessage {
                    Section {
                        Label(errorMessage, systemImage: "exclamationmark.circle.fill")
                            .foregroundColor(.mcDanger)
                            .font(.subheadline)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.mcCanvas)
            .navigationTitle("Edit Name")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { self.dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { self.save() }
                        .disabled(!self.canSave || self.isSaving)
                }
            }
            .onAppear {
                self.name = self.authManager.authState.user?.name ?? ""
                self.isNameFocused = true
            }
        }
    }

    private var trimmed: String {
        self.name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSave: Bool {
        !self.trimmed.isEmpty && self.trimmed != self.authManager.authState.user?.name
    }

    private func save() {
        guard self.canSave, !self.isSaving else { return }
        self.isSaving = true
        self.errorMessage = nil

        Task {
            do {
                try await self.authManager.updateName(self.trimmed)
                self.dismiss()
            } catch let error as APIError {
                self.errorMessage = error.localizedDescription
            } catch {
                self.errorMessage = "An unexpected error occurred. Please try again."
            }
            self.isSaving = false
        }
    }
}

import SwiftUI

/// Sheet presented when the user opens an invitation deep link
struct InvitationView: View {
    let token: String
    let onComplete: () -> Void

    @Environment(AuthenticatedSessionViewModel.self) private var session
    @Environment(CookbookViewModel.self) private var cookbookViewModel
    @State private var preview: CookbookInvitationPreview?
    @State private var state: InvitationState = .loading
    @State private var errorMessage: String?

    private let service: CookbookServiceProtocol

    init(token: String, service: CookbookServiceProtocol = CookbookService.shared, onComplete: @escaping () -> Void) {
        self.token = token
        self.service = service
        self.onComplete = onComplete
    }

    var body: some View {
        NavigationStack {
            Group {
                switch self.state {
                case .loading:
                    self.loadingContent
                case .preview:
                    self.previewContent
                case .accepting:
                    self.acceptingContent
                case .accepted:
                    self.acceptedContent
                case .error:
                    self.errorContent
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if self.state != .accepting {
                        Button("Close") { self.onComplete() }
                    }
                }
            }
        }
        .task {
            await self.fetchPreview()
        }
    }

    // MARK: - Content Views

    private var loadingContent: some View {
        VStack(spacing: Theme.Spacing.md) {
            Spacer()
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .hauptgangPrimary))
            Text("Loading invitation...")
                .font(.subheadline)
                .foregroundStyle(Color.hauptgangTextSecondary)
            Spacer()
        }
    }

    private var previewContent: some View {
        VStack(spacing: Theme.Spacing.lg) {
            Spacer()

            Image(systemName: "person.2.circle.fill")
                .font(.system(size: 60))
                .foregroundStyle(Color.hauptgangPrimary)

            Text("You've been invited!")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundStyle(Color.hauptgangTextPrimary)

            if let preview {
                VStack(spacing: Theme.Spacing.sm) {
                    Text(preview.cookbookName)
                        .font(.title3)
                        .fontWeight(.semibold)
                        .foregroundStyle(Color.hauptgangTextPrimary)

                    Text("Invited by \(preview.inviterEmail)")
                        .font(.subheadline)
                        .foregroundStyle(Color.hauptgangTextSecondary)
                }
            }

            self.sharedCookbookWarning
            self.previewActions

            Spacer()
        }
    }

    @ViewBuilder
    private var sharedCookbookWarning: some View {
        if self.cookbookViewModel.hasSharedCookbook {
            HStack(spacing: Theme.Spacing.sm) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Color.hauptgangAmber)
                Text("You already have a shared cookbook. You must leave it before joining another.")
                    .font(.caption)
                    .foregroundStyle(Color.hauptgangTextSecondary)
            }
            .padding(Theme.Spacing.md)
            .background(Color.hauptgangSurfaceRaised)
            .clipShape(RoundedRectangle(cornerRadius: Theme.CornerRadius.md))
            .padding(.horizontal, Theme.Spacing.xl)
        }
    }

    private var previewActions: some View {
        VStack(spacing: Theme.Spacing.md) {
            Button {
                Task { await self.acceptInvitation() }
            } label: {
                Text("Join Cookbook")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Theme.Spacing.md)
            }
            .buttonStyle(.borderedProminent)
            .tint(.hauptgangPrimary)
            .disabled(self.cookbookViewModel.hasSharedCookbook)

            Button {
                Task { await self.declineInvitation() }
            } label: {
                Text("Decline")
                    .font(.subheadline)
                    .foregroundStyle(Color.hauptgangTextSecondary)
            }
        }
        .padding(.horizontal, Theme.Spacing.xl)
    }

    private var acceptingContent: some View {
        VStack(spacing: Theme.Spacing.md) {
            Spacer()
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .hauptgangPrimary))
            Text("Joining cookbook...")
                .font(.subheadline)
                .foregroundStyle(Color.hauptgangTextSecondary)
            Spacer()
        }
    }

    private var acceptedContent: some View {
        VStack(spacing: Theme.Spacing.lg) {
            Spacer()

            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 60))
                .foregroundStyle(Color.hauptgangSuccess)

            Text("You're in!")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundStyle(Color.hauptgangTextPrimary)

            if let preview {
                Text("You've joined \(preview.cookbookName)")
                    .font(.subheadline)
                    .foregroundStyle(Color.hauptgangTextSecondary)
            }

            Button {
                self.onComplete()
            } label: {
                Text("Done")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Theme.Spacing.md)
            }
            .buttonStyle(.borderedProminent)
            .tint(.hauptgangPrimary)
            .padding(.horizontal, Theme.Spacing.xl)

            Spacer()
        }
    }

    private var errorContent: some View {
        VStack(spacing: Theme.Spacing.lg) {
            Spacer()

            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 60))
                .foregroundStyle(Color.hauptgangError)

            Text("Invitation Error")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundStyle(Color.hauptgangTextPrimary)

            if let errorMessage {
                Text(errorMessage)
                    .font(.subheadline)
                    .foregroundStyle(Color.hauptgangTextSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Theme.Spacing.xl)
            }

            Button {
                self.onComplete()
            } label: {
                Text("Close")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Theme.Spacing.md)
            }
            .buttonStyle(.borderedProminent)
            .tint(.hauptgangPrimary)
            .padding(.horizontal, Theme.Spacing.xl)

            Spacer()
        }
    }

    // MARK: - Actions

    private func fetchPreview() async {
        do {
            let preview = try await service.fetchInvitationPreview(token: self.token)

            if preview.status != "pending" {
                self.errorMessage = "This invitation is no longer available."
                self.state = .error
                return
            }

            if preview.expiresAt < Date() {
                self.errorMessage = "This invitation has expired."
                self.state = .error
                return
            }

            self.preview = preview
            self.state = .preview
        } catch {
            self.errorMessage = error.localizedDescription
            self.state = .error
        }
    }

    private func acceptInvitation() async {
        self.state = .accepting
        do {
            let response = try await service.acceptInvitation(token: self.token)
            await self.cookbookViewModel.loadCookbooks()
            if let cookbook = cookbookViewModel.cookbooks.first(where: { $0.id == response.cookbookId }) {
                await self.session.switchCookbook(cookbook)
            }
            await PushNotificationService.shared.promptForAuthorization()
            self.state = .accepted
        } catch {
            self.errorMessage = error.localizedDescription
            self.state = .error
        }
    }

    private func declineInvitation() async {
        do {
            try await self.service.rejectInvitation(token: self.token)
        } catch {
            // Best-effort — user can always just close
        }
        self.onComplete()
    }
}

// MARK: - State

private enum InvitationState {
    case loading
    case preview
    case accepting
    case accepted
    case error
}

#Preview {
    InvitationView(token: "preview-token") {}
        .environment(CookbookViewModel())
}

import SwiftUI

enum ImportState {
    case extracting
    case importing(URL?)
    case success
    case failed(String)
    case notAuthenticated
}

struct ImportRecipeView: View {
    let state: ImportState
    let onClose: () -> Void
    var onOpenApp: (() -> Void)?

    var body: some View {
        VStack(spacing: Theme.Spacing.lg) {
            switch self.state {
            case .extracting:
                self.extractingView
            case let .importing(url):
                self.importingView(url: url)
            case .success:
                self.successView
            case let .failed(message):
                self.failedView(message: message)
            case .notAuthenticated:
                self.notAuthenticatedView
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(Theme.Spacing.lg)
        .background(Color.mcCanvas)
    }

    private var extractingView: some View {
        VStack(spacing: Theme.Spacing.md) {
            ProgressView()
                .tint(Color.mcAccent)
                .scaleEffect(1.5)
            Text("Processing...")
                .font(.headline)
                .foregroundColor(Color.mcBody)
        }
    }

    private func importingView(url: URL?) -> some View {
        VStack(spacing: Theme.Spacing.md) {
            ProgressView()
                .tint(Color.mcAccent)
                .scaleEffect(1.5)
            Text("Importing Recipe")
                .font(.headline)
                .foregroundColor(Color.mcInk)
            if let url {
                Text(url.host ?? url.absoluteString)
                    .font(.mcMono(.footnote))
                    .foregroundColor(Color.mcBody)
                    .lineLimit(1)
                    .truncationMode(.middle)
            } else {
                Text("From photo")
                    .font(.subheadline)
                    .foregroundColor(Color.mcBody)
            }
        }
    }

    private var successView: some View {
        VStack(spacing: Theme.Spacing.md) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 48))
                .foregroundColor(Color.mcAccent)
            Text("Import Started!")
                .font(.headline)
                .foregroundColor(Color.mcInk)
            Text("Open MainCourse to see your recipe.")
                .font(.subheadline)
                .foregroundColor(Color.mcBody)
        }
    }

    private func failedView(message: String) -> some View {
        VStack(spacing: Theme.Spacing.md) {
            Image(systemName: "xmark.circle.fill")
                .font(.system(size: 48))
                .foregroundColor(Color.mcDanger)
            Text("Import Failed")
                .font(.headline)
                .foregroundColor(Color.mcInk)
            Text(message)
                .font(.subheadline)
                .foregroundColor(Color.mcBody)
                .multilineTextAlignment(.center)
            Button("Close", action: self.onClose)
                .primaryButton()
        }
    }

    private var notAuthenticatedView: some View {
        VStack(spacing: Theme.Spacing.md) {
            Image(systemName: "person.crop.circle.badge.exclamationmark")
                .font(.system(size: 48))
                .foregroundColor(Color.mcAmber)
            Text("Not Signed In")
                .font(.headline)
                .foregroundColor(Color.mcInk)
            Text("Please open MainCourse and sign in first.")
                .font(.subheadline)
                .foregroundColor(Color.mcBody)
                .multilineTextAlignment(.center)
            HStack(spacing: Theme.Spacing.sm) {
                Button("Close", action: self.onClose)
                    .outlineButton()
                if let onOpenApp {
                    Button("Open App", action: onOpenApp)
                        .primaryButton()
                }
            }
        }
    }
}

#Preview("Extracting") {
    ImportRecipeView(state: .extracting, onClose: {})
}

#Preview("Importing") {
    ImportRecipeView(
        state: .importing(URL(string: "https://example.com/recipe")),
        onClose: {}
    )
}

#Preview("Success") {
    ImportRecipeView(state: .success, onClose: {})
}

#Preview("Failed") {
    ImportRecipeView(
        state: .failed("Could not parse recipe from this URL"),
        onClose: {}
    )
}

#Preview("Not Authenticated") {
    ImportRecipeView(state: .notAuthenticated, onClose: {})
}

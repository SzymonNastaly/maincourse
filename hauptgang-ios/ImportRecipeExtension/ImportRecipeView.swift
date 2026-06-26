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
        VStack(spacing: 24) {
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
        .padding(24)
        .background(Color(.systemBackground))
    }

    private var extractingView: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.5)
            Text("Processing...")
                .font(.headline)
                .foregroundColor(.secondary)
        }
    }

    private func importingView(url: URL?) -> some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.5)
            Text("Importing Recipe")
                .font(.headline)
            if let url {
                Text(url.host ?? url.absoluteString)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
            } else {
                Text("From photo")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
    }

    private var successView: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 48))
                .foregroundColor(.green)
            Text("Import Started!")
                .font(.headline)
            Text("Open MainCourse to see your recipe.")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }

    private func failedView(message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "xmark.circle.fill")
                .font(.system(size: 48))
                .foregroundColor(.red)
            Text("Import Failed")
                .font(.headline)
            Text(message)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            Button("Close", action: self.onClose)
                .buttonStyle(.borderedProminent)
        }
    }

    private var notAuthenticatedView: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.crop.circle.badge.exclamationmark")
                .font(.system(size: 48))
                .foregroundColor(.orange)
            Text("Not Signed In")
                .font(.headline)
            Text("Please open MainCourse and sign in first.")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            HStack(spacing: 12) {
                Button("Close", action: self.onClose)
                    .buttonStyle(.bordered)
                if let onOpenApp {
                    Button("Open App", action: onOpenApp)
                        .buttonStyle(.borderedProminent)
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

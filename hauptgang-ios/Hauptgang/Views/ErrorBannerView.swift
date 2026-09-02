import SwiftData
import SwiftUI

struct ErrorBannerView: View {
    let recipe: PersistedRecipe
    let onDismiss: () -> Void

    @State private var offset: CGFloat = 0
    @State private var isDismissing = false

    /// Swipe distance required to trigger dismiss
    private let dismissThreshold: CGFloat = 80

    /// Auto-dismiss delay in seconds
    private let autoDismissDelay: Double = 5.0

    /// Vertical offset for dismiss animation
    @State private var verticalOffset: CGFloat = 0

    var body: some View {
        HStack(spacing: Theme.Spacing.md) {
            // Error icon
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.title3)
                .foregroundColor(Color.mcDanger)
                .frame(width: 24)

            // Error message text
            VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                if let errorMessage = recipe.errorMessage {
                    Text(errorMessage)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(Color.mcDanger)
                        .multilineTextAlignment(.leading)
                } else {
                    // Fallback for recipes without error_message
                    Text("Import failed - page is not supported")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(Color.mcDanger)
                }
            }

            Spacer()
        }
        .padding(Theme.Spacing.md)
        .background(Color.mcDangerTint)
        .clipShape(.rect(cornerRadius: Theme.Radius.card))
        .overlay {
            RoundedRectangle(cornerRadius: Theme.Radius.card)
                .stroke(Color.mcDangerLine, lineWidth: 1)
        }
        .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 2)
        .offset(x: self.offset, y: self.verticalOffset)
        .opacity(self.dismissOpacity)
        .gesture(
            DragGesture()
                .onChanged { value in
                    self.offset = value.translation.width
                }
                .onEnded { value in
                    let swipeDistance = abs(value.translation.width)
                    if swipeDistance > self.dismissThreshold {
                        self.dismiss(direction: value.translation.width > 0 ? 1 : -1)
                    } else {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                            self.offset = 0
                        }
                    }
                }
        )
        .task {
            try? await Task.sleep(for: .seconds(self.autoDismissDelay))
            if !self.isDismissing {
                self.autoDismiss()
            }
        }
    }

    /// Opacity decreases as user swipes further or during dismiss
    private var dismissOpacity: Double {
        // During vertical dismiss animation
        if self.verticalOffset > 0 {
            return max(0, 1 - self.verticalOffset / 40)
        }
        // During horizontal swipe
        let progress = abs(offset) / self.dismissThreshold
        return max(0.3, 1 - progress * 0.5)
    }

    /// Animate off screen horizontally (swipe dismiss)
    private func dismiss(direction: CGFloat) {
        guard !self.isDismissing else { return }
        self.isDismissing = true

        withAnimation(.easeOut(duration: 0.2)) {
            self.offset = direction * 400
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            self.onDismiss()
        }
    }

    /// Animate down gently (auto dismiss)
    private func autoDismiss() {
        guard !self.isDismissing else { return }
        self.isDismissing = true

        withAnimation(.easeInOut(duration: 0.3)) {
            self.verticalOffset = 100
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            self.onDismiss()
        }
    }
}

// MARK: - Preview

#Preview("Single Error") {
    ErrorBannerView(
        recipe: {
            let recipe = PersistedRecipe(
                id: 1,
                name: "Importing...",
                favorite: false,
                updatedAt: Date()
            )
            recipe.importStatus = "failed"
            recipe.errorMessage = "Import from allrecipes.com failed - page is not supported"
            return recipe
        }(),
        onDismiss: {}
    )
    .padding()
}

#Preview("Multiple Errors") {
    VStack(spacing: Theme.Spacing.sm) {
        ErrorBannerView(
            recipe: {
                let recipe = PersistedRecipe(id: 1, name: "Test", favorite: false, updatedAt: Date())
                recipe.importStatus = "failed"
                recipe.errorMessage = "Import from allrecipes.com failed - page is not supported"
                return recipe
            }(),
            onDismiss: {}
        )

        ErrorBannerView(
            recipe: {
                let recipe = PersistedRecipe(id: 2, name: "Test", favorite: false, updatedAt: Date())
                recipe.importStatus = "failed"
                recipe.errorMessage = "Import from epicurious.com failed - page is not supported"
                return recipe
            }(),
            onDismiss: {}
        )
    }
    .padding()
}

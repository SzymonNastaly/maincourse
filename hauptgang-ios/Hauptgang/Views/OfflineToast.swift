import SwiftUI

struct OfflineToast: ViewModifier {
    let isOffline: Bool
    var showToast: Bool = true

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .top) {
                if self.isOffline && self.showToast {
                    self.toastLabel
                        .modifier(OfflineToastBackground())
                        .padding(.top, Theme.Spacing.xs)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .animation(.easeInOut(duration: 0.3), value: self.isOffline && self.showToast)
    }
}

extension OfflineToast {
    private var toastLabel: some View {
        HStack(spacing: Theme.Spacing.xs) {
            Image(systemName: "wifi.slash")
                .font(.caption2)
            Text("Offline")
                .font(.caption)
                .fontWeight(.medium)
        }
        .foregroundStyle(Color.mcBody)
        .padding(.horizontal, Theme.Spacing.sm)
        .padding(.vertical, Theme.Spacing.xs)
    }
}

private struct OfflineToastBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content
                .glassEffect(.regular, in: .capsule)
        } else {
            content
                .background(.ultraThinMaterial)
                .clipShape(Capsule())
                .shadow(color: .black.opacity(0.1), radius: 4, y: 2)
        }
    }
}

extension View {
    func offlineToast(isOffline: Bool, showToast: Bool = true) -> some View {
        modifier(OfflineToast(isOffline: isOffline, showToast: showToast))
    }
}

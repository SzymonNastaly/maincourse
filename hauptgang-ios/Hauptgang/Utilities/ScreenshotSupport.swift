import Foundation

/// Local screenshot harness. Only Debug simulator builds can enable it.
/// Readiness is written outside the UI so capture waits for real image loads.
enum ScreenshotSupport {
    static var isEnabled: Bool {
        #if DEBUG && targetEnvironment(simulator)
        UserDefaults.standard.bool(forKey: "screenshots")
        #else
        false
        #endif
    }

    #if DEBUG && targetEnvironment(simulator)
    @MainActor private static var stage = "starting"
    @MainActor private static var pendingImages: Set<UUID> = []
    @MainActor private static var failures: [String] = []

    @MainActor
    static func signIn(_ authManager: AuthManager) async {
        self.stage = "starting"
        self.pendingImages = []
        self.failures = []
        self.writeStatus()
        await KeychainService.shared.clearAll()
        do {
            let user = try await AuthService.shared.login(
                email: "screenshots@example.test",
                password: "maincourse-screenshots"
            )
            await CookbookContext.shared.configure(userId: user.id)
            await CookbookContext.shared.reset()
            authManager.signIn(user: user)
        } catch {
            self.failures.append("Screenshot sign-in: \(error.localizedDescription)")
            self.writeStatus()
        }
    }

    @MainActor
    static func sessionStarted(_ session: AuthenticatedSessionViewModel) {
        guard self.isEnabled else { return }
        if case .ready = session.startupState {
            self.stage = "ready"
        } else {
            self.failures.append("Screenshot session did not reach ready")
        }
        self.writeStatus()
    }

    @MainActor
    static func imageStarted(_ id: UUID) {
        guard self.isEnabled else { return }
        self.pendingImages.insert(id)
        self.writeStatus()
    }

    @MainActor
    static func imageFinished(_ id: UUID, error: String? = nil) {
        guard self.isEnabled else { return }
        self.pendingImages.remove(id)
        if let error {
            self.failures.append(error)
        }
        self.writeStatus()
    }

    @MainActor
    private static func writeStatus() {
        let status: [String: Any] = [
            "stage": self.stage,
            "pending_images": self.pendingImages.count,
            "failures": self.failures,
            "updated_at": Date().timeIntervalSince1970
        ]
        do {
            let data = try JSONSerialization.data(withJSONObject: status, options: .sortedKeys)
            try data.write(
                to: URL.documentsDirectory.appendingPathComponent("screenshot-status.json"),
                options: .atomic
            )
        } catch {
            // The runner treats a missing status file as a failed capture.
            print("Cannot write screenshot readiness: \(error)")
        }
    }
    #endif
}

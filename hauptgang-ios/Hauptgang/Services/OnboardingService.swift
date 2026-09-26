import Foundation

/// UserDefaults flags for the pre-signup introduction.
enum OnboardingService {
    /// UserDefaults key for the completion timestamp. Non-zero means the user has been
    /// through the introduction (or chose to log in) and we should jump straight to
    /// login on subsequent launches.
    static let completedAtDefaultsKey = "hauptgang.onboarding.completedAt"

    /// UserDefaults key for remembering that the user chose to keep the example recipe
    /// and should resume at the embedded auth screen.
    static let authStepReachedAtDefaultsKey = "hauptgang.onboarding.authStepReachedAt"

    static func hasReachedAuthenticationStep() -> Bool {
        UserDefaults.standard.double(forKey: self.authStepReachedAtDefaultsKey) > 0
    }
}

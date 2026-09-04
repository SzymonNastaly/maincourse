@preconcurrency import GoogleSignIn
import UIKit

@MainActor
final class GoogleSignInService {
    static let shared = GoogleSignInService()

    private init() {}

    func signIn() async throws -> OAuthCredential? {
        guard Constants.OAuth.isGoogleConfigured else {
            throw OAuthSignInError.googleNotConfigured
        }
        guard let presenter = self.presentingViewController else {
            throw OAuthSignInError.missingPresenter
        }

        let nonce = UUID().uuidString

        do {
            let result = try await GIDSignIn.sharedInstance.signIn(
                withPresenting: presenter,
                hint: nil,
                additionalScopes: nil,
                nonce: nonce
            )
            guard let idToken = result.user.idToken?.tokenString else {
                throw OAuthSignInError.missingIdentityToken
            }

            return OAuthCredential(
                provider: .google,
                idToken: idToken,
                authorizationCode: nil,
                nonce: nonce,
                name: nil
            )
        } catch {
            let nsError = error as NSError
            if nsError.domain == kGIDSignInErrorDomain,
               nsError.code == GIDSignInError.canceled.rawValue {
                return nil
            }
            throw error
        }
    }

    func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }

    func disconnect() async {
        do {
            try await GIDSignIn.sharedInstance.disconnect()
        } catch {
            GIDSignIn.sharedInstance.signOut()
        }
    }

    private var presentingViewController: UIViewController? {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }),
            var presenter = scene.windows.first(where: \.isKeyWindow)?.rootViewController
        else {
            return nil
        }

        while let presented = presenter.presentedViewController {
            presenter = presented
        }
        return presenter
    }
}

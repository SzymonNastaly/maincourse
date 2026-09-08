import Foundation
@testable import Hauptgang

@MainActor
final class MockAppleSignInProvider: AppleSignInProviding {
    var results: [Result<OAuthCredential?, Error>]
    private(set) var callCount = 0

    init(results: [Result<OAuthCredential?, Error>] = []) {
        self.results = results
    }

    func signIn() async throws -> OAuthCredential? {
        self.callCount += 1
        guard !self.results.isEmpty else { return nil }
        return try self.results.removeFirst().get()
    }
}

@MainActor
final class BlockingAppleSignInProvider: AppleSignInProviding {
    private(set) var callCount = 0
    private var continuation: CheckedContinuation<OAuthCredential?, Never>?

    func signIn() async throws -> OAuthCredential? {
        self.callCount += 1
        return await withCheckedContinuation { continuation in
            self.continuation = continuation
        }
    }

    func finish(with credential: OAuthCredential? = nil) {
        self.continuation?.resume(returning: credential)
        self.continuation = nil
    }
}

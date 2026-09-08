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
    private var signInStarted = false
    private var signInStartedContinuation: CheckedContinuation<Void, Never>?
    private var releaseContinuation: CheckedContinuation<OAuthCredential?, Never>?

    func signIn() async throws -> OAuthCredential? {
        self.callCount += 1
        self.signInStarted = true
        self.signInStartedContinuation?.resume()
        self.signInStartedContinuation = nil
        return await withCheckedContinuation { continuation in
            self.releaseContinuation = continuation
        }
    }

    func waitUntilSignInStarted() async {
        if self.signInStarted {
            return
        }
        await withCheckedContinuation { continuation in
            self.signInStartedContinuation = continuation
        }
    }

    func release(with credential: OAuthCredential? = nil) {
        self.releaseContinuation?.resume(returning: credential)
        self.releaseContinuation = nil
    }
}

@MainActor
final class BlockingSecondAppleSignInProvider: AppleSignInProviding {
    private var firstCredential: OAuthCredential?
    private var secondAttemptStarted = false
    private var secondAttemptStartedContinuation: CheckedContinuation<Void, Never>?
    private var releaseContinuation: CheckedContinuation<OAuthCredential?, Never>?
    private(set) var callCount = 0

    init(firstCredential: OAuthCredential) {
        self.firstCredential = firstCredential
    }

    func signIn() async throws -> OAuthCredential? {
        self.callCount += 1
        if let firstCredential {
            self.firstCredential = nil
            return firstCredential
        }
        self.secondAttemptStarted = true
        self.secondAttemptStartedContinuation?.resume()
        self.secondAttemptStartedContinuation = nil
        return await withCheckedContinuation { continuation in
            self.releaseContinuation = continuation
        }
    }

    func waitUntilSecondAttemptStarted() async {
        if self.secondAttemptStarted {
            return
        }
        await withCheckedContinuation { continuation in
            self.secondAttemptStartedContinuation = continuation
        }
    }

    func releaseSecondAttempt(with credential: OAuthCredential?) {
        self.releaseContinuation?.resume(returning: credential)
        self.releaseContinuation = nil
    }
}

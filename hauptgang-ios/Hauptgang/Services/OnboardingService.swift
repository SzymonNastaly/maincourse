import Foundation
import os
import RevenueCat

/// Submits onboarding answers to the backend, keyed by the RevenueCat anonymous app user ID.
///
/// We pick RC's anon ID (`$RCAnonymousID:...`) as the device identifier because:
/// - it's already generated and persisted by the RevenueCat SDK before login
/// - on signup we call `Purchases.logIn(...)` and RC aliases the anon ID server-side,
///   while we link our own `OnboardingResponse` row on the Rails side via the same value.
///
/// The ID lives in UserDefaults and dies on uninstall — same as IDFV. Good enough for MVP.
actor OnboardingService {
    static let shared = OnboardingService()

    private let api: APIClientProtocol
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "Onboarding")

    /// UserDefaults key for persisting the anonymous device id used during onboarding,
    /// so AuthService can attach it to the signup/login request and the server can link
    /// the prior anonymous responses to the new user.
    static let deviceIdDefaultsKey = "hauptgang.onboarding.deviceId"

    /// UserDefaults key for the completion timestamp. Non-zero means the user has been
    /// through the onboarding flow (either answered or skipped) and we should jump
    /// straight to login on subsequent launches.
    static let completedAtDefaultsKey = "hauptgang.onboarding.completedAt"

    /// UserDefaults key for remembering that the user already finished the question
    /// portion of onboarding and should resume at the embedded auth screen.
    static let authStepReachedAtDefaultsKey = "hauptgang.onboarding.authStepReachedAt"

    init(api: APIClientProtocol = APIClient.shared) {
        self.api = api
    }

    /// Submit the full set of onboarding answers to the server. Best-effort — failures
    /// are logged but don't block the UI.
    func submit(deviceId: String, answers: [String: AnyCodable]) async {
        guard !deviceId.isEmpty else { return }

        let body = OnboardingRequest(deviceId: deviceId, answers: answers)
        do {
            let _: OnboardingResponse = try await self.api.request(
                endpoint: "onboarding_response",
                method: .post,
                body: body,
                authenticated: false
            )
            self.logger.info("Submitted onboarding response for device \(deviceId, privacy: .public)")
        } catch {
            self.logger.error("Failed to submit onboarding: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Read the current anonymous device id from RevenueCat. Must be called BEFORE
    /// `Purchases.logIn(...)` switches the ID to the user's id.
    @MainActor
    static func currentDeviceId() -> String {
        Purchases.shared.appUserID
    }

    /// Persist the device id used during onboarding so the auth flow can include it on
    /// the next signup/login request.
    static func storeDeviceIdForAuth(_ deviceId: String) {
        UserDefaults.standard.set(deviceId, forKey: self.deviceIdDefaultsKey)
    }

    /// Keep the id until authentication succeeds so a recoverable auth error can retry.
    static func deviceIdForAuth() -> String? {
        UserDefaults.standard.string(forKey: self.deviceIdDefaultsKey)
    }

    static func clearDeviceIdForAuth() {
        UserDefaults.standard.removeObject(forKey: self.deviceIdDefaultsKey)
    }

    static func markAuthStepReached() {
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: self.authStepReachedAtDefaultsKey)
    }

    static func clearAuthStepReached() {
        UserDefaults.standard.removeObject(forKey: self.authStepReachedAtDefaultsKey)
    }

    static func hasReachedAuthenticationStep() -> Bool {
        UserDefaults.standard.double(forKey: self.authStepReachedAtDefaultsKey) > 0
    }
}

// MARK: - Request / Response

private struct OnboardingRequest: Encodable {
    let deviceId: String
    let answers: [String: AnyCodable]
}

private struct OnboardingResponse: Decodable {
    let id: Int
    let deviceId: String
}

/// Lightweight type-erased Codable so we can encode mixed-type answers (Int for
/// household size, [String] for multi-selects) in the same JSON object.
struct AnyCodable: Codable {
    let value: any Sendable

    init(_ value: any Sendable) {
        self.value = value
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self.value {
        case let intValue as Int: try container.encode(intValue)
        case let stringValue as String: try container.encode(stringValue)
        case let boolValue as Bool: try container.encode(boolValue)
        case let doubleValue as Double: try container.encode(doubleValue)
        case let stringArray as [String]: try container.encode(stringArray)
        case let intArray as [Int]: try container.encode(intArray)
        default:
            throw EncodingError.invalidValue(
                self.value,
                EncodingError.Context(codingPath: encoder.codingPath, debugDescription: "Unsupported AnyCodable value")
            )
        }
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let intValue = try? container.decode(Int.self) {
            self.value = intValue
            return
        }
        if let boolValue = try? container.decode(Bool.self) {
            self.value = boolValue
            return
        }
        if let doubleValue = try? container.decode(Double.self) {
            self.value = doubleValue
            return
        }
        if let stringArray = try? container.decode([String].self) {
            self.value = stringArray
            return
        }
        if let intArray = try? container.decode([Int].self) {
            self.value = intArray
            return
        }
        if let stringValue = try? container.decode(String.self) {
            self.value = stringValue
            return
        }
        throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported AnyCodable value")
    }
}

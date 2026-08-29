import Foundation
@testable import Hauptgang

/// Records outgoing requests and returns canned responses. Encodes the body with the
/// same strategy as APIClient so tests can assert on the real wire format.
actor MockAPIClient: APIClientProtocol {
    struct Recorded: Sendable {
        let endpoint: String
        let method: HTTPMethod
        let bodyData: Data?

        /// Look up a top-level string in the recorded JSON body.
        func bodyString(_ key: String) -> String? {
            self.bodyObject()?[key] as? String
        }

        /// Look up a top-level array of objects in the recorded JSON body, e.g.
        /// `{"views": [{"recipe_id": 1, ...}]}` via `bodyArray("views")`.
        func bodyArray(_ key: String) -> [[String: Any]]? {
            self.bodyObject()?[key] as? [[String: Any]]
        }

        private func bodyObject() -> [String: Any]? {
            guard let bodyData else { return nil }
            return (try? JSONSerialization.jsonObject(with: bodyData)) as? [String: Any]
        }
    }

    private(set) var recorded: [Recorded] = []
    var responseData: Data = Data("{}".utf8)
    var errorToThrow: Error?

    func request<T: Decodable>(
        endpoint: String,
        method: HTTPMethod,
        body: Encodable?,
        queryItems _: [URLQueryItem]?,
        authenticated _: Bool
    ) async throws -> T {
        self.record(endpoint: endpoint, method: method, body: body)
        if let error = self.errorToThrow { throw error }
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(T.self, from: self.responseData)
    }

    func requestVoid(
        endpoint: String,
        method: HTTPMethod,
        body: Encodable?,
        queryItems _: [URLQueryItem]?,
        authenticated _: Bool
    ) async throws {
        self.record(endpoint: endpoint, method: method, body: body)
        if let error = self.errorToThrow { throw error }
    }

    func uploadMultipart<T: Decodable>(
        endpoint: String,
        method: HTTPMethod,
        file _: MultipartFile,
        authenticated _: Bool
    ) async throws -> T {
        self.record(endpoint: endpoint, method: method, body: nil)
        if let error = self.errorToThrow { throw error }
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(T.self, from: self.responseData)
    }

    func setResponse(_ json: String) {
        self.responseData = Data(json.utf8)
    }

    private func record(endpoint: String, method: HTTPMethod, body: Encodable?) {
        var bodyData: Data?
        if let body {
            let encoder = JSONEncoder()
            encoder.keyEncodingStrategy = .convertToSnakeCase
            encoder.dateEncodingStrategy = .iso8601
            bodyData = try? encoder.encode(body)
        }
        self.recorded.append(Recorded(endpoint: endpoint, method: method, bodyData: bodyData))
    }
}

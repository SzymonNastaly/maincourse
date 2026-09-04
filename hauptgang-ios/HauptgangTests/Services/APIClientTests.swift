@testable import Hauptgang
import XCTest

final class APIClientTests: XCTestCase {
    private var sut: APIClient!
    private var mockTokenProvider: MockTokenProvider!
    private var urlSession: URLSession!

    override func setUp() async throws {
        try await super.setUp()

        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        self.urlSession = URLSession(configuration: config)

        self.mockTokenProvider = MockTokenProvider()
        self.sut = APIClient(session: self.urlSession, tokenProvider: self.mockTokenProvider)
    }

    override func tearDown() async throws {
        MockURLProtocol.reset()
        self.sut = nil
        self.mockTokenProvider = nil
        self.urlSession = nil
        try await super.tearDown()
    }

    // MARK: - Auth Header Tests

    func testRequest_whenAuthenticatedWithToken_includesAuthHeader() async throws {
        await self.mockTokenProvider.setToken("test-token-123")

        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer test-token-123")
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, "{}".data(using: .utf8)!)
        }

        let _: EmptyDecodable = try await sut.request(
            endpoint: "test",
            method: .get,
            body: nil,
            authenticated: true
        )
    }

    func testRequest_whenAuthenticatedWithoutToken_noAuthHeader() async throws {
        await self.mockTokenProvider.setToken(nil)

        MockURLProtocol.requestHandler = { request in
            XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, "{}".data(using: .utf8)!)
        }

        let _: EmptyDecodable = try await sut.request(
            endpoint: "test",
            method: .get,
            body: nil,
            authenticated: true
        )
    }

    func testRequest_whenNotAuthenticated_noAuthHeader() async throws {
        await self.mockTokenProvider.setToken("should-not-be-used")

        MockURLProtocol.requestHandler = { request in
            XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, "{}".data(using: .utf8)!)
        }

        let _: EmptyDecodable = try await sut.request(
            endpoint: "test",
            method: .get,
            body: nil,
            authenticated: false
        )
    }

    // MARK: - Error Mapping Tests

    func testRequest_401WithInvalidError_throwsInvalidCredentials() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 401,
                httpVersion: nil,
                headerFields: nil
            )!
            let body = #"{"error": "Invalid email or password"}"#.data(using: .utf8)!
            return (response, body)
        }

        do {
            let _: EmptyDecodable = try await sut.request(
                endpoint: "test",
                method: .get,
                body: nil,
                authenticated: false
            )
            XCTFail("Expected invalidCredentials error")
        } catch let error as APIError {
            if case .invalidCredentials = error {
                // Expected
            } else {
                XCTFail("Expected invalidCredentials, got \(error)")
            }
        }
    }

    func testRequest_401WithoutInvalidError_throwsUnauthorized() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 401,
                httpVersion: nil,
                headerFields: nil
            )!
            let body = #"{"error": "Token expired"}"#.data(using: .utf8)!
            return (response, body)
        }

        do {
            let _: EmptyDecodable = try await sut.request(
                endpoint: "test",
                method: .get,
                body: nil,
                authenticated: false
            )
            XCTFail("Expected unauthorized error")
        } catch let error as APIError {
            if case .unauthorized = error {
                // Expected
            } else {
                XCTFail("Expected unauthorized, got \(error)")
            }
        }
    }

    func testRequest_401FromOAuth_throwsOAuthAuthenticationFailed() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 401,
                httpVersion: nil,
                headerFields: nil
            )!
            let body = #"{"error": "Provider authentication failed", "error_code": "oauth_failed"}"#.data(using: .utf8)!
            return (response, body)
        }

        do {
            let _: EmptyDecodable = try await sut.request(
                endpoint: "oauth_session",
                method: .post,
                body: nil,
                authenticated: false
            )
            XCTFail("Expected oauthAuthenticationFailed error")
        } catch let error as APIError {
            if case .oauthAuthenticationFailed = error {
                // Expected
            } else {
                XCTFail("Expected oauthAuthenticationFailed, got \(error)")
            }
        }
    }

    func testRequest_409FromOAuth_throwsAccountLinkRequired() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 409,
                httpVersion: nil,
                headerFields: nil
            )!
            let body = #"{"error": "Sign in with your password", "error_code": "account_link_required"}"#
                .data(using: .utf8)!
            return (response, body)
        }

        do {
            let _: EmptyDecodable = try await sut.request(
                endpoint: "oauth_session",
                method: .post,
                body: nil,
                authenticated: false
            )
            XCTFail("Expected accountLinkRequired error")
        } catch let error as APIError {
            if case .accountLinkRequired = error {
                // Expected
            } else {
                XCTFail("Expected accountLinkRequired, got \(error)")
            }
        }
    }

    func testRequest_503FromOAuth_throwsOAuthUnavailable() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 503,
                httpVersion: nil,
                headerFields: nil
            )!
            let body = #"{"error": "Provider unavailable", "error_code": "oauth_unavailable"}"#
                .data(using: .utf8)!
            return (response, body)
        }

        do {
            let _: EmptyDecodable = try await sut.request(
                endpoint: "oauth_session",
                method: .post,
                body: nil,
                authenticated: false
            )
            XCTFail("Expected oauthUnavailable error")
        } catch let error as APIError {
            if case .oauthUnavailable = error {
                // Expected
            } else {
                XCTFail("Expected oauthUnavailable, got \(error)")
            }
        }
    }

    func testRequest_422_throwsUnprocessableEntity() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 422,
                httpVersion: nil,
                headerFields: nil
            )!
            let body = #"{"error": "Recipe URL is invalid"}"#.data(using: .utf8)!
            return (response, body)
        }

        do {
            let _: EmptyDecodable = try await sut.request(
                endpoint: "test",
                method: .get,
                body: nil,
                authenticated: false
            )
            XCTFail("Expected unprocessableEntity error")
        } catch let error as APIError {
            if case let .unprocessableEntity(message) = error {
                XCTAssertEqual(message, "Recipe URL is invalid")
            } else {
                XCTFail("Expected unprocessableEntity, got \(error)")
            }
        }
    }

    func testRequest_500_throwsServerError() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 503,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, Data())
        }

        do {
            let _: EmptyDecodable = try await sut.request(
                endpoint: "test",
                method: .get,
                body: nil,
                authenticated: false
            )
            XCTFail("Expected serverError")
        } catch let error as APIError {
            if case let .serverError(statusCode) = error {
                XCTAssertEqual(statusCode, 503)
            } else {
                XCTFail("Expected serverError, got \(error)")
            }
        }
    }

    func testRequest_404_throwsNotFound() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 404,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, Data())
        }

        do {
            let _: EmptyDecodable = try await sut.request(
                endpoint: "test",
                method: .get,
                body: nil,
                authenticated: false
            )
            XCTFail("Expected notFound error")
        } catch let error as APIError {
            if case .notFound = error {
                // Expected
            } else {
                XCTFail("Expected notFound, got \(error)")
            }
        }
    }

    // MARK: - Date Decoding Tests

    func testRequest_decodesDateWithFractionalSeconds() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            let body = #"{"created_at": "2024-01-15T10:30:00.123Z"}"#.data(using: .utf8)!
            return (response, body)
        }

        let result: DateTestModel = try await sut.request(
            endpoint: "test",
            method: .get,
            body: nil,
            authenticated: false
        )

        XCTAssertNotNil(result.createdAt)
    }

    func testRequest_decodesDateWithoutFractionalSeconds() async throws {
        MockURLProtocol.requestHandler = { request in
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            let body = #"{"created_at": "2024-01-15T10:30:00Z"}"#.data(using: .utf8)!
            return (response, body)
        }

        let result: DateTestModel = try await sut.request(
            endpoint: "test",
            method: .get,
            body: nil,
            authenticated: false
        )

        XCTAssertNotNil(result.createdAt)
    }

    func testRequest_withQueryItems_buildsURLWithQueryParameters() async throws {
        MockURLProtocol.requestHandler = { request in
            XCTAssertEqual(request.url?.path, "/api/v1/cookbooks/12/meal_plans")

            let components = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)
            let items = components?.queryItems ?? []
            XCTAssertTrue(items.contains(URLQueryItem(name: "from", value: "2026-03-18")))
            XCTAssertTrue(items.contains(URLQueryItem(name: "to", value: "2026-03-19")))

            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, "{}".data(using: .utf8)!)
        }

        let _: EmptyDecodable = try await sut.request(
            endpoint: "cookbooks/12/meal_plans",
            method: .get,
            body: nil,
            queryItems: [
                URLQueryItem(name: "from", value: "2026-03-18"),
                URLQueryItem(name: "to", value: "2026-03-19")
            ],
            authenticated: false
        )
    }
}

// MARK: - Test Helpers

private struct EmptyDecodable: Decodable {}

private struct DateTestModel: Decodable {
    let createdAt: Date
}

// MARK: - MockURLProtocol

final class MockURLProtocol: URLProtocol {
    nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?
    nonisolated(unsafe) static var uploadHandler: ((URLRequest, Data) throws -> (HTTPURLResponse, Data))?

    static func reset() {
        self.requestHandler = nil
        self.uploadHandler = nil
    }

    override class func canInit(with _: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        do {
            let (response, data): (HTTPURLResponse, Data)

            if let uploadHandler = MockURLProtocol.uploadHandler,
               let bodyData = request.httpBody ?? request.httpBodyStream?.readAllData() {
                (response, data) = try uploadHandler(request, bodyData)
            } else if let handler = MockURLProtocol.requestHandler {
                (response, data) = try handler(request)
            } else {
                fatalError("No handler set for MockURLProtocol")
            }

            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

private extension InputStream {
    func readAllData() -> Data {
        open()
        defer { close() }

        var data = Data()
        let bufferSize = 1024
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }

        while hasBytesAvailable {
            let read = self.read(buffer, maxLength: bufferSize)
            if read > 0 {
                data.append(buffer, count: read)
            }
        }
        return data
    }
}

import Foundation

/// Generic HTTP client for API communication
actor APIClient: APIClientProtocol {
    static let shared = APIClient()

    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder
    private let tokenProvider: any TokenProviding

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)
        self.tokenProvider = KeychainService.shared

        self.decoder = JSONDecoder()
        self.decoder.keyDecodingStrategy = .convertFromSnakeCase
        // Use ISO8601FormatStyle for Sendable-safe date parsing
        self.decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let dateString = try container.decode(String.self)

            // Try with fractional seconds first (Rails default)
            if let date = try? Date(dateString, strategy: Date.ISO8601FormatStyle(includingFractionalSeconds: true)) {
                return date
            }
            // Fallback for dates without fractional seconds
            if let date = try? Date(dateString, strategy: Date.ISO8601FormatStyle(includingFractionalSeconds: false)) {
                return date
            }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Cannot decode date: \(dateString)"
            )
        }

        self.encoder = JSONEncoder()
        self.encoder.keyEncodingStrategy = .convertToSnakeCase
        self.encoder.dateEncodingStrategy = .iso8601
    }

    init(session: URLSession, tokenProvider: any TokenProviding) {
        self.session = session
        self.tokenProvider = tokenProvider

        self.decoder = JSONDecoder()
        self.decoder.keyDecodingStrategy = .convertFromSnakeCase
        self.decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let dateString = try container.decode(String.self)

            if let date = try? Date(dateString, strategy: Date.ISO8601FormatStyle(includingFractionalSeconds: true)) {
                return date
            }
            if let date = try? Date(dateString, strategy: Date.ISO8601FormatStyle(includingFractionalSeconds: false)) {
                return date
            }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Cannot decode date: \(dateString)"
            )
        }

        self.encoder = JSONEncoder()
        self.encoder.keyEncodingStrategy = .convertToSnakeCase
        self.encoder.dateEncodingStrategy = .iso8601
    }

    // MARK: - Public Methods

    func request<T: Decodable>(
        endpoint: String,
        method: HTTPMethod = .get,
        body: Encodable? = nil,
        queryItems: [URLQueryItem]? = nil,
        authenticated: Bool = false
    ) async throws -> T {
        let url = try self.buildURL(endpoint: endpoint, queryItems: queryItems)
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        // Add auth header and cookbook scope if needed
        if authenticated, let token = await tokenProvider.getToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            if let cookbookId = await CookbookContext.shared.getActiveCookbookId() {
                request.setValue(String(cookbookId), forHTTPHeaderField: "X-Cookbook-Id")
            }
        }

        // Encode body if present
        if let body {
            request.httpBody = try self.encoder.encode(body)
        }

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.invalidResponse
            }

            try self.validateResponse(httpResponse, data: data, endpoint: endpoint)

            do {
                return try self.decoder.decode(T.self, from: data)
            } catch {
                throw APIError.decodingError(error)
            }
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.networkError(error)
        }
    }

    func requestVoid(
        endpoint: String,
        method: HTTPMethod = .get,
        body: Encodable? = nil,
        queryItems: [URLQueryItem]? = nil,
        authenticated: Bool = false
    ) async throws {
        let url = try self.buildURL(endpoint: endpoint, queryItems: queryItems)
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if authenticated, let token = await tokenProvider.getToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            if let cookbookId = await CookbookContext.shared.getActiveCookbookId() {
                request.setValue(String(cookbookId), forHTTPHeaderField: "X-Cookbook-Id")
            }
        }

        if let body {
            request.httpBody = try self.encoder.encode(body)
        }

        do {
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.invalidResponse
            }

            try self.validateResponse(httpResponse, data: data, endpoint: endpoint)
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.networkError(error)
        }
    }

    func uploadMultipart<T: Decodable>(
        endpoint: String,
        method: HTTPMethod = .post,
        file: MultipartFile,
        authenticated: Bool = false
    ) async throws -> T {
        let url = Constants.API.baseURL.appendingPathComponent(endpoint)
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        if authenticated, let token = await tokenProvider.getToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            if let cookbookId = await CookbookContext.shared.getActiveCookbookId() {
                request.setValue(String(cookbookId), forHTTPHeaderField: "X-Cookbook-Id")
            }
        }

        var body = Data()
        body.append(Data("--\(boundary)\r\n".utf8))
        body.append(
            Data(
                "Content-Disposition: form-data; name=\"\(file.paramName)\"; filename=\"\(file.fileName)\"\r\n".utf8
            )
        )
        body.append(Data("Content-Type: \(file.mimeType)\r\n\r\n".utf8))
        body.append(file.data)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))

        do {
            let (data, response) = try await session.upload(for: request, from: body)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.invalidResponse
            }

            try self.validateResponse(httpResponse, data: data, endpoint: endpoint)

            do {
                return try self.decoder.decode(T.self, from: data)
            } catch {
                throw APIError.decodingError(error)
            }
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.networkError(error)
        }
    }

    // MARK: - Private

    private func buildURL(endpoint: String, queryItems: [URLQueryItem]? = nil) throws -> URL {
        let url = Constants.API.baseURL.appendingPathComponent(endpoint)
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            throw APIError.invalidURL
        }
        components.queryItems = queryItems?.isEmpty == false ? queryItems : nil
        guard let resolvedURL = components.url else {
            throw APIError.invalidURL
        }
        return resolvedURL
    }

    private func validateResponse(_ response: HTTPURLResponse, data: Data, endpoint: String) throws {
        guard !(200 ... 299).contains(response.statusCode) else { return }
        let json = self.parseJSONObject(data)
        if let error = self.codedResponseError(status: response.statusCode, json: json, data: data) {
            throw error
        }
        throw self.fallbackError(status: response.statusCode, json: json, endpoint: endpoint)
    }

    private func codedResponseError(status: Int, json: [String: Any], data: Data) -> APIError? {
        if status == 403, json["error_code"] as? String == "import_limit_reached" {
            return .importLimitReached(self.problem(data, fallbackCode: "import_limit_reached"))
        }
        if [400, 422, 429].contains(status) {
            return self.problemError(
                data,
                status: status,
                fallbackCode: status == 429 ? "rate_limited" : "invalid_request"
            )
        }
        let controlFlowCodes: Set<ApiErrorCode> = [
            .forbidden, .not_found, .recipe_save_conflict, .account_link_required,
            .apple_account_creation_confirmation_required
        ]
        if [403, 404, 409, 413].contains(status),
           let raw = json["error_code"] as? String, let code = ApiErrorCode(rawValue: raw),
           !controlFlowCodes.contains(code) {
            return self.problemError(data, status: status)
        }
        return nil
    }

    private func fallbackError(status: Int, json: [String: Any], endpoint: String) -> APIError {
        switch status {
        case 401:
            self.unauthorizedError(from: json, endpoint: endpoint)
        case 403:
            .forbidden
        case 404:
            .notFound
        case 410:
            .resourceGone
        case 409:
            self.conflictError(from: json)
        case 413:
            .payloadTooLarge(nil)
        case 415:
            .unsupportedMediaType
        case 500 ... 599:
            self.serverError(statusCode: status, json: json)
        default:
            .unknown
        }
    }

    private func parseJSONObject(_ data: Data) -> [String: Any] {
        (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    private func unauthorizedError(from json: [String: Any], endpoint: String) -> APIError {
        switch json["error_code"] as? String {
        case "oauth_failed": return .oauthAuthenticationFailed
        case "invalid_credentials": return .invalidCredentials
        case nil:
            // Older servers lack codes. Endpoint semantics are stable; prose is not.
            if endpoint == "session" {
                return .invalidCredentials
            }
            if endpoint == "oauth_session" {
                return .oauthAuthenticationFailed
            }
            return .unauthorized
        default: return .unauthorized
        }
    }

    private func conflictError(from json: [String: Any]) -> APIError {
        switch json["error_code"] as? String {
        case "account_link_required":
            .accountLinkRequired
        case "apple_account_creation_confirmation_required":
            .appleAccountCreationConfirmationRequired
        case "recipe_save_conflict":
            .requestConflict
        default:
            .unknown
        }
    }

    private func serverError(statusCode: Int, json: [String: Any]) -> APIError {
        json["error_code"] as? String == "oauth_unavailable"
            ? .oauthUnavailable
            : .serverError(statusCode: statusCode)
    }

    private func problemError(_ data: Data, status: Int, fallbackCode: String = "invalid_request") -> APIError {
        let problem = self.problem(data, fallbackCode: fallbackCode)
        switch status {
        case 413: return .payloadTooLarge(problem)
        case 422: return .unprocessableEntity(problem)
        default: return .remote(problem)
        }
    }

    private func problem(_ data: Data, fallbackCode: String) -> APIProblem {
        (try? self.decoder.decode(APIProblem.self, from: data)) ?? APIProblem(
            errorCode: fallbackCode, errorParams: nil, errorDetails: nil
        )
    }
}

// MARK: - HTTP Method

enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case patch = "PATCH"
    case delete = "DELETE"
}

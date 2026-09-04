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

            try self.validateResponse(httpResponse, data: data)

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

            try self.validateResponse(httpResponse, data: data)
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

            try self.validateResponse(httpResponse, data: data)

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

    private func validateResponse(_ response: HTTPURLResponse, data: Data) throws {
        let json = self.parseJSONObject(data)

        switch response.statusCode {
        case 200 ... 299:
            return
        case 401:
            throw self.unauthorizedError(from: json)
        case 403:
            throw self.forbiddenError(from: json)
        case 404:
            throw APIError.notFound
        case 409:
            throw self.conflictError(from: json)
        case 413:
            throw APIError.payloadTooLarge
        case 415:
            throw APIError.unsupportedMediaType
        case 422:
            throw APIError.unprocessableEntity(self.unprocessableMessage(from: json))
        case 500 ... 599:
            throw self.serverError(statusCode: response.statusCode, json: json)
        default:
            throw APIError.unknown
        }
    }

    private func parseJSONObject(_ data: Data) -> [String: Any] {
        (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    private func unauthorizedError(from json: [String: Any]) -> APIError {
        guard let error = json["error"] as? String else {
            return .unauthorized
        }
        if json["error_code"] as? String == "oauth_failed" ||
            error == "Could not authenticate with that provider" {
            return .oauthAuthenticationFailed
        }
        return error.lowercased().contains("invalid") ? .invalidCredentials : .unauthorized
    }

    private func forbiddenError(from json: [String: Any]) -> APIError {
        guard let errorCode = json["error_code"] as? String else {
            return .forbidden
        }
        return errorCode == "import_limit_reached" ? .importLimitReached : .forbidden
    }

    private func conflictError(from json: [String: Any]) -> APIError {
        guard json["error_code"] as? String == "account_link_required" else {
            return .unknown
        }
        return .accountLinkRequired
    }

    private func serverError(statusCode: Int, json: [String: Any]) -> APIError {
        json["error_code"] as? String == "oauth_unavailable"
            ? .oauthUnavailable
            : .serverError(statusCode: statusCode)
    }

    private func unprocessableMessage(from json: [String: Any]) -> String? {
        if let error = json["error"] as? String {
            return error
        }

        if let errors = json["errors"] as? [String] {
            return errors.joined(separator: ". ")
        }

        return nil
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

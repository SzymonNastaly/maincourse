import Foundation

struct RecipeSaveResult: Decodable, Equatable, Sendable {
    let recipeId: Int
    let cookbookId: Int
}

struct RecipeSaveSource: Codable, Equatable, Sendable {
    let type: String
    let key: String
}

protocol RecipeSaving: Sendable {
    func save(source: RecipeSaveSource, toCookbookId: Int, requestId: UUID) async throws -> RecipeSaveResult
}

struct RecipeSaveService: RecipeSaving {
    let api: any APIClientProtocol

    init(api: any APIClientProtocol = APIClient.shared) {
        self.api = api
    }

    func save(source: RecipeSaveSource, toCookbookId: Int, requestId: UUID) async throws -> RecipeSaveResult {
        struct Body: Encodable {
            let source: RecipeSaveSource
            let requestId: UUID
        }
        return try await self.api.request(
            endpoint: "cookbooks/\(toCookbookId)/recipe_saves", method: .post,
            body: Body(source: source, requestId: requestId), authenticated: true
        )
    }
}

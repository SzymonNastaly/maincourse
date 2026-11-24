import Foundation

/// Service for importing recipes from URLs
actor RecipeImportService {
    static let shared = RecipeImportService()

    private let apiClient: APIClientProtocol
    private let instagramPageFetcher: InstagramPageFetcher

    init(
        apiClient: APIClientProtocol = APIClient.shared,
        instagramPageFetcher: InstagramPageFetcher = InstagramPageFetcher()
    ) {
        self.apiClient = apiClient
        self.instagramPageFetcher = instagramPageFetcher
    }

    /// Import a recipe from a URL (fire-and-forget)
    /// The server creates a pending recipe and processes it asynchronously.
    /// For Instagram posts, the caption is read on the device first so the server can skip its paid scraper.
    func importRecipe(from url: URL) async throws -> ImportRecipeResponse {
        struct ImportRequest: Encodable {
            let url: String
        }

        if let pageContent = await self.instagramPageFetcher.pageContent(for: url) {
            return try await self.importRecipe(from: url, pageContent: pageContent)
        }

        return try await self.apiClient.request(
            endpoint: "recipes/import",
            method: .post,
            body: ImportRequest(url: url.absoluteString),
            authenticated: true
        )
    }

    /// Import a recipe from a URL with pre-extracted page content (JS preprocessing)
    func importRecipe(from url: URL, pageContent: PageContent) async throws -> ImportRecipeResponse {
        struct ImportWithContentRequest: Encodable {
            let url: String
            let jsonLd: [String]
            let metaTags: [String: String]
            let coverImageCandidates: [String]
            let html: String
        }

        return try await self.apiClient.request(
            endpoint: "recipes/import_with_content",
            method: .post,
            body: ImportWithContentRequest(
                url: url.absoluteString,
                jsonLd: pageContent.jsonLd,
                metaTags: pageContent.metaTags,
                coverImageCandidates: pageContent.coverImageCandidates,
                html: pageContent.html
            ),
            authenticated: true
        )
    }

    /// Import a recipe from pasted text
    func importRecipe(fromText text: String) async throws -> ImportRecipeResponse {
        struct TextImportRequest: Encodable {
            let text: String
        }

        return try await self.apiClient.request(
            endpoint: "recipes/extract_from_text",
            method: .post,
            body: TextImportRequest(text: text),
            authenticated: true
        )
    }

    /// Import a recipe from image data
    func importRecipe(from imageData: Data, mimeType: String = "image/jpeg") async throws -> ImportRecipeResponse {
        try await self.apiClient.uploadMultipart(
            endpoint: "recipes/extract_from_image",
            file: MultipartFile(
                data: imageData,
                fileName: "recipe.\(mimeType == "image/png" ? "png" : "jpg")",
                mimeType: mimeType,
                paramName: "image"
            ),
            authenticated: true
        )
    }
}

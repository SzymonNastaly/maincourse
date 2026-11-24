@testable import Hauptgang
import XCTest

final class RecipeImportServiceTests: XCTestCase {
    private actor LoadedURLs {
        private(set) var urls: [URL] = []

        func append(_ url: URL) {
            self.urls.append(url)
        }
    }

    private let apiClient = MockAPIClient()
    private let loadedURLs = LoadedURLs()

    override func setUp() async throws {
        await self.apiClient.setResponse(#"{"id": 1, "import_status": "pending"}"#)
    }

    private func makeService(html: String?) -> RecipeImportService {
        let loadedURLs = self.loadedURLs
        let fetcher = InstagramPageFetcher { url in
            await loadedURLs.append(url)
            guard let html else { throw URLError(.notConnectedToInternet) }
            return html
        }
        return RecipeImportService(apiClient: self.apiClient, instagramPageFetcher: fetcher)
    }

    func testInstagramURL_sendsDecodedCaptionToImportWithContent() async throws {
        let url = try XCTUnwrap(URL(string: "https://www.instagram.com/reel/DRUf_pBiPdh/"))
        let service = self.makeService(html: InstagramPageFetcherTests.reelHTML)

        _ = try await service.importRecipe(from: url)

        let recorded = await self.apiClient.recorded
        let request = try XCTUnwrap(recorded.only)
        let metaTags = try XCTUnwrap(request.bodyStringMap("meta_tags"))
        let description = try XCTUnwrap(metaTags["og:description"])
        XCTAssertEqual(request.endpoint, "recipes/import_with_content")
        XCTAssertEqual(request.bodyString("url"), "https://www.instagram.com/reel/DRUf_pBiPdh/")
        XCTAssertEqual(request.bodyString("html"), "")
        XCTAssertEqual(metaTags["og:image"], "https://scontent.cdninstagram.com/v/cover.jpg?stp=dst-jpg&_nc_cat=107")
        XCTAssertTrue(description.hasPrefix("26K likes, 272 comments - fabi on November 21, 2025: \"Vegane Ente"))
        XCTAssertTrue(description.hasSuffix("Zutaten: 200g Seitan & 1 Zwiebel, spät <3 'lecker' 'ok' € &unknown;\""))
    }

    func testInstagramURL_fallsBackToURLOnlyImportWhenPageFetchFails() async throws {
        let url = try XCTUnwrap(URL(string: "https://www.instagram.com/p/DRUf_pBiPdh/"))
        let service = self.makeService(html: nil)

        _ = try await service.importRecipe(from: url)

        let recorded = await self.apiClient.recorded
        let request = try XCTUnwrap(recorded.only)
        let loaded = await self.loadedURLs.urls
        XCTAssertEqual(loaded, [url])
        XCTAssertEqual(request.endpoint, "recipes/import")
        XCTAssertEqual(request.bodyString("url"), "https://www.instagram.com/p/DRUf_pBiPdh/")
    }

    func testNonInstagramURL_neverFetchesThePage() async throws {
        let url = try XCTUnwrap(URL(string: "https://example.com/recipe"))
        let service = self.makeService(html: InstagramPageFetcherTests.reelHTML)

        _ = try await service.importRecipe(from: url)

        let recorded = await self.apiClient.recorded
        let request = try XCTUnwrap(recorded.only)
        let loaded = await self.loadedURLs.urls
        XCTAssertEqual(loaded, [])
        XCTAssertEqual(request.endpoint, "recipes/import")
        XCTAssertEqual(request.bodyString("url"), "https://example.com/recipe")
    }
}

private extension Array {
    var only: Element? {
        self.count == 1 ? self.first : nil
    }
}

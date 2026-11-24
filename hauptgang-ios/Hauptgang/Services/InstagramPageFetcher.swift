import Foundation

/// Reads an Instagram post's caption from its public page on the device. Instagram serves the
/// full caption in `og:description` to logged-out visitors, so sending it lets the server skip
/// its paid scraper.
struct InstagramPageFetcher: Sendable {
    typealias HTMLLoader = @Sendable (URL) async throws -> String

    private static let wantedMetaTags: Set<String> = ["og:description", "og:image", "og:title"]
    private static let namedEntities: [String: String] = [
        "quot": "\"", "amp": "&", "lt": "<", "gt": ">", "apos": "'"
    ]
    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 10
        return URLSession(configuration: configuration)
    }()

    private let loadHTML: HTMLLoader

    init(loadHTML: @escaping HTMLLoader = Self.loadFromNetwork) {
        self.loadHTML = loadHTML
    }

    /// Mirrors `RecipeImporters::InstagramReelExtractor.supports_url?` on the server.
    static func isInstagramPost(_ url: URL) -> Bool {
        guard let host = url.host()?.lowercased(),
              host == "instagram.com" || host.hasSuffix(".instagram.com") else {
            return false
        }
        let path = url.path(percentEncoded: true)
        return ["/reel/", "/reels/", "/p/"].contains { path.contains($0) }
    }

    /// Returns the page's meta tags when `url` is an Instagram post whose page carries a caption,
    /// and nil otherwise, so the caller can fall back to a URL-only import.
    func pageContent(for url: URL) async -> PageContent? {
        guard Self.isInstagramPost(url), let html = try? await self.loadHTML(url) else {
            return nil
        }
        let metaTags = Self.metaTags(fromHTML: html)
        guard metaTags["og:description"]?.isEmpty == false else {
            return nil
        }
        return PageContent(url: url, jsonLd: [], metaTags: metaTags, coverImageCandidates: [], html: "")
    }

    static func metaTags(fromHTML html: String) -> [String: String] {
        var tags: [String: String] = [:]
        for tag in html.matches(of: #/<meta\b(?:[^>"']|"[^"]*"|'[^']*')*>/#.ignoresCase()) {
            var attributes: [String: Substring] = [:]
            for attribute in tag.output.matches(of: #/([a-zA-Z:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/#) {
                attributes[attribute.output.1.lowercased()] = attribute.output.2 ?? attribute.output.3
            }
            guard let key = attributes["property"].map({ String($0) }),
                  self.wantedMetaTags.contains(key),
                  tags[key] == nil,
                  let content = attributes["content"] else {
                continue
            }
            tags[key] = self.decodeEntities(content)
        }
        return tags
    }

    static func decodeEntities(_ text: some StringProtocol) -> String {
        String(text).replacing(#/&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]+);/#) { match in
            let body = match.output.1
            let decoded: String? = if body.hasPrefix("#x") || body.hasPrefix("#X") {
                UInt32(body.dropFirst(2), radix: 16).flatMap(Unicode.Scalar.init).map { String($0) }
            } else if body.hasPrefix("#") {
                UInt32(body.dropFirst()).flatMap(Unicode.Scalar.init).map { String($0) }
            } else {
                self.namedEntities[String(body)]
            }
            return decoded ?? String(match.output.0)
        }
    }

    @Sendable
    static func loadFromNetwork(_ url: URL) async throws -> String {
        let (data, response) = try await self.session.data(from: url)
        guard (response as? HTTPURLResponse)?.statusCode == 200,
              let html = String(bytes: data, encoding: .utf8) else {
            throw URLError(.badServerResponse)
        }
        return html
    }
}

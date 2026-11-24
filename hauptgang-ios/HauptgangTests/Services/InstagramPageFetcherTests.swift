@testable import Hauptgang
import XCTest

final class InstagramPageFetcherTests: XCTestCase {
    static let reelHTML = """
    <html><head>
    <meta property="og:site_name" content="Instagram" />
    <meta property="og:title" content="Fabi on Instagram: &quot;Vegane Ente &#x1f385;&#x1f3fb;&quot;" />
    <meta content="https://scontent.cdninstagram.com/v/cover.jpg?stp=dst-jpg&amp;_nc_cat=107" property="og:image" />
    <meta property="og:description" content="26K likes, 272 comments - fabi on November 21, 2025: \
    &quot;Vegane Ente mit Bratensauce &#x1f385; Rezept &#x2935;&#xfe0f;

    Zutaten: 200g Seitan &amp; 1 Zwiebel, sp&#xe4;t &lt;3 &#39;lecker&#39; &apos;ok&apos; &#8364; &unknown;&quot;" />
    </head><body><script>var x = "<meta property=\\"og:description\\" content=\\"later\\">";</script></body></html>
    """

    func testMetaTags_decodesEntitiesAndToleratesAttributeOrder() {
        let tags = InstagramPageFetcher.metaTags(fromHTML: Self.reelHTML)

        XCTAssertEqual(tags, [
            "og:title": "Fabi on Instagram: \"Vegane Ente 🎅🏻\"",
            "og:image": "https://scontent.cdninstagram.com/v/cover.jpg?stp=dst-jpg&_nc_cat=107",
            "og:description": """
            26K likes, 272 comments - fabi on November 21, 2025: "Vegane Ente mit Bratensauce 🎅 Rezept ⤵️

            Zutaten: 200g Seitan & 1 Zwiebel, spät <3 'lecker' 'ok' € &unknown;"
            """
        ])
    }

    func testIsInstagramPost_matchesReelsAndPostsOnly() throws {
        let matching = [
            "https://www.instagram.com/reel/DRUf_pBiPdh/",
            "https://www.instagram.com/reels/DRUf_pBiPdh/",
            "https://instagram.com/p/DRUf_pBiPdh/?igsh=abc",
            "https://m.Instagram.com/p/DRUf_pBiPdh"
        ]
        let rejected = [
            "https://www.instagram.com/fabiwndrlnd/",
            "https://example.com/reel/DRUf_pBiPdh/",
            "https://notinstagram.com/p/DRUf_pBiPdh/"
        ]

        for string in matching {
            XCTAssertTrue(try InstagramPageFetcher.isInstagramPost(XCTUnwrap(URL(string: string))), string)
        }
        for string in rejected {
            XCTAssertFalse(try InstagramPageFetcher.isInstagramPost(XCTUnwrap(URL(string: string))), string)
        }
    }

    func testPageContent_isNilWhenPageHasNoDescription() async throws {
        let url = try XCTUnwrap(URL(string: "https://www.instagram.com/reel/DRUf_pBiPdh/"))
        let loginWall = InstagramPageFetcher { _ in
            "<html><head><meta property=\"og:title\" content=\"Instagram\"></head></html>"
        }
        let failing = InstagramPageFetcher { _ in throw URLError(.timedOut) }

        let loginWallContent = await loginWall.pageContent(for: url)
        let failingContent = await failing.pageContent(for: url)

        XCTAssertNil(loginWallContent)
        XCTAssertNil(failingContent)
    }
}

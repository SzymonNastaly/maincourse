import Foundation

struct PageContent {
    let url: URL
    let jsonLd: [String]
    let metaTags: [String: String]
    let coverImageCandidates: [String]
    let html: String

    init(url: URL, jsonLd: [String], metaTags: [String: String], coverImageCandidates: [String], html: String) {
        self.url = url
        self.jsonLd = jsonLd
        self.metaTags = metaTags
        self.coverImageCandidates = coverImageCandidates
        self.html = html
    }

    init?(from javaScriptValues: [String: Any]) {
        guard let urlString = javaScriptValues["url"] as? String,
              let url = URL(string: urlString) else {
            return nil
        }

        self.url = url
        self.jsonLd = (javaScriptValues["jsonLd"] as? [String]) ?? []
        self.metaTags = (javaScriptValues["metaTags"] as? [String: String]) ?? [:]
        self.coverImageCandidates = (javaScriptValues["coverImageCandidates"] as? [String]) ?? []
        self.html = (javaScriptValues["html"] as? String) ?? ""
    }
}

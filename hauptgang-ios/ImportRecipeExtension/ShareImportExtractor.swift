import Foundation
import os
import UniformTypeIdentifiers

private let logger = Logger(subsystem: "app.hauptgang.ios.share-extension", category: "ShareImportExtractor")

/// Apple documents `NSItemProvider` as safe to use from any thread, but the SDK does not
/// declare it `Sendable`. Without this, handing the share sheet's attachments — created on
/// the main actor — to the nonisolated extractor is a data-race warning, and an error under
/// the Swift 6 language mode.
extension NSItemProvider: @retroactive @unchecked Sendable {}

protocol ShareItemProviding {
    var registeredTypeIdentifiers: [String] { get }

    func hasItemConformingToTypeIdentifier(_ typeIdentifier: String) -> Bool
    func loadItem(
        forTypeIdentifier typeIdentifier: String,
        // swiftlint:disable:next discouraged_optional_collection
        options: [AnyHashable: Any]?
    ) async throws -> NSSecureCoding?
    func loadFileRepresentation(forTypeIdentifier typeIdentifier: String) async throws -> URL?
}

extension NSItemProvider: ShareItemProviding {
    func loadItem(
        forTypeIdentifier typeIdentifier: String,
        // swiftlint:disable:next discouraged_optional_collection
        options: [AnyHashable: Any]?
    ) async throws -> NSSecureCoding? {
        try await withCheckedThrowingContinuation { continuation in
            self.loadItem(forTypeIdentifier: typeIdentifier, options: options) { item, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                nonisolated(unsafe) let value = item
                continuation.resume(returning: value)
            }
        }
    }

    func loadFileRepresentation(forTypeIdentifier typeIdentifier: String) async throws -> URL? {
        try await withCheckedThrowingContinuation { continuation in
            self.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { url, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                continuation.resume(returning: url)
            }
        }
    }
}

enum ShareImportExtractor {
    /// Result of attempting to extract JS preprocessing data from a web page share.
    /// When JS preprocessing returns data, we get a full PageContent.
    /// When it fails, we may still get the URL from the property list provider.
    enum PageContentResult {
        case success(PageContent)
        case urlOnly(URL)
        case none
    }

    static func extractPageContent(
        from attachments: [some ShareItemProviding]
    ) async -> PageContent? {
        switch await self.extractWebPageData(from: attachments) {
        case let .success(pageContent):
            pageContent
        case .urlOnly, .none:
            nil
        }
    }

    static func extractWebPageData(from attachments: [some ShareItemProviding]) async -> PageContentResult {
        let propertyListType = UTType.propertyList.identifier
        let urlType = UTType.url.identifier

        for provider in attachments where provider.hasItemConformingToTypeIdentifier(propertyListType) {
            if let pageContent = await loadPageContent(from: provider, typeIdentifier: propertyListType) {
                return .success(pageContent)
            }
            if let url = await loadURLFromPropertyList(from: provider, typeIdentifier: propertyListType) {
                logger.info("Extracted URL from property list payload as fallback")
                return .urlOnly(url)
            }
            // JS preprocessing failed — try to extract URL from the same provider
            if provider.hasItemConformingToTypeIdentifier(urlType),
               let url = await loadURL(from: provider, typeIdentifier: urlType) {
                logger.info("Extracted URL from web page provider as fallback")
                return .urlOnly(url)
            }
        }

        logger.debug("No usable web page data found in property-list attachments")
        return .none
    }

    static func extractURL(from attachments: [some ShareItemProviding]) async -> URL? {
        let urlType = UTType.url.identifier
        let plainTextType = UTType.plainText.identifier

        for provider in attachments where provider.hasItemConformingToTypeIdentifier(urlType) {
            if let url = await loadURL(from: provider, typeIdentifier: urlType) {
                logger.info("Extracted URL from URL attachment: \(url.absoluteString)")
                return url
            }
        }

        for provider in attachments where provider.hasItemConformingToTypeIdentifier(plainTextType) {
            if let url = await loadURLFromPlainText(from: provider, typeIdentifier: plainTextType) {
                logger.info("Extracted URL from plain-text attachment: \(url.absoluteString)")
                return url
            }
        }

        logger.debug("Generic URL extraction did not find a URL")
        return nil
    }

    static func extractImageFileURL(from attachments: [some ShareItemProviding]) async -> URL? {
        let imageType = UTType.image.identifier

        for provider in attachments where provider.hasItemConformingToTypeIdentifier(imageType) {
            if let url = await loadImageFileURL(from: provider, typeIdentifier: imageType) {
                logger.info("Extracted image file URL from attachment: \(url.lastPathComponent)")
                return url
            }
        }

        logger.debug("Image extraction did not find an image file URL")
        return nil
    }

    static func urlFromPlainText(_ text: String) -> URL? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed), url.scheme != nil else {
            return nil
        }
        return url
    }

    // MARK: - Private

    private static func loadPageContent(
        from provider: some ShareItemProviding,
        typeIdentifier: String
    ) async -> PageContent? {
        do {
            let item = try await provider.loadItem(forTypeIdentifier: typeIdentifier, options: nil)
            guard let dictionary = item as? NSDictionary else {
                let itemType = String(describing: type(of: item))
                logger.debug("Property list item is not NSDictionary (type: \(itemType))")
                return nil
            }
            let jsKey = NSExtensionJavaScriptPreprocessingResultsKey
            guard let jsValues = dictionary[jsKey] as? [String: Any] else {
                logger.debug("No JS preprocessing results key in property list")
                return nil
            }
            if let jsError = jsValues["error"] as? String {
                logger.error("JS preprocessing threw error: \(jsError)")
            }
            guard jsValues.isEmpty == false else {
                logger.debug("JS preprocessing results were empty")
                return nil
            }
            guard let pageContent = PageContent(from: jsValues) else {
                let keys = Array(jsValues.keys)
                logger.error("Failed to parse PageContent from JS values (keys: \(keys))")
                return nil
            }
            logger.info(
                "Parsed PageContent from JS preprocessing results"
            )
            return pageContent
        } catch {
            logger.error("Failed to load property list item: \(error.localizedDescription)")
            return nil
        }
    }

    private static func loadURLFromPropertyList(
        from provider: some ShareItemProviding,
        typeIdentifier: String
    ) async -> URL? {
        do {
            let item = try await provider.loadItem(forTypeIdentifier: typeIdentifier, options: nil)
            guard let dictionary = item as? NSDictionary else {
                let itemType = String(describing: type(of: item))
                logger.debug(
                    "Property-list URL fallback: item is not NSDictionary (type: \(itemType))"
                )
                return nil
            }

            // Safari usually nests results under NSExtensionJavaScriptPreprocessingResultsKey,
            // but some hosts provide URL values at different depths.
            let jsKey = NSExtensionJavaScriptPreprocessingResultsKey
            if let jsValues = dictionary[jsKey],
               let match = urlFromPropertyListValue(
                   jsValues,
                   path: "$.\(jsKey)"
               ) {
                logger.debug("Recovered URL from property-list JS values at \(match.path)")
                return match.url
            }

            if let match = urlFromPropertyListValue(dictionary, path: "$") {
                logger.debug("Recovered URL from property-list at \(match.path)")
                return match.url
            }

            logger.info("Property-list payload did not contain a recoverable URL")
            return nil
        } catch {
            logger.error("Failed to load property-list for URL fallback: \(error.localizedDescription)")
            return nil
        }
    }

    private static func loadURL(
        from provider: some ShareItemProviding,
        typeIdentifier: String
    ) async -> URL? {
        do {
            let item = try await provider.loadItem(
                forTypeIdentifier: typeIdentifier,
                options: nil
            )
            if let url = item as? URL {
                return url
            }
            if let nsurl = item as? NSURL, let url = nsurl as URL? {
                return url
            }
            if let data = item as? Data,
               let url = URL(dataRepresentation: data, relativeTo: nil) {
                return url
            }
            let itemType = String(describing: type(of: item))
            logger.debug(
                "URL load failed for type \(typeIdentifier). Item type: \(itemType)"
            )
            return nil
        } catch {
            logger.error(
                "Failed to load URL item for type \(typeIdentifier): \(error.localizedDescription)"
            )
            return nil
        }
    }

    private static func loadURLFromPlainText(
        from provider: some ShareItemProviding,
        typeIdentifier: String
    ) async -> URL? {
        do {
            let item = try await provider.loadItem(forTypeIdentifier: typeIdentifier, options: nil)
            let text: String? = if let str = item as? String {
                str
            } else if let data = item as? Data {
                String(data: data, encoding: .utf8)
            } else {
                nil
            }

            if text == nil {
                let itemType = String(describing: type(of: item))
                logger.debug(
                    "Plain-text URL load failed for type \(typeIdentifier). Item type: \(itemType)"
                )
            }
            return text.flatMap { self.urlFromPlainText($0) }
        } catch {
            logger.error(
                "Failed to load plain-text item for type \(typeIdentifier): \(error.localizedDescription)"
            )
            return nil
        }
    }

    private static func loadImageFileURL(
        from provider: some ShareItemProviding,
        typeIdentifier: String
    ) async -> URL? {
        do {
            guard let url = try await provider.loadFileRepresentation(forTypeIdentifier: typeIdentifier) else {
                logger.debug("Image file representation returned nil URL for type \(typeIdentifier)")
                return nil
            }

            let tempURL = FileManager.default.temporaryDirectory
                .appendingPathComponent(UUID().uuidString)
                .appendingPathExtension("jpg")
            do {
                try FileManager.default.copyItem(at: url, to: tempURL)
                return tempURL
            } catch {
                logger.error("Failed to copy image file representation: \(error.localizedDescription)")
                return nil
            }
        } catch {
            logger.error(
                "Failed to load image file representation for type \(typeIdentifier): \(error.localizedDescription)"
            )
            return nil
        }
    }
}

// MARK: - Property List URL Traversal

private extension ShareImportExtractor {
    static let maxPropertyListTraversalDepth = 16
    static let maxPropertyListTraversalNodes = 2000

    static func urlFromPropertyListValue(
        _ value: Any,
        path: String
    ) -> (url: URL, path: String)? {
        var remainingNodes = self.maxPropertyListTraversalNodes
        return self.urlFromPropertyListValue(
            value,
            path: path,
            depth: 0,
            remainingNodes: &remainingNodes
        )
    }

    // swiftlint:disable:next cyclomatic_complexity
    static func urlFromPropertyListValue(
        _ value: Any,
        path: String,
        depth: Int,
        remainingNodes: inout Int
    ) -> (url: URL, path: String)? {
        guard depth <= self.maxPropertyListTraversalDepth else {
            return nil
        }
        guard remainingNodes > 0 else {
            return nil
        }
        remainingNodes -= 1

        if let url = value as? URL {
            return (url, path)
        }
        if let nsurl = value as? NSURL, let url = nsurl as URL? {
            return (url, path)
        }
        if let text = value as? String,
           let url = urlFromPlainText(text) {
            return (url, path)
        }
        if let dictionary = value as? NSDictionary {
            if let lowercaseURL = dictionary["url"],
               let match = urlFromPropertyListValue(
                   lowercaseURL,
                   path: "\(path).url",
                   depth: depth + 1,
                   remainingNodes: &remainingNodes
               ) {
                return match
            }
            if let uppercaseURL = dictionary["URL"],
               let match = urlFromPropertyListValue(
                   uppercaseURL,
                   path: "\(path).URL",
                   depth: depth + 1,
                   remainingNodes: &remainingNodes
               ) {
                return match
            }
            for key in dictionary.allKeys {
                guard let keyString = key as? String else { continue }
                guard let nestedValue = dictionary[key] else { continue }
                if let match = urlFromPropertyListValue(
                    nestedValue,
                    path: "\(path).\(keyString)",
                    depth: depth + 1,
                    remainingNodes: &remainingNodes
                ) {
                    return match
                }
            }
        }
        if let array = value as? NSArray {
            for (index, nestedValue) in array.enumerated() {
                if let match = urlFromPropertyListValue(
                    nestedValue,
                    path: "\(path)[\(index)]",
                    depth: depth + 1,
                    remainingNodes: &remainingNodes
                ) {
                    return match
                }
            }
        }
        return nil
    }
}

import os
import Sentry
import SwiftUI
import UIKit
import UniformTypeIdentifiers

private let logger = Logger(subsystem: "app.hauptgang.ios.share-extension", category: "ShareViewController")

@objc(ShareViewController)
class ShareViewController: UIViewController {
    private var importState: ImportState = .extracting
    private var hostingController: UIHostingController<ImportRecipeView>?
    private var importTask: Task<Void, Never>?

    override func viewDidLoad() {
        super.viewDidLoad()
        SentrySDK.start { options in
            options.dsn = Constants.Sentry.dsn
            options.environment = Constants.Sentry.environment
            options.sendDefaultPii = false
            #if DEBUG
            options.debug = true
            options.enabled = false
            #endif
        }
        self.setupUI()
        self.extractContent()
    }

    deinit {
        importTask?.cancel()
    }

    private func setupUI() {
        let importView = self.makeImportView(state: self.importState)
        let hostingController = UIHostingController(rootView: importView)
        self.hostingController = hostingController

        addChild(hostingController)
        view.addSubview(hostingController.view)
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        hostingController.didMove(toParent: self)
    }

    private func updateState(_ newState: ImportState) {
        self.importState = newState
        self.hostingController?.rootView = self.makeImportView(state: newState)
    }

    private func makeImportView(state: ImportState) -> ImportRecipeView {
        ImportRecipeView(
            state: state,
            onClose: { [weak self] in self?.close() },
            onOpenApp: { [weak self] in self?.openMainApp() }
        )
    }

    private func extractContent() {
        guard let extensionItems = extensionContext?.inputItems as? [NSExtensionItem]
        else {
            logger.error("No extension item or attachments found")
            self.updateState(.failed("No content found"))
            return
        }

        let attachments = extensionItems.flatMap { $0.attachments ?? [] }
        guard attachments.isEmpty == false else {
            logger.error("No attachments found across \(extensionItems.count) extension items")
            self.updateState(.failed("No content found"))
            return
        }

        for (index, provider) in attachments.enumerated() {
            logger.info("Attachment \(index): \(provider.registeredTypeIdentifiers)")
        }

        self.importTask = Task {
            // Restore cookbook context so extension imports into the user's active cookbook
            if let user = await KeychainService.shared.getUser() {
                await CookbookContext.shared.configure(userId: user.id)
            }

            // Try JS preprocessing results first (Safari shares with page content)
            let webPageResult = await ShareImportExtractor.extractWebPageData(from: attachments)
            switch webPageResult {
            case let .success(pageContent):
                // Some domains are handled entirely by the backend (e.g. YouTube uses the Data API).
                // Skip client-side extraction and send URL only.
                if self.isBackendOnlyDomain(pageContent.url) {
                    logger
                        .info("Backend-only domain, skipping client-side extraction: \(pageContent.url.absoluteString)")
                    await self.handleExtractedURL(pageContent.url)
                    return
                }

                let url = pageContent.url.absoluteString
                let jsonLdCount = pageContent.jsonLd.count
                let htmlSize = pageContent.html.utf8.count
                logger.info(
                    "JS preprocessing OK — URL: \(url), JSON-LD: \(jsonLdCount), HTML: \(htmlSize) bytes"
                )
                await self.handleExtractedPageContent(pageContent)
                return
            case let .urlOnly(url):
                logger.info("JS preprocessing failed, but got URL from web page item: \(url.absoluteString)")
                await self.handleExtractedURL(url)
                return
            case .none:
                break
            }

            // Try URL (non-Safari apps, or when JS preprocessing unavailable)
            if let url = await ShareImportExtractor.extractURL(from: attachments) {
                logger.info("URL extracted: \(url.absoluteString)")
                await self.handleExtractedURL(url)
                return
            }

            // Fall back to image
            if let imageFileURL = await ShareImportExtractor.extractImageFileURL(from: attachments) {
                logger.info("Image extracted: \(imageFileURL.lastPathComponent)")
                await self.handleExtractedImage(imageFileURL)
                return
            }

            logger.error("Could not extract any content from attachments")
            self.reportImportFailure(message: "Could not extract content from attachments", source: "extraction")
            await MainActor.run {
                self.updateState(.failed("Could not extract recipe content"))
            }
        }
    }

    private func handleExtractedPageContent(_ pageContent: PageContent) async {
        if let unsupportedDomain = self.unsupportedDomain(for: pageContent.url) {
            logger.info("Unsupported domain detected: \(unsupportedDomain, privacy: .public)")
            self.reportImportFailure(
                message: "Unsupported domain",
                source: "web_page_with_content",
                url: pageContent.url.absoluteString,
                host: unsupportedDomain
            )
            await MainActor.run {
                self.updateState(.failed("Importing from \(unsupportedDomain) is currently not supported."))
            }
            return
        }

        await MainActor.run {
            self.updateState(.importing(pageContent.url))
        }

        guard await KeychainService.shared.getToken() != nil else {
            await MainActor.run {
                self.updateState(.notAuthenticated)
            }
            return
        }

        do {
            _ = try await RecipeImportService.shared.importRecipe(from: pageContent.url, pageContent: pageContent)
            logger.info("Import with content succeeded for \(pageContent.url.absoluteString)")
            await MainActor.run {
                self.updateState(.success)
            }
            try? await Task.sleep(for: .seconds(2))
            await MainActor.run {
                self.close()
            }
        } catch APIError.payloadTooLarge {
            logger.warning(
                "Payload too large for \(pageContent.url.absoluteString), falling back to URL-only import"
            )
            await self.handleExtractedURL(pageContent.url)
        } catch {
            logger
                .error(
                    "Import with content failed for \(pageContent.url.absoluteString): \(error.localizedDescription)"
                )
            SentrySDK.capture(error: error) { scope in
                scope.setContext(value: [
                    "source": "web_page_with_content",
                    "url": pageContent.url.absoluteString,
                    "host": pageContent.url.host ?? "unknown"
                ], key: "import")
            }
            await MainActor.run {
                self.updateState(.failed(error.localizedDescription))
            }
        }
    }

    private func handleExtractedURL(_ url: URL) async {
        if let unsupportedDomain = self.unsupportedDomain(for: url) {
            logger.info("Unsupported domain detected: \(unsupportedDomain, privacy: .public)")
            self.reportImportFailure(
                message: "Unsupported domain",
                source: "url_only",
                url: url.absoluteString,
                host: unsupportedDomain
            )
            await MainActor.run {
                self.updateState(.failed("Importing from \(unsupportedDomain) is currently not supported."))
            }
            return
        }

        await MainActor.run {
            self.updateState(.importing(url))
        }

        guard await KeychainService.shared.getToken() != nil else {
            logger.error("Not authenticated — no token found")
            await MainActor.run {
                self.updateState(.notAuthenticated)
            }
            return
        }

        do {
            _ = try await RecipeImportService.shared.importRecipe(from: url)
            logger.info("URL-only import succeeded for \(url.absoluteString)")
            await MainActor.run {
                self.updateState(.success)
            }
            try? await Task.sleep(for: .seconds(2))
            await MainActor.run {
                self.close()
            }
        } catch {
            logger.error("URL-only import failed for \(url.absoluteString): \(error.localizedDescription)")
            SentrySDK.capture(error: error) { scope in
                scope.setContext(value: [
                    "source": "url_only",
                    "url": url.absoluteString,
                    "host": url.host ?? "unknown"
                ], key: "import")
            }
            await MainActor.run {
                self.updateState(.failed(error.localizedDescription))
            }
        }
    }

    private func handleExtractedImage(_ fileURL: URL) async {
        defer { try? FileManager.default.removeItem(at: fileURL) }

        await MainActor.run {
            self.updateState(.importing(nil))
        }

        guard await KeychainService.shared.getToken() != nil else {
            logger.error("Not authenticated — no token found")
            await MainActor.run {
                self.updateState(.notAuthenticated)
            }
            return
        }

        guard let compressed = ImageCompressor.compressToJPEG(from: fileURL) else {
            logger.error("Image compression failed for \(fileURL.lastPathComponent)")
            self.reportImportFailure(message: "Image compression failed", source: "image")
            await MainActor.run {
                self.updateState(.failed("Could not process image"))
            }
            return
        }

        do {
            _ = try await RecipeImportService.shared.importRecipe(from: compressed)
            logger.info("Image import succeeded")
            await MainActor.run {
                self.updateState(.success)
            }
            try? await Task.sleep(for: .seconds(2))
            await MainActor.run {
                self.close()
            }
        } catch {
            logger.error("Image import failed: \(error.localizedDescription)")
            SentrySDK.capture(error: error) { scope in
                scope.setContext(value: ["source": "image"], key: "import")
            }
            await MainActor.run {
                self.updateState(.failed(error.localizedDescription))
            }
        }
    }

    private func openMainApp() {
        guard let appURL = URL(string: "hauptgang://") else { return }
        extensionContext?.open(appURL) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.close()
            }
        }
    }

    private func close() {
        self.importTask?.cancel()
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }

    private static let backendOnlyDomains: Set<String> = [
        "youtube.com",
        "youtu.be"
    ]

    private func isBackendOnlyDomain(_ url: URL) -> Bool {
        guard let host = url.host?.lowercased() else { return false }
        return Self.backendOnlyDomains.contains { host == $0 || host.hasSuffix(".\($0)") }
    }

    private func unsupportedDomain(for url: URL) -> String? {
        guard let host = url.host?.lowercased() else {
            return nil
        }

        for domain in UnsupportedImportDomains.all {
            if host == domain || host.hasSuffix(".\(domain)") {
                return domain
            }
        }
        return nil
    }

    private func reportImportFailure(message: String, source: String, url: String? = nil, host: String? = nil) {
        let event = Event(level: .error)
        event.message = SentryMessage(formatted: "Recipe import failed: \(message)")
        var extra: [String: Any] = ["source": source]
        if let url {
            extra["url"] = url
        }
        if let host {
            extra["host"] = host
        }
        event.extra = extra
        SentrySDK.capture(event: event)
    }
}

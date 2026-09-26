import SwiftUI
import UIKit

struct CachedRecipeImage<Content: View, Placeholder: View, Failure: View>: View {
    let url: URL?
    let maxPixelSize: CGFloat?
    let cache: RecipeImageCache
    @ViewBuilder let content: (Image) -> Content
    @ViewBuilder let placeholder: () -> Placeholder
    @ViewBuilder let failure: () -> Failure

    @State private var phase: Phase = .empty

    init(
        url: URL?,
        maxPixelSize: CGFloat? = nil,
        cache: RecipeImageCache = .shared,
        @ViewBuilder content: @escaping (Image) -> Content,
        @ViewBuilder placeholder: @escaping () -> Placeholder,
        @ViewBuilder failure: @escaping () -> Failure
    ) {
        self.url = url
        self.maxPixelSize = maxPixelSize
        self.cache = cache
        self.content = content
        self.placeholder = placeholder
        self.failure = failure
    }

    var body: some View {
        Group {
            switch self.phase {
            case .empty:
                self.placeholder()
            case let .success(image):
                self.content(image)
            case .failure:
                self.failure()
            }
        }
        .task(id: self.url) {
            await self.load()
        }
    }

    @MainActor
    private func load() async {
        guard let url else {
            self.phase = .failure
            return
        }

        self.phase = .empty

        #if DEBUG && targetEnvironment(simulator)
        let captureLoadId = UUID()
        ScreenshotSupport.imageStarted(captureLoadId)
        defer { ScreenshotSupport.imageFinished(captureLoadId) }
        #endif

        do {
            let image = try await cache.image(for: url, maxPixelSize: self.maxPixelSize)
            if Task.isCancelled {
                return
            }
            self.phase = .success(Image(uiImage: image))
        } catch {
            if Task.isCancelled {
                return
            }
            self.phase = .failure
            #if DEBUG && targetEnvironment(simulator)
            ScreenshotSupport.imageFinished(captureLoadId, error: "Image \(url): \(error.localizedDescription)")
            #endif
        }
    }

    enum Phase {
        case empty
        case success(Image)
        case failure
    }
}

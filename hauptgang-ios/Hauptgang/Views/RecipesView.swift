import os
import PhotosUI
import RevenueCatUI
import SwiftData
import SwiftUI

private let logger = Logger(subsystem: "app.hauptgang.ios", category: "RecipesView")

/// Lightweight value capturing the info needed for the delete confirmation dialog,
/// avoiding holding a SwiftData model object in @State after deletion.
struct DeleteCandidate: Identifiable {
    let id: Int
    let name: String
}

struct MoveCandidate: Identifiable {
    let id: Int
    let name: String
    let targetCookbookId: Int
    let targetCookbookName: String
}

private struct ClipboardContent: Identifiable {
    let id = UUID()
    let text: String
}

struct RecipesView: View {
    @EnvironmentObject var authManager: AuthManager
    @Environment(AuthenticatedSessionViewModel.self) private var session
    @Environment(CookbookViewModel.self) private var cookbookViewModel
    @Environment(NetworkMonitor.self) private var networkMonitor
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.scenePhase) private var scenePhase
    let recipeViewModel: RecipeViewModel
    let suppressTransientUI: Bool
    @Binding var pendingRecipeId: Int?

    @State private var showingCamera = false
    @State private var showingPhotoPicker = false
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var isScrolledDown = false
    @State private var navigationPath = NavigationPath()
    @State private var recipeToDelete: DeleteCandidate?
    @State private var recipeToMove: MoveCandidate?
    @State private var clipboardContent: ClipboardContent?

    init(
        recipeViewModel: RecipeViewModel,
        suppressTransientUI: Bool,
        pendingRecipeId: Binding<Int?> = .constant(nil)
    ) {
        self.recipeViewModel = recipeViewModel
        self.suppressTransientUI = suppressTransientUI
        self._pendingRecipeId = pendingRecipeId
    }

    var body: some View {
        NavigationStack(path: self.$navigationPath) {
            self.recipeContent
        }
        .onChange(of: self.pendingRecipeId, initial: true) { _, recipeId in
            guard let recipeId else { return }
            self.navigationPath.append(recipeId)
            self.pendingRecipeId = nil
        }
        .offlineToast(
            isOffline: self.networkMonitor.isOffline,
            showToast: self.shouldShowOfflineToast
        )
    }

    private var recipeContent: some View {
        self.recipeLayout
            .onChange(of: self.recipeViewModel.didReceiveForbidden) { _, forbidden in
                guard forbidden else { return }
                self.recipeViewModel.didReceiveForbidden = false
                Task {
                    await self.session.handleForbidden()
                }
            }
            .onChange(of: self.scenePhase) { oldPhase, newPhase in
                if oldPhase == .background && newPhase == .active {
                    Task {
                        await self.session.refreshActiveCookbook()
                        self.promptForNotificationsIfRecipesVisible()
                    }
                }
            }
            // Both conditions need their own observer. The recipes arrive during the
            // startup cache load, while the splash still covers this view, and the count
            // does not change again when the splash lifts.
            .onChange(of: self.recipeViewModel.recipes.count, initial: true) { _, _ in
                self.promptForNotificationsIfRecipesVisible()
            }
            .onChange(of: self.suppressTransientUI, initial: true) { _, _ in
                self.promptForNotificationsIfRecipesVisible()
            }
            .onChange(of: self.selectedPhotoItem) { _, newItem in
                guard let newItem else { return }
                Task {
                    if let data = try? await newItem.loadTransferable(type: Data.self) {
                        await self.recipeViewModel.importRecipeFromImage(data)
                    }
                    self.selectedPhotoItem = nil
                }
            }
            .fullScreenCover(isPresented: self.$showingCamera) {
                CameraView { imageData in
                    Task { await self.recipeViewModel.importRecipeFromImage(imageData) }
                }
                .ignoresSafeArea()
            }
            .onDisappear {
                self.recipeViewModel.stopPolling()
            }
            .overlay {
                if self.recipeViewModel.isImporting {
                    self.importingOverlay
                }
            }
            .alert(
                "Import Failed",
                isPresented: Binding(
                    get: { self.recipeViewModel.importError != nil },
                    set: {
                        if !$0 {
                            self.recipeViewModel.importError = nil
                        }
                    }
                )
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                if let error = recipeViewModel.importError {
                    Text(error)
                }
            }
            .sheet(isPresented: Binding(
                get: { self.recipeViewModel.shouldShowPaywall },
                set: { self.recipeViewModel.shouldShowPaywall = $0 }
            )) {
                PaywallView()
            }
            .sheet(item: self.$clipboardContent) { content in
                ClipboardPreviewSheet(text: content.text) {
                    self.clipboardContent = nil
                    Task { await self.recipeViewModel.importRecipeFromText(content.text) }
                }
            }
    }

    /// A user looking at their own recipes can see what a reminder would be about, so
    /// this is the best moment to ask. Gated on a non-empty list rather than on login:
    /// someone reinstalling hits it seconds after signing in, while a brand-new account
    /// stays silent until their first import shows up here.
    ///
    /// `suppressTransientUI` is what makes "looking at" true. Without it the recipes
    /// arrive under the startup splash and the one system prompt iOS grants gets spent
    /// on a logo — worst for exactly the reinstalling user this exists to catch.
    private func promptForNotificationsIfRecipesVisible() {
        guard !self.suppressTransientUI, !self.recipeViewModel.recipes.isEmpty else { return }

        Task {
            await PushNotificationService.shared.promptForAuthorization()
        }
    }

    private var recipeLayout: some View {
        Group {
            if self.shouldShowEmptyState {
                self.emptyStateView
            } else if self.recipeViewModel.recipes.isEmpty {
                Color.clear
            } else {
                self.recipeListView
            }
        }
        .background(Color.mcCanvas.ignoresSafeArea())
        .navigationDestination(for: Int.self) { recipeId in
            RecipeDetailView(recipeId: recipeId)
        }
        .navigationTitle(self.cookbookViewModel.activeCookbook?.name ?? "Recipes")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarTitleMenu {
            CookbookTitleMenu(
                cookbooks: self.cookbookViewModel.cookbooks,
                activeCookbookId: self.cookbookViewModel.activeCookbook?.id,
                onSelect: self.selectCookbook
            )
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    if UIImagePickerController.isSourceTypeAvailable(.camera) {
                        Button {
                            self.showingCamera = true
                        } label: {
                            Label("Take Photo", systemImage: "camera")
                        }
                    }
                    Button {
                        self.showingPhotoPicker = true
                    } label: {
                        Label("Choose from Library", systemImage: "photo.on.rectangle")
                    }
                    Button {
                        if let text = UIPasteboard.general.string, !text.isEmpty {
                            self.clipboardContent = ClipboardContent(text: text)
                        } else {
                            self.recipeViewModel.importError =
                                "Nothing to paste. Copy a recipe to your clipboard first."
                        }
                    } label: {
                        Label("Paste from Clipboard", systemImage: "doc.on.clipboard")
                    }
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .photosPicker(isPresented: self.$showingPhotoPicker, selection: self.$selectedPhotoItem, matching: .images)
    }

    private var shouldShowOfflineToast: Bool {
        !self.isScrolledDown && !self.suppressTransientUI
    }

    private var shouldShowEmptyState: Bool {
        self.recipeViewModel.recipes.isEmpty && !self.recipeViewModel.isLoading && !self.suppressTransientUI
    }

    // MARK: - Handlers

    private func selectCookbook(_ cookbook: Cookbook) {
        Task {
            await self.session.switchCookbook(cookbook)
        }
    }

    // MARK: - Subviews

    private var importingOverlay: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
            VStack(spacing: Theme.Spacing.md) {
                ProgressView()
                    .scaleEffect(1.5)
                    .tint(.white)
                Text("Importing recipe...")
                    .font(.headline)
                    .foregroundColor(.white)
            }
            .padding(Theme.Spacing.xl)
            .modifier(ImportingOverlayBackground())
        }
    }

    private var recipeListView: some View {
        ScrollView {
            LazyVGrid(columns: self.recipeColumns, spacing: Theme.Spacing.md) {
                ForEach(self.recipeViewModel.successfulRecipes) { recipe in
                    self.recipeRow(recipe)
                }
            }
            .frame(maxWidth: self.recipeGridMaxWidth)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Theme.Spacing.lg)
            .padding(.vertical, Theme.Spacing.sm)
        }
        .scrollDismissesKeyboard(.immediately)
        .onScrollGeometryChange(for: Bool.self) { geometry in
            geometry.contentOffset.y > 10
        } action: { _, isScrolled in
            self.isScrolledDown = isScrolled
        }
        .refreshable {
            await self.networkMonitor.refreshStatus()
            await self.session.refreshActiveCookbook()
        }
        .overlay(alignment: .bottom) {
            self.failedRecipeBanners
        }
    }

    private var recipeColumns: [GridItem] {
        if self.horizontalSizeClass == .compact {
            return [GridItem(.flexible(), spacing: Theme.Spacing.md)]
        }

        return [GridItem(.adaptive(minimum: 280, maximum: 360), spacing: Theme.Spacing.md)]
    }

    private var recipeGridMaxWidth: CGFloat {
        self.horizontalSizeClass == .compact ? .infinity : 1100
    }

    private func recipeRow(_ recipe: PersistedRecipe) -> some View {
        Button {
            self.navigationPath.append(recipe.id)
        } label: {
            RecipeCardView(recipe: recipe)
        }
        .buttonStyle(.plain)
        .contextMenu {
            if let targetCookbook = self.cookbookViewModel.cookbooks.first(where: {
                $0.id != self.cookbookViewModel.activeCookbook?.id
            }) {
                Button {
                    self.recipeToMove = MoveCandidate(
                        id: recipe.id,
                        name: recipe.name,
                        targetCookbookId: targetCookbook.id,
                        targetCookbookName: targetCookbook.name
                    )
                } label: {
                    Label("Move to \(targetCookbook.name)", systemImage: "arrow.right.arrow.left")
                }
            }
            Button(role: .destructive) {
                self.recipeToDelete = DeleteCandidate(id: recipe.id, name: recipe.name)
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
        .confirmationDialog(
            "Delete Recipe",
            isPresented: Binding(
                get: { self.recipeToDelete?.id == recipe.id },
                set: {
                    if !$0 {
                        self.recipeToDelete = nil
                    }
                }
            ),
            presenting: self.recipeToDelete
        ) { candidate in
            Button("Delete", role: .destructive) {
                Task {
                    await self.recipeViewModel.deleteRecipe(id: candidate.id)
                }
            }
        } message: { _ in
            Text("Are you sure?")
        }
        .confirmationDialog(
            "Move Recipe",
            isPresented: Binding(
                get: { self.recipeToMove?.id == recipe.id },
                set: {
                    if !$0 {
                        self.recipeToMove = nil
                    }
                }
            ),
            presenting: self.recipeToMove
        ) { candidate in
            Button("Move to \(candidate.targetCookbookName)") {
                Task {
                    await self.recipeViewModel.moveRecipe(
                        id: candidate.id,
                        toCookbookId: candidate.targetCookbookId
                    )
                }
            }
        } message: { candidate in
            Text("Move \"\(candidate.name)\" to \(candidate.targetCookbookName)?")
        }
    }

    /// Floating error banners with swipe-to-dismiss
    private var failedRecipeBanners: some View {
        VStack(spacing: Theme.Spacing.sm) {
            ForEach(self.recipeViewModel.failedRecipes) { recipe in
                ErrorBannerView(recipe: recipe) {
                    Task {
                        await self.recipeViewModel.dismissFailedRecipe(recipe)
                    }
                }
                .padding(.horizontal, Theme.Spacing.lg)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .padding(.bottom, Theme.Spacing.sm)
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: self.recipeViewModel.failedRecipes.count)
    }

    private var emptyStateView: some View {
        VStack(spacing: Theme.Spacing.lg) {
            Spacer()

            VStack(spacing: Theme.Spacing.sm) {
                Text("No recipes yet")
                    .font(.title3)
                    .fontWeight(.semibold)
                    .foregroundColor(.mcInk)

                Text("Your recipes will appear here")
                    .font(.subheadline)
                    .foregroundColor(.mcBody)
            }

            Button {
                Task {
                    await self.networkMonitor.refreshStatus()
                    await self.session.refreshActiveCookbook()
                }
            } label: {
                Label("Refresh", systemImage: "arrow.clockwise")
                    .font(.subheadline)
            }
            .buttonStyle(.bordered)
            .tint(.mcAccent)

            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}

private struct ImportingOverlayBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content
                .glassEffect(.regular, in: .rect(cornerRadius: Theme.Radius.panel))
        } else {
            content
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: Theme.Radius.panel))
        }
    }
}

#Preview {
    let authManager = AuthManager()
    let session = AuthenticatedSessionViewModel()
    return RecipesView(recipeViewModel: session.recipeViewModel, suppressTransientUI: false)
        .environmentObject(authManager)
        .environment(session)
        .environment(session.cookbookViewModel)
        .environment(NetworkMonitor.shared)
        .modelContainer(for: PersistedRecipe.self, inMemory: true)
        .onAppear {
            authManager.signIn(user: User(id: 1, email: "test@example.com"))
        }
}

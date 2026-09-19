# iOS Authenticated Startup

`AuthenticatedSessionViewModel` owns authenticated startup for the iOS app. `RootView` creates one session for the authenticated lifecycle, and `AuthenticatedAppShell` starts it for the current `User` and keeps the startup splash visible until the session reaches a terminal startup state.

Startup ordering is explicit:

1. Configure `CookbookContext` for the authenticated user.
2. Configure and load `CookbookViewModel`.
3. Resolve the active cookbook.
4. Configure `RecipeViewModel` and its search index for that cookbook.
5. Attempt the initial recipe refresh.
6. Dismiss the startup splash when startup is `.ready` or `.failed`.

Recipe failures do not block the authenticated UI forever. They are represented in cookbook-scoped recipe content state, and the session can still allow the splash to dismiss into a degraded/error UI.

## Ownership rules

- Views must not independently resolve active cookbook ids from `CookbookContext` during startup. Cookbook resolution belongs to `AuthenticatedSessionViewModel`.
- Cookbook switching must go through `AuthenticatedSessionViewModel.switchCookbook(_:)`; views should not call `CookbookViewModel.setActiveCookbook(_:)` directly.
- `MainTabView` renders tabs only. It uses session-owned child view models and does not decide startup readiness.
- `RecipesView` renders recipe state and user actions. It no longer performs initial app startup.
- `RootView` keeps authenticated-user side effects such as subscription, push-notification, invitation, and deep-link handling, but cookbook-scoped startup is delegated to the session.

## Recipe readiness

`RecipeViewModel` exposes cookbook-scoped `RecipeContentState` instead of a global `hasResolvedInitialContent` flag. A refresh for cookbook A must not satisfy readiness for cookbook B. `hasResolvedContent(for:)` returns true only for a matching `.resolved` or `.failed` content state.

`configureSearchIndex(userId:cookbookId:)` requires a concrete cookbook id. Calling `refreshRecipes()` without a configured cookbook is a no-op that leaves content state idle; normal startup should prevent that path.

## Logout/account switching

`RecipeViewModel.clearData()` is async and awaits search-index reset. `AuthenticatedSessionViewModel.reset()` awaits cookbook and recipe cleanup so a logout or account switch cannot race a later login's search-index configuration.

## Interactive onboarding continuation

`RootView` also owns the durable explicit Keep intent for the interactive recipe example. Authentication binds that intent to the user; `AuthenticatedAppShell` attempts saving only after session startup has resolved. The preview can cover the tabs during saving or failure, but saving never extends the global startup splash. Retry/cancel follows the intent and account lifetime. The recipe repository is configured even when the initial cookbook request fails, so a later connection can recover without relaunching.

Successful example saves refresh/switch through the session owner and use the existing recipe navigation path. Invitations and notification destinations take priority. A successful empty cookbook shows the welcoming import/example invitation; failed loading shows retry instead.

# Web redesign — design decisions

Date: 1 September 2026

Rebuild the MainCourse **web** app (desktop + mobile) against the mockups in
`docs/web-design/`. The old ERB views are replaced wholesale; models, jobs,
importers, and the `Authentication` concern are kept.

## Sources of truth

| Concern | Reference |
|---|---|
| Visual design, tokens, layout | `docs/web-design/parts.mjs` (`T` object) + the `.dc.html` artboards |
| Functionality, business rules | The iOS app (`hauptgang-ios/`) and the existing `Api::V1` controllers |

The old Hauptgang palette (brown `#8B5E34`, Lato, Merriweather) is **superseded
on web**. It is kept as `ios-legacy-tokens.md` in the `maincourse-design` skill,
which otherwise documents the new web palette.

## Decisions

1. **Favorites are not surfaced on web.** The `recipes.favorite` column, the
   `Api::V1::FavoritesController`, and the API JSON stay untouched for the iOS
   app, but the heart badges and the favorite toggle drawn in the mockups are
   dropped, matching iOS (which never surfaces them either). The `Favorites`
   chip and the `Favorites` collection row from the mockups are therefore also
   dropped; the web `toggle_favorite` route and view are deleted.
2. **Collections (tags) are wired.** Tag chips filter the recipe list; tags are
   editable from the recipe form. Tags are a global `Tag` table, so the rail
   lists only tags that have at least one recipe in the active cookbook.
3. **Manual recipe creation is kept**, reachable as an "or add one manually"
   link inside the Add-a-recipe dialog. The edit form is needed regardless, so
   the create form is nearly free.
4. **Payments stay iOS-only.** No RevenueCat on web. `/pro` shows the feature
   list from the mockup and a "Upgrade in the MainCourse iOS app" CTA. No
   prices, no trial copy, no plan pickers — those would drift from the
   RevenueCat dashboard config. Settings shows "Manage in the iOS app" for Pro
   users. `users.pro` (set by the RevenueCat webhook) remains the only source of
   truth.
5. **Email + password only.** The "Continue with Apple/Google" buttons in the
   mockup are omitted; the backend has no OAuth and adding it is a separate
   project that would need iOS parity.
6. **Live sync via Turbo 8 morph refreshes.** `broadcasts_refreshes_to :cookbook`
   on `Recipe` and `ShoppingListItem` over the already-configured Solid Cable.
   This gets shared-cookbook sync *and* import-progress updates with no polling
   and no custom JS.
7. **Search lives both in the rail and behind ⌘K** in the recipes header; both
   route to `/search`. Search spans **all** of the user's cookbooks (matching
   the mockup's "3 results across Our Kitchen and My Recipes"), unlike every
   other screen which is scoped to the active cookbook.
8. **Shopping tiles are 2-up on mobile** (4-up desktop), not the 3-up from iOS —
   two-line names get too tight at 360px.
9. **IBM Plex Sans/Mono are self-hosted** as woff2 under `app/assets/fonts/`,
   consistent with the current setup and avoiding a third-party request.
10. **Light mode only.** The design defines no dark tokens; the old `dark:`
    variants are removed rather than guessed at.

## Active cookbook

The web previously hardcoded `Current.user.personal_cookbook`, so shared
cookbooks were invisible. It now mirrors the iOS `CookbookContext`:

- `Current.cookbook` is resolved per request from `session[:cookbook_id]`,
  validated against the user's memberships.
- Default when unset or invalid: **prefer shared, fall back to personal** — the
  same rule as `AuthenticatedSessionViewModel`.
- Every screen except search is scoped to it. Switching posts to
  `PATCH /active_cookbook`.

## Import on web

The old web import ran `RecipeImporter` **synchronously in the request** with no
limit check and stashed the result in the session for a review step. It is
replaced by the API's pattern:

- Create a placeholder `Recipe(name: "Importing…", import_status: :pending)`
  inside `current_user.with_lock` with `check_import_limit`.
- Enqueue `RecipeImportJob` (URL) or `RecipeImageExtractJob` (photo).
- Redirect back to the list; the placeholder card renders with a spinner and is
  replaced when the broadcast lands.
- `import_limit_reached?` redirects to `/pro`.

There is no review-before-save step on web any more — same as iOS, you edit
after the fact.

## Out of scope

Meal plan (deliberately hidden on iOS too), RevenueCat/web payments, OAuth,
favorites UI, per-ingredient or per-step checkoff, recipe sharing, more than two
cookbooks, roles beyond owner/collaborator.

# Web redesign — implementation plan

**Status: complete.** See `docs/web-ui.md` for the resulting architecture.

Design: `docs/superpowers/specs/2026-09-01-web-redesign-design.md`
Mockups: `docs/web-design/`

## Phase 1 — Foundation

- [x] Rewrite `app/assets/tailwind/application.css` `@theme` with the `parts.mjs`
      palette. Self-host IBM Plex Sans (300/400/500/600) + Mono (400/500) woff2
      in `app/assets/fonts/`; drop Lato/Merriweather/DM, `.grain-texture`, and
      the masonry FOUC guard from `app/assets/stylesheets/application.css`.
- [x] `Current.cookbook` + `CookbookScoped` controller concern (resolve from
      `session[:cookbook_id]`, default shared → personal). `PATCH /active_cookbook`.
- [x] New `layouts/application.html.erb`: 196px rail on `md:`, top app bar +
      slide-in drawer below. Rail = brand, cookbook switcher card, nav with
      counts, Collections, Settings footer. Flash rendering in the layout.
- [x] Delete: `HomeController` + views, `layouts/_navbar`, all `*.jbuilder`,
      `toggle_favorite` (route/action/view/partial), old recipe views/partials,
      `masonry`/`view_switcher`/`hello` Stimulus controllers, the `masonry-layout`
      importmap pin.

## Phase 2 — Recipes

- [x] `recipes#index`: card grid, tag chips, sort menu, ⌘K search field,
      pending-import spinner cards, failed-import banners, per-card dots menu
      (Open / Move to other cookbook / Delete).
- [x] `recipes#show`: hero, tags, source link, prep/cook stats, servings
      stepper, ingredients, numbered method, notes, dots menu.
- [x] Add-all-to-shopping-list review dialog (uncheck to exclude, "Add N").
- [x] `recipes#new` / `#edit` form rebuilt; tags as chips.
- [x] `broadcasts_refreshes_to :cookbook` on `Recipe`.

## Phase 3 — Import

- [x] Add-a-recipe dialog: URL field, photo upload, camera capture, manual link.
- [x] `POST /recipes/import` and `POST /recipes/import_photo`: placeholder +
      job + limit check, mirroring `Api::V1::RecipesController`.

## Phase 4 — Shopping list

- [x] `shopping_list_items#index` with To Buy / Already Got tiles (4-up desktop,
      2-up mobile), toggle, add bar, remove all, stale cleanup.
- [x] `broadcasts_refreshes_to :cookbook` on `ShoppingListItem`.

## Phase 5 — Cookbooks

- [x] `cookbooks#index`: personal panel, shared panel with members/roles,
      generate invite link, delete/leave, create-shared dialog with
      "move all personal recipes".
- [x] Cookbook switcher (desktop popover, mobile bottom sheet).
- [x] `/invite/:token` redesigned with real accept/reject for signed-in users;
      token stashed in session for signed-out visitors.

## Phase 6 — Settings, auth, Pro

- [x] `settings#edit`: account, display name, recipe reminders toggle, cookbooks
      link, subscription panel, sign out, delete account (typed `DELETE`).
- [x] Auth screens (sign in, sign up, forgot, reset) per `Account.dc.html`.
- [x] `/pro` page with the feature list and an iOS CTA.

## Phase 7 — Search, tests

- [x] `/search?q=` across all of the user's cookbooks (name, ingredient raw,
      instructions), grouped result count.
- [x] Rewrite controller tests; keep the API tests untouched; `bin/ci` green.
- [x] Responsive pass at 360px; all empty states.

## Non-goals

See the design doc. No migrations are required by any of this.

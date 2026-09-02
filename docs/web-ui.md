# Web UI

How the MainCourse web app (desktop + mobile browser) is put together. The iOS
app is a separate client on the same API; this doc is only about the HTML side.

Visual reference: `docs/web-design/` (mockups + the `T` token table in
`parts.mjs`). Design decisions and their rationale:
`docs/superpowers/specs/2026-09-01-web-redesign-design.md`.

## Design tokens

Tokens live in the `@theme` block of `app/assets/tailwind/application.css` and
are the single source of truth. Never hard-code a hex value in a view.

| Token | Use |
|---|---|
| `canvas` / `surface` / `rail` / `sunken` | page, cards, left rail, inputs & done states |
| `ink` / `body` / `muted` | headings, prose, labels and counts |
| `line` / `hairline` | dividers, borders |
| `accent`, `accent-dark`, `accent-tint`, `accent-line` | **the only action colour** |
| `lime` | signal only — the brand mark glyph, badges |
| `amber`, `amber-tint` | owner crown, Pro |
| `danger`, `danger-tint`, `danger-line` | destructive actions and failures |
| `rounded-control` (5px) / `rounded-card` (6px) / `rounded-panel` (8px) | radii |

Component shorthands (`mc-field`, `mc-btn`, `mc-btn-primary`, `mc-panel`,
`mc-section-label`, …) are `@utility` rules in the same file.

**Type:** IBM Plex Sans for everything, IBM Plex Mono (`font-mono`, or the
`mono` helper) for numerics *only* — times, servings, counts, IDs. Both are
self-hosted woff2 under `app/assets/fonts`, declared in
`app/assets/stylesheets/application.css`.

**Light mode only.** The design defines no dark tokens; do not add `dark:`
variants without designing them.

Icons come from `lucide-rails` through `ApplicationHelper#icon`, which applies
the design's 1.9 stroke. No emoji.

**Brand mark:** `ApplicationHelper#brand_mark` renders
`app/assets/images/logo.png` — the leather cookbook that is also the iOS app
icon and the getmaincourse.com header mark. It is sized by height with
`w-auto`, like the landing site, so the book keeps its proportions. The favicon
set (`favicon.ico`, `icon.png`, `apple-touch-icon.png`, rendered by
`layouts/_favicons`) is copied from the landing site so all web properties match.
If the artwork changes, re-export from `maincourse-landing/public/logo.png`:
`magick logo.png -trim +repage -resize x256 -strip -colors 200 logo.png`.

## Layout

`layouts/application.html.erb` is the signed-in shell:

- `layouts/_rail` — 196px left rail, `md:` and up.
- `layouts/_drawer` — the same contents in a slide-in panel below `md:`,
  driven by `drawer_controller.js`. There is deliberately **no bottom tab bar**.
- Both render `layouts/_rail_contents`, so the two never drift.
- `layouts/_app_bar` — the mobile top bar. A page can replace it entirely with
  `content_for :mobile_header` (usually `layouts/_back_bar`, which turns an
  iOS nav-stack push into a browser-style back affordance).

`layouts/authentication.html.erb` is the centred card used by sign in, sign up,
password reset and the invite landing page.

## Active cookbook

Every screen except search is scoped to `Current.cookbook`, resolved per request
by `CookbookScoped` (`app/controllers/concerns/cookbook_scoped.rb`):

- remembered in `session[:cookbook_id]`, validated against the user's memberships
- default when unset: **prefer shared, fall back to personal** — the same rule
  as the iOS `CookbookContext`
- switched via `PATCH /active_cookbook` (`ActiveCookbooksController`), which
  redirects to the equivalent *list* rather than the previous page, because
  records are per-cookbook and would 404

Search (`SearchesController`) is the exception: it spans every cookbook the user
belongs to. Opening a result that lives in the other cookbook makes
`RecipesController#set_recipe` switch to it rather than 404.

The API uses the `X-Cookbook-Id` header for the same purpose
(`Api::V1::BaseController#set_current_cookbook!`); the two mechanisms are
independent.

## Import

Web import mirrors the API exactly, and shares its jobs:

1. Create a placeholder `Recipe(name: "Importing…", import_status: :pending)`
   inside `Current.user.with_lock`, re-checking `import_limit_reached?` under
   the lock so the monthly cap cannot be raced.
2. Enqueue `RecipeImportJob` (link) or `RecipeImageExtractJob` (photo).
3. Redirect back to the list. The placeholder renders as a spinner card.

`import_limit_reached?` redirects to `/pro`. Failed imports never appear in the
grid — they render as dismissible banners, where dismiss deletes the record.

There is no review-before-save step on web; you edit after the fact, like iOS.

## Live updates

`Recipe` and `ShoppingListItem` both `broadcasts_refreshes_to :cookbook`, and
the index views subscribe with `turbo_stream_from current_cookbook`. Turbo 8
morph refreshes therefore cover two things at once with no polling and no
custom JS:

- an import that finishes in the background replaces its own spinner card
- members of a shared cookbook see each other's changes

Solid Cable is already configured; nothing else is needed.

## Stimulus controllers

Small and single-purpose. `app/javascript/controllers/`:

| Controller | Job |
|---|---|
| `drawer` | the mobile rail panel |
| `dialog` / `dialog_opener` | `<dialog>` close/backdrop, and opening one by id from elsewhere |
| `menu` | popovers — the per-recipe dots menu, the sort picker |
| `portion_scaler` | scales ingredient quantities from the servings stepper; also rewrites the hidden fields in the add-to-list dialog so the list gets the scaled amounts |
| `list_review` | the add-to-shopping-list tick/untick step |
| `recipe_form` | dynamic ingredient/step rows, cover preview, unsaved-changes guard |
| `auto_submit` | file pickers and toggles that submit on change |
| `search_shortcut` | ⌘K / Ctrl-K |
| `clipboard`, `dismissable` | invite link copy, flash dismissal |

`portion_scaler`'s number formatting deliberately mirrors
`RecipesHelper#format_amount` (including the unicode fraction table) so server
and client render the same string.

## Payments

There are none on the web. `users.pro` is written only by the RevenueCat webhook
(`Api::V1::Webhooks::RevenuecatController`); `/pro` explains the plan and links
to the App Store. Do not add prices or plan pickers to the web — they would
drift from the RevenueCat dashboard config.

## Gotchas

- **`stylesheet_link_tag :app` pulls in the Avo admin CSS.** Propshaft's `:app`
  means every stylesheet under `app/assets`. Both layouts name
  `"application", "tailwind"` explicitly instead.
- **A stale `public/assets` shadows your source.** If a newly added Stimulus
  controller silently never loads, check for a leftover local precompile:
  `rm -rf public/assets`. It is gitignored build output.
- **An empty file field purges an attachment.** Active Storage treats `""` as
  "delete", so `RecipesController#recipe_params` drops a blank `cover_image`.
- **`source_url` is user-supplied.** Link it through
  `RecipesHelper#safe_source_url`, which only allows http(s) — otherwise
  `javascript:` URLs become an XSS vector (Brakeman catches this).
- **Recipes referenced by a meal plan cannot be deleted.**
  `Recipe#meal_plan_entries` is `restrict_with_error`, and meal plans have no UI
  on web or iOS, so the entry can never be removed by a user. The web surfaces
  the error rather than failing silently.

## Not on the web

Meal plan (hidden on iOS too), favourites (the column and API remain for iOS),
RevenueCat/payments, OAuth sign-in, per-ingredient or per-step checkoff,
recipe sharing.

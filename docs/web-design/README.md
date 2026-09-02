# Web design mockups

Static mockups for the MainCourse **web** app (desktop + mobile), from the design
session on 31 August 2026. This is the visual reference for building the web UI —
it is *not* shipped code and nothing here is loaded by the Rails app.

> **These have been built.** The web UI now implements them; see
> [`docs/web-ui.md`](../web-ui.md) for how it is put together and
> [`docs/superpowers/specs/2026-09-01-web-redesign-design.md`](../superpowers/specs/2026-09-01-web-redesign-design.md)
> for where the build deliberately departs from these artboards (no favourites,
> no OAuth buttons, no prices on the Pro screen, 2-up shopping tiles on mobile).
> The tokens below now live in `app/assets/tailwind/application.css`.

Canvas (all screens, pan/zoom): https://claude.ai/code/artifact/4ecaa114-9c92-4eb9-9a9e-ea6704db05e4

## Two rules that shaped these

1. **The existing Rails ERB views are not the reference.** They are old and were
   explicitly ruled out for the look. The old Hauptgang tokens (brown `#8B5E34`,
   Lato, Merriweather) are **superseded** by the palette below for web. The iOS
   app has since adopted the same tokens
   (`hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift`); see the iOS
   section of the `maincourse-design` skill.
2. **Web patterns, not iOS patterns.** No bottom tab bar: the left rail collapses
   behind a menu button in a top app bar. Nav-stack pushes become a back
   affordance; action sheets become bottom sheets.

## Design tokens

Everything below is also the single source of truth in [`parts.mjs`](parts.mjs)
(the `T` object) — read it before hand-copying values out of the markup.

| Role | Value |
|---|---|
| Page canvas | `#EEF0F2` |
| Surface / card | `#FFFFFF` |
| Left rail | `#F8F9FA` |
| Sunken (inputs, chips, done states) | `#F5F7F8` |
| Ink (headings, primary text) | `#14171C` |
| Body text | `#5B6570` |
| Muted (labels, counts) | `#9AA3AE` |
| Hairline / border | `#E3E6EA` / `#DCE0E6` |
| Accent — the only action colour | deep green `#16624B`, hover `#0F4736`, tint `#F1F7F4` |
| Signal only (badges, "2 months free") | lime `#CDEB7A` |
| Owner crown | amber `#B07D12` |
| Danger | `#B42318`, tint `#FDF3F2` |

- **Type:** IBM Plex Sans (300/400/500/600) for everything; IBM Plex Mono (400/500)
  for numerics only — times, servings, counts, prices, IDs.
- **Radii:** 5px controls and chips, 6px cards and fields, 8px panels.
- **Icons:** inline lucide-style SVG, 1.9 stroke. No emoji. The set used is the `P`
  table in `parts.mjs`.
- **Layout:** 196px light left rail (cookbook switcher card → nav with counts →
  Collections → account footer). Desktop frames are 872×780, mobile 360×780.

## The screens

Each `.dc.html` is one artboard pairing a desktop frame with a mobile frame.

| File | Screen |
|---|---|
| `Main.dc.html` | Recipes — grid, filter chips, cookbook switcher |
| `RecipeDetail.dc.html` | Recipe — servings stepper, ingredients, method, source |
| `Search.dc.html` | Results with the per-recipe menu open; empty state |
| `ShoppingList.dc.html` | To Buy / Already Got square tiles, add bar |
| `Cookbooks.dc.html` | Members + invite link; create-your-first empty state |
| `Settings.dc.html` | Account, reminders, cookbooks, Pro, sign out / delete |
| `Import.dc.html` | Link / upload / camera dialog; in-progress and failed states |
| `Account.dc.html` | Sign in, Pro paywall, invite-accept |
| `MobileNav.dc.html` | Drawer open; cookbook-switcher bottom sheet |
| `DirectionA/B/C.dc.html` | The three rejected first-pass directions (page 2 of the canvas) |

Out of scope: **Meal Plan** — the tab is intentionally hidden in the iOS app
(`hauptgang-ios/Hauptgang/Views/MainTabView.swift`).

## Working with these files

**To read a screen:** open the `.dc.html` in any browser. They are plain HTML with
inline styles; the `<script src="./support.js">` 404 and the `<x-dc>` / `<helmet>`
custom elements are canvas plumbing and render fine without it.

**To change a screen:** edit the builder, not the HTML. The `.dc.html` files are
generated:

```bash
cd docs/web-design && node screens1.mjs && node screens2.mjs && node screens3.mjs
```

- `parts.mjs` — tokens, icons, rail, app bar, buttons, chips, recipe card, sample data
- `screens1.mjs` — Recipes, Recipe detail, Search, Shopping list
- `screens2.mjs` — Cookbooks, Settings
- `screens3.mjs` — Import, Account, Mobile nav

**To update the published canvas:** re-seed a fresh copy of the Claude Design
payload from these files and republish to the artifact URL above (`/design`
skill; pin `contract: "0.1.31"`). The seeded `.html` is build output and is
gitignored — never hand-edit it.

## Open questions for whoever builds this

- **Search as a rail destination** is inherited from the iOS tab. With ⌘K in the
  recipes header, the sidebar entry may be redundant on desktop.
- **Shopping tiles are 3-up on mobile**, matching iOS. At 360px the two-line names
  get tight; 2-up would breathe.
- **Collections in the rail are read-only** in the mockups. If tags are editable,
  that list needs an affordance.

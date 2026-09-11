---
name: maincourse-design
description: Use when styling or building UI in the MainCourse Rails web app or the SwiftUI iOS app — writing or editing ERB views, picking Tailwind classes, adding a screen, or needing a colour, type, radius, icon or component value. Also the source for the iOS app's shared colour, radius and mono tokens (see the iOS section).
---

# MainCourse Design System

Grey canvas, one deep-green accent, IBM Plex. Tokens live in the `@theme` block of
`app/assets/tailwind/application.css` — that file is the source of truth, this
skill is the map of it.

## Non-negotiables

- **Never hard-code a hex value in a view.** Use a token class (`bg-surface`,
  `text-muted`, `border-hairline`) or `var(--color-…)` in an inline `style`.
- **Light mode only.** No `dark:` variants — no dark tokens are designed.
- **Accent is the only action colour.** `lime` is signal (brand glyph, badges),
  `amber` is the owner crown / Pro, `danger` is destructive. Nothing else.
- **IBM Plex Mono is for numerics only** — times, servings, counts, IDs. Use the
  `mono` helper or `font-mono`. Everything else is Plex Sans.
- **Icons are lucide via `ApplicationHelper#icon`**, which applies the 1.9
  stroke. No emoji, ever.
- **The brand mark keeps its brown.** The palette dropped the old brown theme,
  but the logo did not come with it: `logo.png` and the iOS app icon are still
  the gold-`H` leather cookbook, and they stay that way. It is the one brown
  thing left and the one place brown is correct — never "fix" it to green, and
  never treat it as leftover legacy. Its ground, when it needs one, is the app
  icon's own cream (`#F1DFA9` → `#E6D097`), not `surface` or `accent`.
- **Web patterns, not iOS patterns.** No bottom tab bar; the rail collapses into
  a drawer. Nav-stack pushes become a back bar (`layouts/_back_bar`).
- **iOS patterns on iOS.** Tab bar, nav stack pushes, Lists and Forms stay
  native; only colours, type and radii are shared.

## Tokens

| Class stem | Value | Use |
|---|---|---|
| `canvas` | `#EEF0F2` | page behind the shell |
| `surface` | `#FFFFFF` | cards, panels, dialogs, main column |
| `rail` | `#F8F9FA` | left rail |
| `sunken` | `#F5F7F8` | inputs, chips, done states, hover fills |
| `ink` | `#14171C` | headings, primary text |
| `body` | `#5B6570` | prose, labels |
| `muted` | `#9AA3AE` | counts, captions, placeholder |
| `line` | `#E3E6EA` | dividers |
| `hairline` | `#DCE0E6` | control and card borders |
| `accent` / `accent-dark` / `accent-tint` / `accent-line` | `#16624B` / `#0F4736` / `#F1F7F4` / `#D6E7DF` | the only action colour |
| `lime` | `#CDEB7A` | signal only (glyph on accent, badges) |
| `amber` / `amber-tint` | `#B07D12` / `#FBF3E0` | owner crown, Pro |
| `danger` / `danger-tint` / `danger-line` | `#B42318` / `#FDF3F2` / `#EFD5D3` | destructive, failures |

Radii: `rounded-control` 5px (buttons, chips, nav rows), `rounded-card` 6px
(cards, fields), `rounded-panel` 8px (panels). Dialogs use `rounded-[10px]`.

Type: `--font-sans` IBM Plex Sans 300/400/500/600 (+300/400 italic),
`--font-mono` IBM Plex Mono 400/500. Self-hosted woff2 in `app/assets/fonts`,
declared in `app/assets/stylesheets/application.css`.

Sizes are explicit half-pixel values, not the Tailwind scale: `text-[12.5px]` for
most body copy, `text-[11.5px]` labels, `text-[13.5px]`/`text-[14.5px]` card
titles, `text-[29px]` desktop page titles (`text-[23px]` mobile) with
`tracking-[-0.7px]`. `font-light` is the default for secondary prose.

## Component utilities

`@utility` rules in `app/assets/tailwind/application.css`. Reach for these before
writing classes by hand:

| Utility | What it is |
|---|---|
| `mc-btn` + `mc-btn-primary` / `mc-btn-outline` / `mc-btn-quiet` / `mc-btn-danger` | every button; `mc-btn` alone is the base, always pair it with a variant |
| `mc-field` | text input / select / textarea |
| `mc-label` | field label above an `mc-field` |
| `mc-section-label` | 9.5px uppercase tracking label ("Collections") |
| `mc-panel` | bordered white panel with clipped corners |

## iOS

The iOS app (`hauptgang-ios/`) uses the same colour tokens, defined as hex
literals in `Hauptgang/Utilities/MainCourseTheme.swift` and compiled into both
the app and the share extension. Native chrome stays native: nav bars, the tab
bar, Lists, Forms, sheets, alerts and Liquid Glass keep system materials and are
only tinted by the green `AccentColor` asset.

| Web | iOS |
|---|---|
| `bg-canvas` / `bg-surface` / `bg-sunken` | `Color.mcCanvas` / `Color.mcSurface` / `Color.mcSunken` |
| `text-ink` / `text-body` / `text-muted` | `Color.mcInk` / `Color.mcBody` / `Color.mcMuted` |
| `border-line` / `border-hairline` | `Color.mcLine` / `Color.mcHairline` |
| `accent` `-dark` `-tint` `-line` | `Color.mcAccent` `.mcAccentDark` `.mcAccentTint` `.mcAccentLine` |
| `lime`, `amber(-tint)`, `danger(-tint/-line)` | `Color.mcLime`, `Color.mcAmber(Tint)`, `Color.mcDanger(Tint/Line)` |
| `rounded-control` / `-card` / `-panel` | `Theme.Radius.control` 8 / `.card` 10 / `.panel` 12 |
| `mc-btn-primary` / `mc-btn-outline` | `.primaryButton()` / `.outlineButton()` |
| `mc-field` | `.themeTextField()` |
| `mc-section-label` | `.caption2.weight(.medium)` + `.textCase(.uppercase)` + `.tracking(1.1)` + `Color.mcMuted` |
| `font-mono` | `Font.mcMono(_ style:, weight:)` — bundled IBM Plex Mono 400/500 |

- Text is **San Francisco** via semantic styles (`.headline`, `.body`, …), never
  Plex Sans and never `design: .serif`. Plex Mono is for numerics only.
- Always write `Color.mcAccent`, not `.mcAccent` — bare member syntax fails in
  `ShapeStyle` contexts.
- Flat surfaces get a 1px `Color.mcHairline` stroke, no shadow. Shadows only on
  floating overlays (error banners, glass).
- Selected chips are `Color.mcInk` with white text, not accent.
- Light only: `HauptgangApp` pins `.preferredColorScheme(.light)`.
- Photo-less recipe cards use `RecipePlaceholderGradient`, the same MD5-keyed
  gradient list as `RecipesHelper::PLACEHOLDER_GRADIENTS`.

## Helpers

`ApplicationHelper`: `icon(name, size:, stroke:)`, `brand_mark(size:)` (the
leather-cookbook `logo.png`, sized by height), `mono(value)`, `user_initials`,
`flash_styles`, `page_title`.

## Patterns

```erb
<%# Chip — selected chips are ink, not accent %>
<span class="rounded-control px-3 py-[5px] text-[12px] bg-ink font-medium text-white">All</span>
<span class="rounded-control px-3 py-[5px] text-[12px] border border-hairline text-body hover:border-accent-line">Pasta</span>

<%# Popover / menu (menu_controller.js) %>
<div class="rounded-[7px] border border-hairline bg-surface p-[5px] shadow-[0_12px_28px_-10px_rgba(20,23,28,0.28)]">

<%# Accent glyph tile — lime on accent, via the CSS var %>
<span class="flex size-6 items-center justify-center rounded-control bg-accent">
  <%= icon "book", size: 12, stroke: 2, style: "color: var(--color-lime)" %>
</span>
```

There are no shadow tokens: elevation is written inline and only on overlays
(menus, dialogs). Flat surfaces stay flat — separate with `border-hairline`.

Focus is global: `:focus-visible` draws a 2px accent outline in the base layer.
Fields add `focus:border-accent focus:ring-3 focus:ring-accent-tint` via
`mc-field`. Don't invent per-component focus styles.

## Common mistakes

- Adding a `dark:` variant. There is no dark palette.
- Using `accent` for a non-action (a count, a divider, decorative fill).
- Setting a number in Plex Sans, or body copy in Plex Mono.
- `bg-white` / `#fff` instead of `bg-surface`; `text-gray-500` instead of `text-muted`.
- Hand-rolling a button instead of `mc-btn mc-btn-*`.
- `stylesheet_link_tag :app` — it drags in the Avo admin CSS. Name
  `"application", "tailwind"` explicitly.

## More

- Full token list with RGB/OKLCH plus the shipped layout inventory:
  [references/tokens.md](references/tokens.md).
- How the web app is assembled (layouts, drawer, Stimulus, live updates):
  `docs/web-ui.md` in the maincourse repo.
- Original artboards and the `T` token object: `docs/web-design/`.

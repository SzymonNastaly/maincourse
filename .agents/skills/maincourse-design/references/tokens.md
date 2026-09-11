# MainCourse web design tokens

Full reference for the web palette, type, and layout metrics. The authoritative
copy is the `@theme` block in `app/assets/tailwind/application.css`; if the two
ever disagree, the CSS wins.

Every token name below is usable as a Tailwind colour class (`bg-canvas`,
`text-muted`, `border-hairline`, `ring-accent-tint`, `bg-danger-tint`) and as a
CSS variable (`var(--color-accent)`) for the rare inline `style`.

## Colours

### Surfaces
| Token | HEX | RGB | Use |
|---|---|---|---|
| `canvas` | `#EEF0F2` | `238, 240, 242` | page ground behind the shell; also the `theme-color` meta |
| `surface` | `#FFFFFF` | `255, 255, 255` | main column, cards, panels, dialogs |
| `rail` | `#F8F9FA` | `248, 249, 250` | desktop left rail and mobile drawer |
| `sunken` | `#F5F7F8` | `245, 247, 248` | inputs, chips, "already got" tiles, hover fills |

### Text
| Token | HEX | RGB | Use |
|---|---|---|---|
| `ink` | `#14171C` | `20, 23, 28` | headings, primary text, selected chips, drawer scrim (`bg-ink/45`) |
| `body` | `#5B6570` | `91, 101, 112` | prose, labels, inactive nav rows |
| `muted` | `#9AA3AE` | `154, 163, 174` | counts, captions, placeholders, empty-state icons |

### Lines
| Token | HEX | RGB | Use |
|---|---|---|---|
| `line` | `#E3E6EA` | `227, 230, 234` | dividers, rail border, header rules, avatar chips |
| `hairline` | `#DCE0E6` | `220, 224, 230` | borders on cards, fields, panels, buttons |

### Accent — the only action colour
| Token | HEX | RGB | Use |
|---|---|---|---|
| `accent` | `#16624B` | `22, 98, 75` | primary buttons, active nav, links, badges, checks |
| `accent-dark` | `#0F4736` | `15, 71, 54` | hover/active on accent |
| `accent-tint` | `#F1F7F4` | `241, 247, 244` | focus rings, success flash background |
| `accent-line` | `#D6E7DF` | `214, 231, 223` | hover border on quiet controls, success flash border |

### Signal
| Token | HEX | RGB | Use |
|---|---|---|---|
| `lime` | `#CDEB7A` | `205, 235, 122` | glyph colour **on** an accent tile, promo badges. Never a surface, never text on white |
| `amber` | `#B07D12` | `176, 125, 18` | owner crown, Pro marks |
| `amber-tint` | `#FBF3E0` | `251, 243, 224` | background behind those |
| `danger` | `#B42318` | `180, 35, 24` | destructive actions, failed imports, error flash |
| `danger-tint` | `#FDF3F2` | `253, 243, 242` | destructive hover / error flash background |
| `danger-line` | `#EFD5D3` | `239, 213, 211` | borders on the above |

**No dark mode.** No dark values are designed; do not add `dark:` variants.

## Type

| Token | Stack | Weights available |
|---|---|---|
| `--font-sans` (`font-sans`) | `"IBM Plex Sans", system-ui, sans-serif` | 300, 400, 500, 600 (+300/400 italic) |
| `--font-mono` (`font-mono`) | `"IBM Plex Mono", ui-monospace, monospace` | 400, 500 |

Mono is for **numerics only** — times, servings, counts, IDs, quantities.
`ApplicationHelper#mono(value)` wraps a value in a `font-mono` span.

Self-hosted woff2 in `app/assets/fonts/` (`IBMPlexSans-300.woff2` …
`IBMPlexMono-500.woff2`), `@font-face`-declared in
`app/assets/stylesheets/application.css` with `font-display: swap`.

### Scale in use
Sizes are literal pixel values, not Tailwind's `text-sm` scale.

| Size | Where |
|---|---|
| `text-[29px]` / `tracking-[-0.7px]` | desktop page title |
| `text-[23px]` / `tracking-[-0.5px]` | mobile page title |
| `text-[16px]`–`text-[17px]` | dialog and section headings |
| `text-[14.5px]` / `text-[13.5px]` | card titles, rail wordmark |
| `text-[12.5px]` | default body copy — the most common size in the app |
| `text-[11.5px]` | field labels, secondary meta |
| `text-[10px]`–`text-[10.5px]` | mono counts, rail subtitles |
| `text-[9.5px]` / `tracking-[1.1px]` uppercase | `mc-section-label` |

Weights: `font-light` (300) for secondary prose and captions, 400 default,
`font-medium` (500) for emphasis and active rows, `font-semibold` (600) for
titles and the wordmark.

## Radii

| Token | Value | Use |
|---|---|---|
| `rounded-control` | 5px | buttons, chips, nav rows, small icon tiles |
| `rounded-card` | 6px | cards, fields, flash banners |
| `rounded-panel` | 8px | panels, large touch targets |

Outside the scale: `rounded-[7px]` popovers, `rounded-[10px]` dialogs,
`rounded-[12px]` empty-state icon tile, `rounded-full` check circles and
radio dots. Avatars use `rounded-control`, not a circle.

## Elevation

There are no shadow tokens. Flat surfaces are separated by `border-hairline` or
`border-line`. Shadows are written inline and reserved for overlays (the one
exception is a `shadow-sm` on the shopping tile's floating remove button):

| Overlay | Shadow |
|---|---|
| Menu / popover | `shadow-[0_12px_28px_-10px_rgba(20,23,28,0.28)]` |
| Dialog | `shadow-[0_24px_60px_-20px_rgba(20,23,28,0.5)]` |
| Mobile drawer | `shadow-[12px_0_32px_-12px_rgba(20,23,28,0.4)]` |

## Component utilities

Defined as `@utility` rules alongside the tokens:

```css
mc-field          /* w-full rounded-card border-hairline bg-surface px-3 py-2.5 text-[13px],
                     light muted placeholder, focus:border-accent focus:ring-3 ring-accent-tint */
mc-label          /* mb-1.5 block text-[11.5px] font-medium text-body */
mc-section-label  /* text-[9.5px] font-medium tracking-[1.1px] uppercase text-muted */
mc-panel          /* overflow-hidden rounded-panel border-hairline bg-surface */
mc-btn            /* inline-flex items-center gap-1.5 rounded-card px-3.5 py-2.5 text-[12.5px] font-medium */
mc-btn-primary    /* bg-accent text-white, hover accent-dark */
mc-btn-outline    /* bg-surface border-hairline text-ink, hover bg-sunken */
mc-btn-quiet      /* transparent, text-body, hover bg-sunken */
mc-btn-danger     /* bg-surface border-danger-line text-danger, hover bg-danger-tint */
```

`mc-btn` is the base — always pair it with a variant. Widths and extra padding
are layered on (`class="mc-btn mc-btn-primary w-full py-3 text-[13.5px]"`).

## Base layer

```css
html  { background: var(--color-canvas); }
body  { color: var(--color-ink); -webkit-font-smoothing: antialiased; }
:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px; }
```

The focus ring is global — do not write per-component focus styles beyond the
field ring that `mc-field` already carries.

## Layout metrics

| Thing | Value |
|---|---|
| Desktop rail | `w-[196px]`, `bg-rail`, `border-r border-line`, `py-[18px]`, `md:` and up |
| Mobile drawer | `w-[286px]`, same contents, backdrop `bg-ink/45` |
| Mobile app bar | `border-b border-line bg-surface px-3.5 py-[13px]`, `md:hidden` |
| Page padding | `px-4` mobile, `md:px-[30px]` desktop |
| Recipe grid | `grid-cols-2 gap-x-3.5 gap-y-[18px]`, `md:grid-cols-3 md:gap-x-5 md:gap-y-6` |
| Shopping tiles | 2-up mobile, 4-up desktop |
| Dialog | `w-[min(420px,calc(100vw-32px))]` (440px for the wider ones), `m-auto`, `border-0` |
| Shell | `flex h-dvh overflow-hidden`; only `<main>` scrolls |

## Icons

`ApplicationHelper#icon(name, size: 16, stroke: 1.9, **options)` renders a
`lucide-rails` icon at the design stroke and adds `shrink-0`. Sizes in use:
11–13px inline chevrons and checks (with a heavier `stroke: 2.2`–`2.6`), 15–17px
nav and buttons, 19px app-bar controls, 24px empty states.

Colour icons with a text class (`class: "text-muted"`) — the exception is lime
on accent, which needs `style: "color: var(--color-lime)"` since `lime` is not
otherwise used as a foreground.

**No emoji anywhere in the UI.**

## Brand

`ApplicationHelper#brand_mark(size: 22)` renders `app/assets/images/logo.png` —
the leather cookbook shared with the iOS app icon and getmaincourse.com. Sized
by height with `w-auto` so its proportions hold. Favicons come from
`layouts/_favicons` and are copied from the landing site.

Note the mockups in `docs/web-design/` draw the mark as a green square with a
lime fork; the shipped app uses the cookbook PNG instead.

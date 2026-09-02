# iOS redesign: MainCourse design language, native chrome

Date: 2026-09-02

## Goal

Bring the iOS app (`hauptgang-ios/`) onto the design language the web app
adopted on 1 September 2026 (`2026-09-01-web-redesign-design.md`): grey canvas,
one deep-green accent, mono numerics, flat hairline-bordered surfaces. The app
must still feel like a native iOS app, and the same vocabulary must be reusable
by a future native Android client.

The old brown / Lato / Merriweather look is retired on iOS; nothing of it
remains in the app after this work.

## Decisions

| Question | Decision |
|---|---|
| UI typeface | San Francisco via semantic text styles. No bundled sans. All `design: .serif` removed. |
| Numerics | IBM Plex Mono 400 and 500, bundled, via one `Font` helper that scales with Dynamic Type. |
| Colour | Full web token set as named `Color.mc…` statics, used in every custom view. System chrome (nav bars, tab bar, Lists, Forms, alerts, sheets, menus, Liquid Glass) keeps its materials and takes accent as tint. |
| Dark mode | Light only, unchanged. `preferredColorScheme(.light)` stays. No dark variants in any colorset. |
| Icons | SF Symbols, unchanged. No lucide. |
| Recipe card | Keeps the image-fill card with gradient overlay. Recoloured and retyped only. |
| Shape | Flat surfaces with a 1px hairline border, no drop shadows. Radii 8 / 10 / 12 (control / card / panel). |
| Token delivery | One Swift file with hex literals, compiled into the app and the share extension. Not the asset catalogue, except `AccentColor` and the launch-screen colour which the system reads by name. |
| Scope | Main app, onboarding, share extension, splash and launch screen. App icon unchanged. |

Out of scope: dark mode, the web's image-top card anatomy, replacing SF Symbols,
any layout or navigation change, meal plan tab visibility, Android.

## Tokens

`Hauptgang/Utilities/MainCourseTheme.swift` replaces `Color+Theme.swift`. It is
listed in the `ImportRecipeExtension` sources in `project.yml`.

### Colours

`extension Color` statics, hex literals, single value each. Always reference
with the explicit `Color.` prefix (`Color.mcAccent`): bare `.mcAccent` fails to
resolve in `ShapeStyle` positions such as `foregroundStyle` and `tint`.

| Name | Hex | Use on iOS |
|---|---|---|
| `mcCanvas` | `#EEF0F2` | screen backgrounds, List / Form backgrounds, splash, launch screen |
| `mcSurface` | `#FFFFFF` | cards, tiles, login card, custom rows |
| `mcSunken` | `#F5F7F8` | checked shopping tiles, image placeholders, detail meta panel, photo picker tile, invite summary box |
| `mcInk` | `#14171C` | primary text, selected chips |
| `mcBody` | `#5B6570` | secondary text, labels |
| `mcMuted` | `#9AA3AE` | counts, captions, placeholders, empty-state glyphs |
| `mcLine` | `#E3E6EA` | dividers |
| `mcHairline` | `#DCE0E6` | borders on cards, tiles, fields, outline buttons |
| `mcAccent` | `#16624B` | tint, primary buttons, links, checks, success states, leading icons in lists |
| `mcAccentDark` | `#0F4736` | pressed primary button |
| `mcAccentTint` | `#F1F7F4` | step-number tiles, field focus ring |
| `mcAccentLine` | `#D6E7DF` | border on accent-tint surfaces |
| `mcLime` | `#CDEB7A` | signal only: a glyph on an accent tile, a promo badge. Never a surface, never text on white |
| `mcAmber` | `#B07D12` | owner crown, Pro star |
| `mcAmberTint` | `#FBF3E0` | background behind those |
| `mcDanger` | `#B42318` | destructive actions, error banner text and icon, field errors, failed imports |
| `mcDangerTint` | `#FDF3F2` | error banner and error field fill |
| `mcDangerLine` | `#EFD5D3` | error banner and error field border |

Not ported: `rail` (iOS has no rail). Mapping from the old names:

| Old | New |
|---|---|
| `hauptgangPrimary`, `hauptgangSuccess` | `mcAccent` |
| `hauptgangPrimaryHover` | `mcAccentDark` |
| `hauptgangBackground` | `mcCanvas` |
| `hauptgangCard` | `mcSurface` |
| `hauptgangSurfaceRaised` | `mcSunken` |
| `hauptgangBorderSubtle` | `mcHairline` |
| `hauptgangTextPrimary` / `Secondary` / `Muted` | `mcInk` / `mcBody` / `mcMuted` |
| `hauptgangError`, `hauptgangErrorSoft`, `.red` | `mcDanger` |
| `hauptgangAmber`, `.yellow` | `mcAmber` |
| `.green` (extension) | `mcAccent` |
| `.orange` (extension) | `mcAmber` |

### Asset catalogue

- `AccentColor.colorset`: single universal value `#16624B`, dark appearance
  entry removed. This is what tints system controls.
- `LaunchBackground.colorset`: single value `#EEF0F2`. `Info.plist`
  `UILaunchScreen/UIColorName` points at it.
- Deleted: `HauptgangPrimary`, `HauptgangPrimaryHover`, `HauptgangBackground`.
- `AppIcon`, `LaunchLogo`, `LoginLogo` unchanged.

### Type

San Francisco through the semantic styles already in use (`.title2`,
`.headline`, `.subheadline`, `.caption`, …). Rules:

- No `design: .serif` anywhere. Recipe titles are `.title2` semibold; the login
  and onboarding wordmark is `.title` semibold with the brand word in accent.
- Mono is for numerics only: card and row times, prep / cook / servings values,
  the portion scaler number, ingredient quantities, shopping quantities, step
  numbers, member counts. Prose never uses mono; numbers never use SF.
- `Font.mcMono(_ style: Font.TextStyle, weight: Font.Weight = .regular)` wraps
  `Font.custom("IBMPlexMono", size:relativeTo:)` using the default point size
  of the given text style, so it scales with Dynamic Type. `.medium` maps to
  the 500 face, anything else to 400.
- Font files: `Hauptgang/Resources/Fonts/IBMPlexMono-Regular.ttf` and
  `IBMPlexMono-Medium.ttf` (OFL; licence file alongside), registered under
  `UIAppFonts` in the app's `Info.plist`. The extension shows no numerics and
  does not register them.

### Shape and spacing

`enum Theme` keeps `Spacing` (4 / 8 / 16 / 24 / 32 / 48) unchanged.
`CornerRadius` becomes:

| Name | Value | Use |
|---|---|---|
| `Theme.Radius.control` | 8 | buttons, chips, step-number tiles, small image thumbnails |
| `Theme.Radius.card` | 10 | recipe cards, search rows, shopping tiles, fields, banners |
| `Theme.Radius.panel` | 12 | logo tile, login card, detail meta panel, photo picker tile |

Toasts are capsules. Sheets, alerts, menus and Liquid Glass keep system radii.
`Theme.Shadow` is deleted. A flat surface is separated from the canvas by a
1px `mcHairline` stroke. Shadows remain only on overlays: the floating error
banner and the pre-iOS-26 material import chips.

## Shared styles

`Hauptgang/Utilities/MainCourseStyles.swift` (renamed from
`ThemeTextFieldStyle.swift`), also listed in the extension's sources.

- `PrimaryButtonStyle`: accent fill, accent-dark when pressed, white
  `.headline` label, full width, `Theme.Spacing.md` padding, card radius.
  Disabled: accent at 40% opacity. Press animation 0.15s ease-in-out.
- `OutlineButtonStyle` (new): surface fill, 1px hairline border, ink
  `.headline` label, same metrics as primary. Pressed: sunken fill.
- `PuffyButtonStyle` and `.puffyButton()` are deleted.
- `ThemeTextFieldModifier` / `.themeTextField(isError:isGrouped:)`: surface
  fill, 1px hairline border, card radius, ink text, min height 52. Error:
  danger-line border and danger-tint fill. Grouped (inside the login card):
  no border, no radius, clear fill, unchanged behaviour.
- iOS 26 `.glass` / `.glassProminent` buttons and `glassEffect` stay wherever
  they are today and take `Color.mcAccent` as tint.

## Screens

**System containers.** Every `List` and `Form` (settings, cookbook settings,
recipe edit, manage account, edit name, delete account, clipboard preview) keeps
inset-grouped style and system row chrome. Add `.scrollContentBackground(.hidden)`
and a `Color.mcCanvas` background. Row text ink and body, leading icons accent,
Pro star and owner crown amber, destructive rows danger.

**Recipes grid** (`RecipeCardView`, `RecipesView`). Card keeps the image-fill
gradient overlay. Card radius, no shadow, title `.headline` semibold SF, time in
mono. Cards without a photo use the web's placeholder gradient: the same ten
`150deg` two-stop gradients as `RecipesHelper::PLACEHOLDER_GRADIENTS`, chosen by
the recipe id (MD5 of the decimal id, taken as an integer, mod 10) so web and
iOS show the same colour for the same recipe. Title on a placeholder card is
white on the gradient, as on an image. Import overlay unchanged. Empty state:
glyph muted, title ink, copy body. The import-chip material on pre-26 stays.

**Search** (`RecipeSearchView`, `RecipeRowView`, `SearchInputBar`). Search bar
stays on system fills. Rows: surface, hairline, card radius, no shadow, name
ink, time mono body, favourite heart accent, chevron muted.

**Recipe detail** (`RecipeDetailContentView`, `PortionScalerView`,
`RecipeDetailToolbarContent`). Title `.title2` semibold. The prep / cook /
servings panel: sunken fill, hairline border, panel radius; values mono ink,
labels body, icons accent. Step numbers: 22pt square, control radius,
accent-tint fill, mono `.caption` medium digit in accent. Ingredient quantities
mono, names ink. Section headers `.headline` ink. Notes body. Hero image, iOS 26
top gradient and cooking-mode glass button unchanged apart from tint. Loading
and error states: spinner accent, error glyph danger.

**Shopping list** (`ShoppingListView`, `ShoppingListSectionsContent`,
`ShoppingListReviewSheet`). Unchecked tile: surface, hairline, card radius,
name ink, quantity mono body. Checked tile: sunken, hairline, name and quantity
muted with strikethrough. No shadows. Section headers: `.caption2` medium,
uppercase, `tracking(1.1)`, muted, matching the web's section label. Add bar,
review sheet and empty state keep their system materials; empty state text ink
and body.

**Cookbooks and invitations** (`CookbookSettingsView`, `InvitationView`,
`CookbookTitleMenu`). 60pt hero symbols accent; success accent; failure danger.
Member rows: owner crown amber, member icon muted, name ink, role body. Invite
summary box sunken with hairline, control radius. Buttons primary / outline.

**Login and onboarding** (`LoginView`, `OnboardingWelcomeView`,
`OnboardingFlowView`, `OnboardingChip`, `OnboardingQuestionViews`). Canvas
background. Logo tile panel radius. Wordmark `.title` semibold, brand word
accent. Form card surface, hairline, panel radius. Field errors danger
`.caption`. Links accent. Chips: selected is ink fill with white text,
unselected is surface with hairline, control radius, no shadow. Progress dots
accent / hairline. Skip and back text body.

**Banners and toasts** (`ErrorBannerView`, `OfflineToast`). Error banner:
danger-tint fill, danger-line border, danger icon and text, card radius, light
shadow (it floats). Offline toast keeps its glass capsule, text body.

**Share extension** (`ImportRecipeExtension/ImportRecipeView.swift`). Canvas
background, headings ink, copy body, success glyph accent, failure glyph
danger, unsupported-domain glyph amber, buttons `PrimaryButtonStyle`.

**Splash and launch** (`RootView.SplashView`, `Info.plist`). Splash background
canvas. Launch screen colour `LaunchBackground`. `LaunchLogo` unchanged.

**Meal plan** (`MealPlanView`, `MealPlanDayRow`, `MealPlanRecipePicker`). The
tab is hidden but the views compile; mechanical recolour only, using the
mapping table.

## Order of work

1. Add `MainCourseTheme.swift`, the two Plex Mono TTFs and licence, the
   `UIAppFonts` entry, the green `AccentColor`, the `LaunchBackground`
   colorset and the `Info.plist` launch key. Add the theme and styles files to
   the extension target in `project.yml`; run `xcodegen generate`.
2. Rewrite `MainCourseStyles.swift`.
3. Migrate views in groups, compiling after each: recipes grid and card; search
   and row; detail; shopping list; settings and cookbook lists; invitation and
   cookbook empty states; login and onboarding; banners and toast; meal plan;
   share extension.
4. Delete `Color+Theme.swift` and the three brown colorsets. Prove nothing
   references `hauptgang[A-Z]` colour names, `design: .serif`, `Theme.Shadow`,
   `Theme.CornerRadius`, `puffyButton`, or bare `.red` / `.yellow` / `.green` /
   `.orange` colours in views.
5. Docs and skill.

## Verification

- `bin/ios-build` after step 1 and after every group in step 3.
- `bin/ios-test` at the end.
- SwiftLint and SwiftFormat clean, as established in commit `7c77b26`.
- Simulator screenshots through XcodeBuildMCP of recipes, detail, shopping
  list, settings, login and onboarding, compared by eye with the web mockups in
  `docs/web-design/`.
- No new unit tests: the change is visual, and the existing suite covers
  behaviour.

## Docs and skill

- `~/.claude/skills/maincourse-design/SKILL.md`: remove the "web only, iOS not
  redesigned" note. Add an iOS section with the platform rules: SF not Plex
  Sans, SF Symbols not lucide, system chrome with accent tint, radii 8 / 10 /
  12, the `Color.mc…` names, the mono helper, and the rule that the token file
  in the repo is the source of truth for iOS. Retire
  `references/ios-legacy-tokens.md`.
- `hauptgang-ios/AGENTS.md`: replace the `Color.hauptgangPrimary` rule with the
  `Color.mcAccent` equivalent and point at `MainCourseTheme.swift`.
- `docs/web-ui.md` needs no change; it is web only.

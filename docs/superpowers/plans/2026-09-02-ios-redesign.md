# iOS MainCourse Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-skin the iOS app (main app + share extension) in the MainCourse web design language — grey canvas, deep-green accent, San Francisco text with IBM Plex Mono numerics, flat hairline surfaces — while keeping every screen structurally native.

**Architecture:** One new `MainCourseTheme.swift` (hex-literal `Color.mc…` tokens, `Font.mcMono`, `Theme.Spacing`, `Theme.Radius`) is compiled into both the app and the share extension. A mechanical rename migrates every `Color.hauptgang*` / `Theme.CornerRadius.*` usage in one commit, after which the old `Color+Theme.swift` and brown colorsets are deleted. Screen-by-screen tasks then apply the non-mechanical changes (drop serif, drop shadows, mono numerics, placeholder gradients, chip and button restyles). System chrome (nav bars, tab bar, Lists, Forms, sheets, alerts, Liquid Glass) is untouched apart from the accent colour.

**Tech Stack:** SwiftUI (iOS 18 target, iOS 26 glass branches), XcodeGen (`project.yml` → never edit the `.xcodeproj` by hand), SwiftLint (`bin/ios-lint`, strict), SwiftFormat (`bin/ios-format`, maxwidth 120, `--self insert`), `bin/ios-build`, `bin/ios-test`.

**Spec:** `docs/superpowers/specs/2026-09-02-ios-redesign-design.md`

## Global Constraints

- **Light only.** `HauptgangApp.swift` keeps `.preferredColorScheme(.light)`. No dark variants, no `@Environment(\.colorScheme)` branching.
- **Explicit `Color.` prefix** for any token used in a `ShapeStyle` context (`foregroundStyle`, `background(_:in:)`, `fill`, `tint`, `stroke`). Bare `.mcAccent` only compiles where a `Color` is expected (`foregroundColor`).
- **Radii:** `Theme.Radius.control` = 8, `Theme.Radius.card` = 10, `Theme.Radius.panel` = 12. No other radius literals except the 22pt step-number tile and system capsules.
- **IBM Plex Mono is for numerics only** — times, servings, counts, quantities, step numbers. Every other text is San Francisco via semantic styles (`.headline`, `.body`, `.caption`, …). No `design: .serif` anywhere.
- **No drop shadows on flat surfaces.** Cards, chips, tiles, panels separate with a 1px `Color.mcHairline` stroke. Shadows survive only on the floating `ErrorBannerView` and existing Liquid Glass overlays.
- **Accent is the only action colour.** Amber = owner/Pro/warning, danger = destructive/failure. Never bare `.red`, `.yellow`, `.green`, `.orange`.
- **Native chrome stays native.** Do not add custom backgrounds to navigation bars, the tab bar, alerts, sheets or context menus. Lists and Forms only get `.scrollContentBackground(.hidden)` + `.background(Color.mcCanvas)`.
- **Every task ends green:** `bin/ios-format` (writes), `bin/ios-lint` (strict, must print no violations), `bin/ios-build` (must print `** BUILD SUCCEEDED **`). Run them from the repo root.
- **Commit messages** end with `Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C`.
- **Branch:** all work on `ios-redesign` (already exists, contains the spec commit).

---

### Task 1: Theme tokens, Plex Mono fonts, asset catalogue, extension wiring

**Files:**
- Create: `hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift`
- Create: `hauptgang-ios/Hauptgang/Resources/Fonts/IBMPlexMono-Regular.ttf`, `hauptgang-ios/Hauptgang/Resources/Fonts/IBMPlexMono-Medium.ttf`, `hauptgang-ios/Hauptgang/Resources/Fonts/OFL.txt`
- Create: `hauptgang-ios/Hauptgang/Resources/Assets.xcassets/LaunchBackground.colorset/Contents.json`
- Modify: `hauptgang-ios/Hauptgang/Resources/Assets.xcassets/AccentColor.colorset/Contents.json`
- Modify: `hauptgang-ios/Hauptgang/Resources/Info.plist`
- Modify: `hauptgang-ios/Hauptgang/Utilities/Color+Theme.swift` (move `enum Theme` out)
- Modify: `hauptgang-ios/project.yml` (extension sources)

**Interfaces:**
- Produces: `Color.mcCanvas, mcSurface, mcSunken, mcInk, mcBody, mcMuted, mcLine, mcHairline, mcAccent, mcAccentDark, mcAccentTint, mcAccentLine, mcLime, mcAmber, mcAmberTint, mcDanger, mcDangerTint, mcDangerLine` (all `static let … : Color`), `Color.init(mcHex: UInt32)`, `Font.mcMono(_ style: Font.TextStyle = .body, weight: Font.Weight = .regular) -> Font`, `enum Theme { enum Spacing { xs sm md lg xl xxl }; enum Radius { control card panel } }`.
- Consumes: nothing.

- [ ] **Step 1: Download the fonts and licence**

```bash
mkdir -p hauptgang-ios/Hauptgang/Resources/Fonts
curl -sSL -o hauptgang-ios/Hauptgang/Resources/Fonts/IBMPlexMono-Regular.ttf https://github.com/google/fonts/raw/main/ofl/ibmplexmono/IBMPlexMono-Regular.ttf
curl -sSL -o hauptgang-ios/Hauptgang/Resources/Fonts/IBMPlexMono-Medium.ttf https://github.com/google/fonts/raw/main/ofl/ibmplexmono/IBMPlexMono-Medium.ttf
curl -sSL -o hauptgang-ios/Hauptgang/Resources/Fonts/OFL.txt https://github.com/google/fonts/raw/main/ofl/ibmplexmono/OFL.txt
file hauptgang-ios/Hauptgang/Resources/Fonts/*.ttf
fc-scan --format '%{postscriptname}\n' hauptgang-ios/Hauptgang/Resources/Fonts/*.ttf
```

Expected: both `.ttf` files report `TrueType Font data`; `fc-scan` prints `IBMPlexMono-Regular` and `IBMPlexMono-Medium`. If `fc-scan` is missing, skip that line — the names are verified.

- [ ] **Step 2: Create `MainCourseTheme.swift`**

```swift
import SwiftUI

// MARK: - Colour tokens

/// MainCourse design tokens. Hex literals mirror the `@theme` block of
/// `app/assets/tailwind/application.css` in the web app; this file is the iOS
/// source of truth and is compiled into both the app and the share extension.
extension Color {
    // Surfaces
    static let mcCanvas = Color(mcHex: 0xEEF0F2)
    static let mcSurface = Color(mcHex: 0xFFFFFF)
    static let mcSunken = Color(mcHex: 0xF5F7F8)

    // Text
    static let mcInk = Color(mcHex: 0x14171C)
    static let mcBody = Color(mcHex: 0x5B6570)
    static let mcMuted = Color(mcHex: 0x9AA3AE)

    // Lines
    static let mcLine = Color(mcHex: 0xE3E6EA)
    static let mcHairline = Color(mcHex: 0xDCE0E6)

    // Accent — the only action colour
    static let mcAccent = Color(mcHex: 0x16624B)
    static let mcAccentDark = Color(mcHex: 0x0F4736)
    static let mcAccentTint = Color(mcHex: 0xF1F7F4)
    static let mcAccentLine = Color(mcHex: 0xD6E7DF)

    // Signal
    static let mcLime = Color(mcHex: 0xCDEB7A)
    static let mcAmber = Color(mcHex: 0xB07D12)
    static let mcAmberTint = Color(mcHex: 0xFBF3E0)
    static let mcDanger = Color(mcHex: 0xB42318)
    static let mcDangerTint = Color(mcHex: 0xFDF3F2)
    static let mcDangerLine = Color(mcHex: 0xEFD5D3)

    /// Builds an opaque sRGB colour from a `0xRRGGBB` literal.
    init(mcHex hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

// MARK: - Type

extension Font {
    /// IBM Plex Mono, scaled with Dynamic Type relative to `style`.
    /// Use for numerics only: times, servings, counts, quantities, step numbers.
    /// Weights at or above `.medium` use the 500 face; everything else uses 400.
    static func mcMono(_ style: Font.TextStyle = .body, weight: Font.Weight = .regular) -> Font {
        let heavyWeights: Set<Font.Weight> = [.medium, .semibold, .bold, .heavy, .black]
        let name = heavyWeights.contains(weight) ? "IBMPlexMono-Medium" : "IBMPlexMono-Regular"
        return .custom(name, size: Self.mcBaseSize(for: style), relativeTo: style)
    }

    /// Default (Large) point sizes of the iOS text styles, used as the base for scaling.
    private static func mcBaseSize(for style: Font.TextStyle) -> CGFloat {
        switch style {
        case .largeTitle: 34
        case .title: 28
        case .title2: 22
        case .title3: 20
        case .headline: 17
        case .body: 17
        case .callout: 16
        case .subheadline: 15
        case .footnote: 13
        case .caption: 12
        case .caption2: 11
        @unknown default: 17
        }
    }
}

// MARK: - Layout

enum Theme {
    enum Spacing {
        static let xs: CGFloat = 4
        static let sm: CGFloat = 8
        static let md: CGFloat = 16
        static let lg: CGFloat = 24
        static let xl: CGFloat = 32
        static let xxl: CGFloat = 48
    }

    /// Three radii only: controls (buttons, chips, fields' inner controls),
    /// cards (recipe cards, rows, tiles, text fields) and panels (grouped surfaces).
    enum Radius {
        static let control: CGFloat = 8
        static let card: CGFloat = 10
        static let panel: CGFloat = 12
    }
}
```

- [ ] **Step 3: Move `enum Theme` out of `Color+Theme.swift`**

Open `hauptgang-ios/Hauptgang/Utilities/Color+Theme.swift`. Replace the whole `enum Theme { … }` block (it contains `Spacing`, `CornerRadius`, `Shadow`, `ShadowStyle`) with an extension that keeps only the members the old code still references, so the project compiles with both files present until Task 2 deletes this one:

```swift
extension Theme {
    enum CornerRadius {
        static let sm: CGFloat = 4
        static let md: CGFloat = 8
        static let lg: CGFloat = 12
        static let xl: CGFloat = 16
    }

    enum Shadow {
        static let sm = ShadowStyle(color: .black.opacity(0.05), radius: 2, offsetY: 1)
        static let md = ShadowStyle(color: .black.opacity(0.1), radius: 4, offsetY: 2)
    }

    struct ShadowStyle {
        let color: Color
        let radius: CGFloat
        let offsetY: CGFloat
    }
}
```

Delete the `Spacing` enum from this file (it now lives in `MainCourseTheme.swift`). Leave the `extension Color { static let hauptgang… }` block untouched.

- [ ] **Step 4: Asset catalogue — single green accent, new launch background**

Overwrite `hauptgang-ios/Hauptgang/Resources/Assets.xcassets/AccentColor.colorset/Contents.json`:

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x4B",
          "green" : "0x62",
          "red" : "0x16"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

Create `hauptgang-ios/Hauptgang/Resources/Assets.xcassets/LaunchBackground.colorset/Contents.json`:

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xF2",
          "green" : "0xF0",
          "red" : "0xEE"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 5: Info.plist — launch colour and font registration**

In `hauptgang-ios/Hauptgang/Resources/Info.plist`, inside the `UILaunchScreen` dict change

```xml
<key>UIColorName</key>
<string>HauptgangBackground</string>
```

to

```xml
<key>UIColorName</key>
<string>LaunchBackground</string>
```

and add, as a top-level key (next to `UILaunchScreen`):

```xml
<key>UIAppFonts</key>
<array>
    <string>IBMPlexMono-Regular.ttf</string>
    <string>IBMPlexMono-Medium.ttf</string>
</array>
```

XcodeGen flattens files under `Resources/` into the bundle root, so bare filenames are correct. Step 8 verifies this.

- [ ] **Step 6: project.yml — compile the theme into the share extension**

In `hauptgang-ios/project.yml`, in `targets.ImportRecipeExtension.sources`, add one entry after the existing `Hauptgang/Utilities/…` entries:

```yaml
      - path: Hauptgang/Utilities/MainCourseTheme.swift
```

(Match the indentation and form of the neighbouring entries; some are `- Hauptgang/...` strings, some are `- path:` maps — copy whichever form the neighbours use.)

- [ ] **Step 7: Regenerate the project and build**

```bash
cd hauptgang-ios && xcodegen generate && cd ..
bin/ios-format && bin/ios-lint && bin/ios-build
```

Expected: `xcodegen` prints `Created project`; lint prints no violations; build prints `** BUILD SUCCEEDED **`.

- [ ] **Step 8: Verify the fonts landed in the bundle**

```bash
APP=$(find ~/Library/Developer/Xcode/DerivedData -path '*Debug-iphonesimulator/Hauptgang.app' -maxdepth 6 | head -1)
ls "$APP" | grep -i plex
plutil -p "$APP/Info.plist" | grep -A3 UIAppFonts
```

Expected: both `IBMPlexMono-*.ttf` listed at the bundle root and `UIAppFonts` present. If the TTFs are nested (e.g. under `Fonts/`), change the `UIAppFonts` strings in Info.plist to `Fonts/IBMPlexMono-Regular.ttf` etc. and rebuild.

- [ ] **Step 9: Commit**

```bash
git add hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift hauptgang-ios/Hauptgang/Utilities/Color+Theme.swift hauptgang-ios/Hauptgang/Resources hauptgang-ios/project.yml
git commit -m "feat(ios): add MainCourse colour, type and radius tokens

Adds MainCourseTheme.swift (hex tokens, Font.mcMono, Theme.Radius) shared
with the share extension, bundles IBM Plex Mono 400/500, flips AccentColor
to the deep green and adds a LaunchBackground colorset.

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

(`Hauptgang.xcodeproj` is git-ignored; XcodeGen regenerates it from `project.yml`.)

---

### Task 2: Mechanical rename of every legacy token, delete the old theme

**Files:**
- Modify: every `*.swift` under `hauptgang-ios/` that references `hauptgang[A-Z]`, `Theme.CornerRadius`, or `Theme.Shadow`
- Delete: `hauptgang-ios/Hauptgang/Utilities/Color+Theme.swift`
- Delete: `hauptgang-ios/Hauptgang/Resources/Assets.xcassets/HauptgangPrimary.colorset`, `HauptgangPrimaryHover.colorset`, `HauptgangBackground.colorset`

**Interfaces:**
- Consumes: the `Color.mc*` statics and `Theme.Radius` from Task 1.
- Produces: a codebase with zero `hauptgang*` colour references. Later tasks assume the mapping below has already happened.

Token mapping (from the spec):

| Old | New |
|---|---|
| `hauptgangPrimaryHover` | `mcAccentDark` |
| `hauptgangPrimary` | `mcAccent` |
| `hauptgangSuccess` | `mcAccent` |
| `hauptgangBackground` | `mcCanvas` |
| `hauptgangCard` | `mcSurface` |
| `hauptgangSurfaceRaised` | `mcSunken` |
| `hauptgangBorderSubtle` | `mcHairline` |
| `hauptgangTextPrimary` | `mcInk` |
| `hauptgangTextSecondary` | `mcBody` |
| `hauptgangTextMuted` | `mcMuted` |
| `hauptgangErrorSoft` | `mcDanger` |
| `hauptgangError` | `mcDanger` |
| `hauptgangAmber` | `mcAmber` |
| `Theme.CornerRadius.sm` (4) | `Theme.Radius.control` |
| `Theme.CornerRadius.md` (8) | `Theme.Radius.card` |
| `Theme.CornerRadius.lg` (12) | `Theme.Radius.card` |
| `Theme.CornerRadius.xl` (16) | `Theme.Radius.panel` |

- [ ] **Step 1: Run the rename**

```bash
FILES=$(grep -rlE 'hauptgang[A-Z]|Theme\.CornerRadius' hauptgang-ios --include='*.swift' | grep -v 'Color+Theme.swift')
echo "$FILES"
sed -i '' -E \
  -e 's/hauptgangPrimaryHover/mcAccentDark/g' \
  -e 's/hauptgangPrimary/mcAccent/g' \
  -e 's/hauptgangSuccess/mcAccent/g' \
  -e 's/hauptgangBackground/mcCanvas/g' \
  -e 's/hauptgangCard/mcSurface/g' \
  -e 's/hauptgangSurfaceRaised/mcSunken/g' \
  -e 's/hauptgangBorderSubtle/mcHairline/g' \
  -e 's/hauptgangTextPrimary/mcInk/g' \
  -e 's/hauptgangTextSecondary/mcBody/g' \
  -e 's/hauptgangTextMuted/mcMuted/g' \
  -e 's/hauptgangErrorSoft/mcDanger/g' \
  -e 's/hauptgangError/mcDanger/g' \
  -e 's/hauptgangAmber/mcAmber/g' \
  -e 's/Theme\.CornerRadius\.sm/Theme.Radius.control/g' \
  -e 's/Theme\.CornerRadius\.md/Theme.Radius.card/g' \
  -e 's/Theme\.CornerRadius\.lg/Theme.Radius.card/g' \
  -e 's/Theme\.CornerRadius\.xl/Theme.Radius.panel/g' \
  $FILES
```

Under zsh, if `--include` errors, use `grep -rlE 'hauptgang[A-Z]|Theme\.CornerRadius' hauptgang-ios | grep '\.swift$' | grep -v 'Color+Theme.swift'` instead.

- [ ] **Step 2: Remove every `Theme.Shadow` usage**

```bash
grep -rn 'Theme\.Shadow' hauptgang-ios | grep '\.swift$' | grep -v 'Color+Theme.swift'
```

For each hit, delete the whole `.shadow(color: Theme.Shadow.sm.color, radius: Theme.Shadow.sm.radius, y: Theme.Shadow.sm.offsetY)` modifier call (it spans four lines). Expected hits: `RecipeRowView.swift`, `RecipeCardView.swift`. The literal `.shadow(...)` calls in `OnboardingChip.swift`, `ShoppingListSectionsContent.swift` and `ErrorBannerView.swift` are handled by the screen tasks that own those files (Tasks 8, 6 and 4).

- [ ] **Step 3: Delete the legacy theme file and colorsets**

```bash
git rm hauptgang-ios/Hauptgang/Utilities/Color+Theme.swift
git rm -r hauptgang-ios/Hauptgang/Resources/Assets.xcassets/HauptgangPrimary.colorset \
          hauptgang-ios/Hauptgang/Resources/Assets.xcassets/HauptgangPrimaryHover.colorset \
          hauptgang-ios/Hauptgang/Resources/Assets.xcassets/HauptgangBackground.colorset
grep -rn 'Color+Theme' hauptgang-ios/project.yml || echo "not in project.yml"
```

If `project.yml` listed `Color+Theme.swift` in the extension sources, remove that line.

- [ ] **Step 4: Guard grep, regenerate, build**

```bash
grep -rnE 'hauptgang[A-Z]|Theme\.CornerRadius|Theme\.Shadow' hauptgang-ios | grep '\.swift$'
cd hauptgang-ios && xcodegen generate && cd ..
bin/ios-format && bin/ios-lint && bin/ios-build
```

Expected: the grep prints nothing; build succeeds. If the build fails with "Cannot infer contextual base" on a bare `.mcX`, prefix it with `Color.`.

- [ ] **Step 5: Run the unit tests**

```bash
bin/ios-test 2>&1 | tail -20
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add -A hauptgang-ios
git commit -m "refactor(ios): migrate every view to the MainCourse tokens

Mechanical rename of Color.hauptgang* and Theme.CornerRadius.* to the new
tokens, drops Theme.Shadow from flat surfaces, deletes Color+Theme.swift and
the brown colorsets.

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

---

### Task 3: Shared styles — primary and outline buttons, text fields

**Files:**
- Rename: `hauptgang-ios/Hauptgang/Utilities/ThemeTextFieldStyle.swift` → `hauptgang-ios/Hauptgang/Utilities/MainCourseStyles.swift`
- Modify: `hauptgang-ios/project.yml` (extension sources), `hauptgang-ios/Hauptgang/Views/LoginView.swift`, `hauptgang-ios/Hauptgang/Views/Onboarding/OnboardingWelcomeView.swift`, `hauptgang-ios/Hauptgang/Views/Onboarding/OnboardingFlowView.swift` (callers of `.puffyButton()`)

**Interfaces:**
- Consumes: `Color.mc*`, `Theme.Spacing`, `Theme.Radius`.
- Produces: `PrimaryButtonStyle`, `OutlineButtonStyle` (both `ButtonStyle`), `View.primaryButton()`, `View.outlineButton()`, `View.themeTextField(isError: Bool = false, isGrouped: Bool = false)`. `PuffyButtonStyle` and `.puffyButton()` no longer exist.

- [ ] **Step 1: Rename the file and rewrite it**

```bash
git mv hauptgang-ios/Hauptgang/Utilities/ThemeTextFieldStyle.swift hauptgang-ios/Hauptgang/Utilities/MainCourseStyles.swift
```

Replace the file's contents with:

```swift
import SwiftUI

// MARK: - Text fields

/// Standalone fields sit on a surface card with a hairline border; grouped
/// fields (stacked inside a shared card) draw no background of their own.
struct ThemeTextFieldModifier: ViewModifier {
    var isError: Bool = false
    var isGrouped: Bool = false

    func body(content: Content) -> some View {
        let radius = self.isGrouped ? 0 : Theme.Radius.card
        return content
            .textFieldStyle(.plain)
            .padding(.horizontal, Theme.Spacing.md)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 52, alignment: .center)
            .background(self.fillColor)
            .clipShape(.rect(cornerRadius: radius))
            .overlay {
                if !self.isGrouped {
                    RoundedRectangle(cornerRadius: radius)
                        .stroke(self.isError ? Color.mcDangerLine : Color.mcHairline, lineWidth: 1)
                }
            }
            .contentShape(.rect(cornerRadius: radius))
    }

    private var fillColor: Color {
        if self.isError {
            return Color.mcDangerTint
        }
        return self.isGrouped ? Color.clear : Color.mcSurface
    }
}

// MARK: - Buttons

/// Solid accent button: the single primary action on a screen.
struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(Theme.Spacing.md)
            .background(self.fill(isPressed: configuration.isPressed))
            .clipShape(.rect(cornerRadius: Theme.Radius.card))
            .animation(.easeInOut(duration: 0.15), value: configuration.isPressed)
    }

    private func fill(isPressed: Bool) -> Color {
        guard self.isEnabled else {
            return Color.mcAccent.opacity(0.4)
        }
        return isPressed ? Color.mcAccentDark : Color.mcAccent
    }
}

/// Surface button with a hairline border: secondary actions next to a primary one.
struct OutlineButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundColor(Color.mcInk)
            .frame(maxWidth: .infinity)
            .padding(Theme.Spacing.md)
            .background(configuration.isPressed ? Color.mcSunken : Color.mcSurface)
            .clipShape(.rect(cornerRadius: Theme.Radius.card))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.card)
                    .stroke(Color.mcHairline, lineWidth: 1)
            }
            .opacity(self.isEnabled ? 1 : 0.4)
            .animation(.easeInOut(duration: 0.15), value: configuration.isPressed)
    }
}

// MARK: - View helpers

extension View {
    func themeTextField(isError: Bool = false, isGrouped: Bool = false) -> some View {
        self.modifier(ThemeTextFieldModifier(isError: isError, isGrouped: isGrouped))
    }

    func primaryButton() -> some View {
        self.buttonStyle(PrimaryButtonStyle())
    }

    func outlineButton() -> some View {
        self.buttonStyle(OutlineButtonStyle())
    }
}
```

- [ ] **Step 2: Remove every `.puffyButton()` call**

```bash
grep -rn 'puffyButton\|PuffyButtonStyle' hauptgang-ios | grep '\.swift$'
```

For each hit (expected: `LoginView.swift`, `OnboardingWelcomeView.swift`, `OnboardingFlowView.swift`) delete the `.puffyButton()` line. Where it was chained directly after `.primaryButton()`, the primary style alone remains. Where a button had only `.puffyButton()`, replace with `.primaryButton()`.

- [ ] **Step 3: Add the styles file to the share extension**

In `hauptgang-ios/project.yml`, `targets.ImportRecipeExtension.sources`, add next to the `MainCourseTheme.swift` entry:

```yaml
      - path: Hauptgang/Utilities/MainCourseStyles.swift
```

If the extension sources previously listed `ThemeTextFieldStyle.swift`, remove that entry.

- [ ] **Step 4: Regenerate, lint, build**

```bash
cd hauptgang-ios && xcodegen generate && cd ..
bin/ios-format && bin/ios-lint && bin/ios-build
```

Expected: `** BUILD SUCCEEDED **`, no lint violations.

- [ ] **Step 5: Commit**

```bash
git add -A hauptgang-ios
git commit -m "feat(ios): MainCourse button and text-field styles

Renames ThemeTextFieldStyle.swift to MainCourseStyles.swift, restyles
PrimaryButtonStyle (accent / accent-dark, 40% when disabled), adds
OutlineButtonStyle, gives fields a hairline border and danger tint on
error, and removes PuffyButtonStyle.

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

---

### Task 4: Recipes grid — card placeholder gradients, mono times, error banners

**Files:**
- Create: `hauptgang-ios/Hauptgang/Utilities/RecipePlaceholderGradient.swift`
- Modify: `hauptgang-ios/Hauptgang/Views/RecipeCardView.swift`, `hauptgang-ios/Hauptgang/Views/RecipesView.swift`, `hauptgang-ios/Hauptgang/Views/ErrorBannerView.swift`, `hauptgang-ios/Hauptgang/Views/OfflineToast.swift`
- Test: `hauptgang-ios/HauptgangTests/Utilities/RecipePlaceholderGradientTests.swift`

**Interfaces:**
- Produces: `enum RecipePlaceholderGradient { static let stops: [(start: Color, end: Color)]; static func index(for key: String) -> Int; static func gradient(for key: String) -> LinearGradient }`.
- Consumes: `Color(mcHex:)`, `Font.mcMono`, `Theme.Radius`.

- [ ] **Step 1: Write the failing test**

Create `hauptgang-ios/HauptgangTests/Utilities/RecipePlaceholderGradientTests.swift`:

```swift
@testable import Hauptgang
import XCTest

final class RecipePlaceholderGradientTests: XCTestCase {
    func testIndexIsDeterministicAndInRange() {
        let first = RecipePlaceholderGradient.index(for: "42")
        let second = RecipePlaceholderGradient.index(for: "42")
        XCTAssertEqual(first, second)
        XCTAssertTrue((0..<RecipePlaceholderGradient.stops.count).contains(first))
    }

    func testIndexMatchesWebHelper() {
        // Verified with: ruby -rdigest -e 'puts Digest::MD5.hexdigest("1").to_i(16) % 10'  => 1
        XCTAssertEqual(RecipePlaceholderGradient.index(for: "1"), 1)
        // ruby -rdigest -e 'puts Digest::MD5.hexdigest("7").to_i(16) % 10'  => 5
        XCTAssertEqual(RecipePlaceholderGradient.index(for: "7"), 5)
    }
}
```

The test target uses XCTest (see `hauptgang-ios/HauptgangTests/ViewModels/RecipeViewModelTests.swift`). Put the new file under `hauptgang-ios/HauptgangTests/Utilities/`.

- [ ] **Step 2: Run the test to see it fail**

```bash
bin/ios-test 2>&1 | grep -E 'RecipePlaceholderGradient|error:' | head
```

Expected: a compile error `cannot find 'RecipePlaceholderGradient' in scope`.

- [ ] **Step 3: Implement the gradient helper**

Create `hauptgang-ios/Hauptgang/Utilities/RecipePlaceholderGradient.swift`:

```swift
import CryptoKit
import Foundation
import SwiftUI

/// Deterministic placeholder gradient for recipes without a photo. Mirrors
/// `RecipesHelper::PLACEHOLDER_GRADIENTS` and `placeholder_gradient_for` in the
/// web app so the same recipe gets the same colours on every platform.
enum RecipePlaceholderGradient {
    static let stops: [(start: Color, end: Color)] = [
        (Color(mcHex: 0xC9B08F), Color(mcHex: 0xA2794F)),
        (Color(mcHex: 0xBFCFA8), Color(mcHex: 0x7E9560)),
        (Color(mcHex: 0xDEC0A0), Color(mcHex: 0xB8834F)),
        (Color(mcHex: 0xC8BBA6), Color(mcHex: 0x94795C)),
        (Color(mcHex: 0xDCBCAD), Color(mcHex: 0xA96450)),
        (Color(mcHex: 0xD0CABB), Color(mcHex: 0x99907C)),
        (Color(mcHex: 0xDED6C2), Color(mcHex: 0xADA283)),
        (Color(mcHex: 0xC6A8B4), Color(mcHex: 0x8E5F72)),
        (Color(mcHex: 0xE3CE9C), Color(mcHex: 0xC09A44)),
        (Color(mcHex: 0xB7C9A4), Color(mcHex: 0x6F8757)),
    ]

    /// `MD5(key)` read as a big-endian integer, modulo the number of gradients —
    /// the same arithmetic as Ruby's `hexdigest.to_i(16) % 10`.
    static func index(for key: String) -> Int {
        let digest = Insecure.MD5.hash(data: Data(key.utf8))
        return digest.reduce(0) { ($0 * 256 + Int($1)) % self.stops.count }
    }

    /// The web's `linear-gradient(150deg, start, end)`, approximated as a
    /// top-left-ish to bottom-right-ish sweep.
    static func gradient(for key: String) -> LinearGradient {
        let pair = self.stops[self.index(for: key)]
        return LinearGradient(
            colors: [pair.start, pair.end],
            startPoint: UnitPoint(x: 0.25, y: 0),
            endPoint: UnitPoint(x: 0.75, y: 1)
        )
    }
}
```

- [ ] **Step 4: Run the tests**

```bash
bin/ios-test 2>&1 | grep -E 'RecipePlaceholderGradient|TEST (SUCCEEDED|FAILED)'
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 5: Restyle `RecipeCardView`**

In `hauptgang-ios/Hauptgang/Views/RecipeCardView.swift` make these changes (read the file first; the structure — image fill, dark gradient overlay, title + time at the bottom — stays):

1. Replace the no-image branch's `solidBackgroundView` (the flat `Color.mcSurface`/formerly `hauptgangCard` fill) with the placeholder gradient, and give it the same dark overlay the photo branch uses so the white text is always legible:

```swift
private var placeholderBackground: some View {
    RecipePlaceholderGradient.gradient(for: String(self.recipe.id))
        .overlay(self.textOverlayGradient)
}
```

Where `textOverlayGradient` is the existing black-to-clear `LinearGradient` used over the photo — extract it into a private computed property if it is inline today.

2. Title and time are white in both branches. Remove any `hasImage ? .white : Color.mcInk`-style conditionals; use `.white` and `.white.opacity(0.85)`.

3. Title font: replace `.font(.system(.headline, design: .serif))` (or whatever serif variant is there) with `.font(.headline)`. Add `.fontWeight(.semibold)` if not already implied.

4. Time text (the `clock` label with formatted minutes): `.font(.mcMono(.caption))`.

5. Card clipping: `.clipShape(.rect(cornerRadius: Theme.Radius.card))` (Task 2 already mapped this; confirm). No `.shadow` remains. Do not add a hairline stroke — the photo/gradient fill gives the card its edge.

- [ ] **Step 6: `RecipesView` empty state and overlay**

In `hauptgang-ios/Hauptgang/Views/RecipesView.swift`:

- Empty-state text: title `.font(.headline)` `Color.mcInk`, body `.font(.subheadline)` `Color.mcBody`, icon `Color.mcMuted`. Remove any `design: .serif`.
- `ImportingOverlayBackground`: radius `Theme.Radius.panel` in both the glass and legacy branches (the legacy branch may use `.regularMaterial` — keep the material, change only the radius).
- Screen background is `Color.mcCanvas` (mapped in Task 2; confirm the `ScrollView` has `.background(Color.mcCanvas)` or the containing view does).

- [ ] **Step 7: `ErrorBannerView` and `OfflineToast`**

`ErrorBannerView.swift`:

```swift
.background(Color.mcDangerTint)
.clipShape(.rect(cornerRadius: Theme.Radius.card))
.overlay {
    RoundedRectangle(cornerRadius: Theme.Radius.card)
        .stroke(Color.mcDangerLine, lineWidth: 1)
}
.shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 2)
```

Icon and title `Color.mcDanger`; secondary text (URL or reason) `Color.mcBody`; dismiss `xmark` `Color.mcMuted`. Remove `design: .serif` if present.

`OfflineToast.swift`: text `Color.mcBody`, icon `Color.mcMuted`; keep its existing material background.

- [ ] **Step 8: Lint, build, commit**

```bash
bin/ios-format && bin/ios-lint && bin/ios-build
git add -A hauptgang-ios
git commit -m "feat(ios): restyle recipe cards, grid and banners

Recipe cards keep the image-fill layout but drop the serif title and
shadow, set times in Plex Mono and use the web's deterministic placeholder
gradients when a recipe has no photo. Error banners move to the danger tint.

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

---

### Task 5: Recipe detail — duration panel, ingredient rows, step numbers, portion scaler

**Files:**
- Modify: `hauptgang-ios/Hauptgang/Views/RecipeDetailContentView.swift`, `hauptgang-ios/Hauptgang/Views/PortionScalerView.swift`, `hauptgang-ios/Hauptgang/Views/RecipeDetailView.swift`, `hauptgang-ios/Hauptgang/Views/RecipeDetailToolbarContent.swift`

**Interfaces:**
- Consumes: `Font.mcMono`, `Color.mc*`, `Theme.Radius`.

- [ ] **Step 1: Duration panel**

In `RecipeDetailContentView.swift`, the `durationCard` container becomes a sunken panel with a hairline:

```swift
.padding(Theme.Spacing.md)
.background(Color.mcSunken)
.clipShape(.rect(cornerRadius: Theme.Radius.panel))
.overlay {
    RoundedRectangle(cornerRadius: Theme.Radius.panel)
        .stroke(Color.mcHairline, lineWidth: 1)
}
```

Remove any `.shadow` on it. Inside `durationItem(...)`: icon `Color.mcMuted`, label `.font(.caption)` `Color.mcMuted`, value `.font(.mcMono(.subheadline, weight: .medium))` `Color.mcInk`.

- [ ] **Step 2: Section headings**

"Ingredients" / "Instructions" (or "Method") headings: `.font(.headline)` `Color.mcInk`. Remove `design: .serif`. The recipe title (if rendered in this view rather than the nav bar): `.font(.title2).fontWeight(.semibold)` `Color.mcInk`, no serif.

- [ ] **Step 3: Ingredient rows**

In `IngredientRow`: quantity/unit text `.font(.mcMono(.body, weight: .medium))` `Color.mcInk`; ingredient name `.font(.body)` `Color.mcInk`; any leading accent dot/circle becomes `Color.mcMuted`. Checked/struck-through state (if any) uses `Color.mcMuted`.

- [ ] **Step 4: Step numbers**

Replace the step-number view (currently a filled accent circle with a white digit, or similar) with the web's tile:

```swift
Text("\(index + 1)")
    .font(.mcMono(.caption, weight: .medium))
    .foregroundColor(Color.mcAccent)
    .frame(width: 22, height: 22)
    .background(Color.mcAccentTint, in: RoundedRectangle(cornerRadius: 6))
```

Step body text `.font(.body)` `Color.mcInk`.

- [ ] **Step 5: Cooking-mode toggle (legacy branch) and `PressDownButtonStyle`**

The pre-iOS-26 capsule for "Cooking mode": inactive `Color.mcSurface` fill, `Color.mcHairline` stroke, `Color.mcInk` label; active `Color.mcAccentTint` fill, `Color.mcAccentLine` stroke, `Color.mcAccent` label. Keep the capsule shape (system control). The iOS 26 glass branch is untouched. Mark `PressDownButtonStyle` `private` if it is not already.

- [ ] **Step 6: Portion scaler**

In `PortionScalerView.swift`: the servings number `.font(.mcMono(.headline, weight: .medium))` `Color.mcInk`; "servings" label `.font(.caption)` `Color.mcMuted`; the ± buttons keep their system styling with `Color.mcAccent` tint; if a custom pill background exists, it is `Color.mcSurface` with `Color.mcHairline` stroke and `Theme.Radius.card`.

- [ ] **Step 7: `RecipeDetailView` and toolbar**

`RecipeDetailView.swift`: any `.background(Color.mcCanvas)` stays; ensure no leftover serif in the header; hero image corner radius (if rounded) `Theme.Radius.card`. `RecipeDetailToolbarContent.swift`: buttons use `.tint(Color.mcAccent)` only where a tint was already set; nothing else changes.

- [ ] **Step 8: Lint, build, commit**

```bash
bin/ios-format && bin/ios-lint && bin/ios-build
git add -A hauptgang-ios
git commit -m "feat(ios): restyle recipe detail

Duration panel becomes a sunken hairline panel with mono values, ingredient
quantities and the servings count are set in Plex Mono, step numbers use the
web's accent-tint tile, serif headings are gone.

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

---

### Task 6: Shopping list — tiles, section labels, legacy controls

**Files:**
- Modify: `hauptgang-ios/Hauptgang/Views/ShoppingListSectionsContent.swift`, `hauptgang-ios/Hauptgang/Views/ShoppingListView.swift`, `hauptgang-ios/Hauptgang/Views/ShoppingListReviewSheet.swift` (only if it has custom colours — check with grep)

- [ ] **Step 1: Item tiles**

In `ShoppingListSectionsContent.swift`, `ShoppingListItemTile`:

```swift
.background(self.item.isChecked ? Color.mcSunken : Color.mcSurface)
.clipShape(.rect(cornerRadius: Theme.Radius.card))
.overlay {
    RoundedRectangle(cornerRadius: Theme.Radius.card)
        .stroke(Color.mcHairline, lineWidth: 1)
}
```

(Use whatever the checked-state property is actually called.) No `.shadow`. Checkbox: unchecked `circle` `Color.mcMuted`; checked `checkmark.circle.fill` `Color.mcAccent`. Item name `.font(.body)`, `Color.mcInk` unchecked, `Color.mcMuted` with strikethrough when checked. Quantity, if displayed separately from the name, `.font(.mcMono(.subheadline))` `Color.mcBody`.

- [ ] **Step 2: Section headers**

Section header text becomes the web's section label:

```swift
Text(section.title)
    .font(.caption2.weight(.medium))
    .textCase(.uppercase)
    .tracking(1.1)
    .foregroundColor(Color.mcMuted)
```

Remove any serif or `.headline` styling on these headers.

- [ ] **Step 3: `ShoppingListView`**

- Screen background `Color.mcCanvas` (confirm after Task 2).
- Legacy (pre-26) "Remove all" / "Clear checked" capsule: `Color.mcSurface` fill, `Color.mcHairline` stroke, `Color.mcDanger` label for the destructive one, `Color.mcInk` otherwise. Glass branch untouched.
- Empty state: icon `Color.mcMuted`, title `.font(.headline)` `Color.mcInk`, body `.font(.subheadline)` `Color.mcBody`; no serif.

- [ ] **Step 4: `ShoppingListReviewSheet`**

```bash
grep -n 'Color\.\|\.font(' hauptgang-ios/Hauptgang/Views/ShoppingListReviewSheet.swift
```

If it uses only system styling, leave it. If it has a custom row background, apply the tile treatment from Step 1. Remove any serif.

- [ ] **Step 5: Lint, build, commit**

```bash
bin/ios-format && bin/ios-lint && bin/ios-build
git add -A hauptgang-ios
git commit -m "feat(ios): restyle the shopping list

Tiles are flat hairline cards, checked tiles sink, section headers use the
uppercase section-label style, and legacy controls lose the brown palette.

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

---

### Task 7: Lists and Forms — settings, cookbook settings, edit, account sheets

**Files:**
- Modify: `hauptgang-ios/Hauptgang/Views/SettingsView.swift`, `hauptgang-ios/Hauptgang/Views/CookbookSettingsView.swift`, `hauptgang-ios/Hauptgang/Views/RecipeEditView.swift`, `hauptgang-ios/Hauptgang/Views/ManageAccountView.swift`, `hauptgang-ios/Hauptgang/Views/EditNameView.swift`, `hauptgang-ios/Hauptgang/Views/DeleteAccountConfirmationView.swift`, `hauptgang-ios/Hauptgang/Views/ClipboardPreviewSheet.swift`

- [ ] **Step 1: Canvas behind every List and Form**

In each file above, find every top-level `List { … }` or `Form { … }` and append, directly after its closing brace and before any `.navigationTitle`:

```swift
.scrollContentBackground(.hidden)
.background(Color.mcCanvas)
```

Skip a List that already has these two modifiers. Do not change list styles (`.insetGrouped` stays).

- [ ] **Step 2: Replace system colour literals**

```bash
grep -nE '\.(red|yellow|green|orange)\b|Color\.(red|yellow|green|orange)\b' hauptgang-ios/Hauptgang/Views/SettingsView.swift hauptgang-ios/Hauptgang/Views/CookbookSettingsView.swift hauptgang-ios/Hauptgang/Views/RecipeEditView.swift hauptgang-ios/Hauptgang/Views/ManageAccountView.swift hauptgang-ios/Hauptgang/Views/EditNameView.swift hauptgang-ios/Hauptgang/Views/DeleteAccountConfirmationView.swift hauptgang-ios/Hauptgang/Views/ClipboardPreviewSheet.swift hauptgang-ios/Hauptgang/Views/MealPlanDayRow.swift
```

For each hit: `.yellow` (owner crown, Pro badge) → `Color.mcAmber`; `.red` on custom text or icons → `Color.mcDanger`; `.green` → `Color.mcAccent`; `.orange` → `Color.mcAmber`. Leave `Button(role: .destructive)` alone — the system draws it red and that is correct native behaviour.

- [ ] **Step 3: Per-file specifics**

- `SettingsView.swift`: the signed-in user header (avatar initials tile + name) — tile `Color.mcAccent` fill with white initials, `Theme.Radius.control`; name `.font(.headline)` `Color.mcInk`; email `.font(.subheadline)` `Color.mcBody`. Version/footer text `Color.mcMuted`.
- `CookbookSettingsView.swift`: the invite-link box (around the share/copy link) → `Color.mcSunken` fill, `Color.mcHairline` stroke, `Theme.Radius.control`, link text `.font(.mcMono(.footnote))` `Color.mcInk`. Member rows: owner crown `Color.mcAmber`. The "create cookbook" Form gets the canvas treatment from Step 1.
- `RecipeEditView.swift`: image picker placeholder → `Color.mcSunken` with `Color.mcHairline` stroke, `Theme.Radius.card`; selected image clipped to `Theme.Radius.card`; any inline validation text `Color.mcDanger`.
- `ManageAccountView.swift`, `EditNameView.swift`, `DeleteAccountConfirmationView.swift`: canvas treatment; explanatory copy `Color.mcBody`; the delete confirmation's warning text `Color.mcDanger`.
- `ClipboardPreviewSheet.swift`: preview URL text `.font(.mcMono(.footnote))` `Color.mcBody`; canvas treatment behind the List/Form; buttons use `.primaryButton()` / `.outlineButton()` if they are custom-styled, otherwise leave system buttons alone.

- [ ] **Step 4: Lint, build, commit**

```bash
bin/ios-format && bin/ios-lint && bin/ios-build
git add -A hauptgang-ios
git commit -m "feat(ios): canvas behind Lists and Forms, amber and danger semantics

Settings, cookbook settings, recipe edit and the account sheets sit on the
canvas colour and swap bare system reds and yellows for the danger and
amber tokens.

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

---

### Task 8: Login and onboarding

**Files:**
- Modify: `hauptgang-ios/Hauptgang/Views/LoginView.swift`, `hauptgang-ios/Hauptgang/Views/Onboarding/OnboardingChip.swift`, `hauptgang-ios/Hauptgang/Views/Onboarding/OnboardingFlowView.swift`, `hauptgang-ios/Hauptgang/Views/Onboarding/OnboardingWelcomeView.swift`, `hauptgang-ios/Hauptgang/Views/Onboarding/OnboardingQuestionViews.swift` (if it holds `OnboardingQuestionHeader`; otherwise wherever that type lives — `grep -rn 'struct OnboardingQuestionHeader' hauptgang-ios`)

- [ ] **Step 1: Login tagline**

In `LoginView.swift` replace the serif tagline (the `Text("Cook something ") + Text("delicious")… + Text(" today")` composition, or however it is built) with:

```swift
(Text("Cook something ")
    + Text("delicious").foregroundColor(Color.mcAccent)
    + Text(" today"))
    .font(.title2)
    .fontWeight(.semibold)
    .foregroundColor(Color.mcInk)
    .multilineTextAlignment(.center)
```

No italic, no underline, no serif.

- [ ] **Step 2: Login logo tile and form card**

- Logo tile (the rounded square behind `LoginLogo`): `Theme.Radius.panel`, `Color.mcSurface` fill, `Color.mcHairline` 1px stroke, no shadow.
- Form card (the container around the email/password fields and the sign-in button):

```swift
.padding(Theme.Spacing.lg)
.background(Color.mcSurface)
.clipShape(.rect(cornerRadius: Theme.Radius.panel))
.overlay {
    RoundedRectangle(cornerRadius: Theme.Radius.panel)
        .stroke(Color.mcHairline, lineWidth: 1)
}
```

- Fields inside the card use `.themeTextField(isGrouped: true)` if they are stacked in one bordered group, else the default `.themeTextField()`. Sign-in button `.primaryButton()`; "Create account"/secondary link is a plain text button in `Color.mcAccent`.
- Screen background `Color.mcCanvas`. Error text `Color.mcDanger`.

- [ ] **Step 3: Onboarding chips**

Rewrite the styling section of `OnboardingChip.swift` so a chip is:

```swift
.font(.subheadline.weight(self.isSelected ? .medium : .regular))
.foregroundColor(self.isSelected ? .white : Color.mcInk)
.padding(.horizontal, Theme.Spacing.md)
.padding(.vertical, Theme.Spacing.sm)
.background(self.isSelected ? Color.mcInk : Color.mcSurface)
.clipShape(.rect(cornerRadius: Theme.Radius.control))
.overlay {
    RoundedRectangle(cornerRadius: Theme.Radius.control)
        .stroke(self.isSelected ? Color.clear : Color.mcHairline, lineWidth: 1)
}
```

Selected chips are **ink**, not accent (web rule). No shadow, no scale effect beyond what already exists for press feedback.

`OnboardingQuestionHeader` (wherever it lives): title `.font(.title2).fontWeight(.semibold)` `Color.mcInk`, subtitle `.font(.subheadline)` `Color.mcBody`. Remove `design: .serif`.

- [ ] **Step 4: Flow and welcome**

`OnboardingFlowView.swift`: progress dots active `Color.mcAccent`, inactive `Color.mcHairline`; "Continue"/"Finish" button `.primaryButton()`; "Skip" is a plain text button `Color.mcBody`; background `Color.mcCanvas`.

`OnboardingWelcomeView.swift`: headline `.font(.largeTitle).fontWeight(.semibold)` `Color.mcInk`, no serif; body `Color.mcBody`; illustration tile (if any) `Theme.Radius.panel` with hairline; "Get started" `.primaryButton()`.

- [ ] **Step 5: Lint, build, commit**

```bash
bin/ios-format && bin/ios-lint && bin/ios-build
grep -rn 'design: .serif' hauptgang-ios || echo "no serif left"
git add -A hauptgang-ios
git commit -m "feat(ios): restyle login and onboarding

Login and onboarding use San Francisco headings, hairline panels, ink
selected chips and the accent primary button.

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

---

### Task 9: Search, meal plan, invitations, splash

**Files:**
- Modify: `hauptgang-ios/Hauptgang/Views/RecipeSearchView.swift`, `hauptgang-ios/Hauptgang/Views/RecipeRowView.swift`, `hauptgang-ios/Hauptgang/Views/MealPlanView.swift`, `hauptgang-ios/Hauptgang/Views/MealPlanDayRow.swift`, `hauptgang-ios/Hauptgang/Views/MealPlanRecipePicker.swift`, `hauptgang-ios/Hauptgang/Views/InvitationView.swift`, `hauptgang-ios/Hauptgang/Views/RootView.swift`, `hauptgang-ios/Hauptgang/Views/MainTabView.swift`

- [ ] **Step 1: Search**

`RecipeRowView.swift` (the list row used by search and the picker): thumbnail clipped to `Theme.Radius.card` with a `RecipePlaceholderGradient.gradient(for: String(recipe.id))` fallback when there is no image; title `.font(.headline)` `Color.mcInk`; time `.font(.mcMono(.caption))` `Color.mcMuted`; row background `Color.mcSurface` with `Color.mcHairline` stroke and `Theme.Radius.card` if it draws its own card, otherwise leave it as a plain List row.

`RecipeSearchView.swift`: canvas treatment (`.scrollContentBackground(.hidden)` + `.background(Color.mcCanvas)`) if it uses a List; "no results" state icon `Color.mcMuted`, text `Color.mcBody`. The search bar stays system (`.searchable` or the existing `SearchInputBar`), only its tint follows the accent.

- [ ] **Step 2: Meal plan**

`MealPlanView.swift` / `MealPlanDayRow.swift`: canvas treatment behind the List; day headers use the section-label style:

```swift
.font(.caption2.weight(.medium))
.textCase(.uppercase)
.tracking(1.1)
.foregroundColor(Color.mcMuted)
```

Today's marker `Color.mcAccent`. Any `.red`/`.yellow` from Task 7's grep in `MealPlanDayRow.swift` → `Color.mcDanger` / `Color.mcAmber`. `MealPlanRecipePicker.swift`: canvas treatment; rows use `RecipeRowView`.

- [ ] **Step 3: Invitation**

`InvitationView.swift`: the invitation card `Color.mcSurface` + `Color.mcHairline` stroke + `Theme.Radius.panel`; cookbook name `.font(.title3).fontWeight(.semibold)` `Color.mcInk`; inviter line `Color.mcBody`; "Accept" `.primaryButton()`, "Decline" `.outlineButton()`; error text `Color.mcDanger`; background `Color.mcCanvas`. No serif.

- [ ] **Step 4: Splash and tab tint**

`RootView.swift` `SplashView`: background `Color.mcCanvas` (mapped in Task 2 — confirm), logo unchanged, any spinner `.tint(Color.mcAccent)`.

`MainTabView.swift`: `.tint(Color.mcAccent)` (Task 2 produced `.tint(.mcAccent)`; add the explicit `Color.` prefix).

- [ ] **Step 5: Lint, build, commit**

```bash
bin/ios-format && bin/ios-lint && bin/ios-build
git add -A hauptgang-ios
git commit -m "feat(ios): restyle search, meal plan, invitations and splash

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

---

### Task 10: Share extension

**Files:**
- Modify: `hauptgang-ios/ImportRecipeExtension/ImportRecipeView.swift`

**Interfaces:**
- Consumes: `Color.mc*`, `.primaryButton()`, `.outlineButton()` (compiled into the extension in Tasks 1 and 3).

- [ ] **Step 1: Recolour**

```bash
grep -nE '\.(red|yellow|green|orange|secondary)\b|Color\.(red|yellow|green|orange|secondary)\b|borderedProminent|bordered' hauptgang-ios/ImportRecipeExtension/ImportRecipeView.swift
```

Apply, for every hit:

- Root container `.background(Color.mcCanvas)` (add `.ignoresSafeArea()` if the view fills the sheet).
- Success state: checkmark icon `Color.mcAccent`, title `.font(.headline)` `Color.mcInk`, detail `Color.mcBody`.
- Failure state: `xmark.circle`/`exclamationmark.triangle` icon `Color.mcDanger`, message `Color.mcBody`.
- Warning / not-signed-in state: icon `Color.mcAmber`.
- `.secondary` text colours → `Color.mcBody`.
- Progress state: `ProgressView().tint(Color.mcAccent)`.
- `Button("Close")… .buttonStyle(.borderedProminent)` → `.primaryButton()`; a second "Cancel"/"Open app" button, if present, → `.outlineButton()`. Remove `.tint(…)` calls that referenced system colours.

- [ ] **Step 2: Build both targets, run the extension in the simulator**

```bash
bin/ios-format && bin/ios-lint && bin/ios-build
xcodebuild -project hauptgang-ios/Hauptgang.xcodeproj -scheme ImportRecipeExtension -destination 'generic/platform=iOS Simulator' build 2>&1 | grep -E 'BUILD (SUCCEEDED|FAILED)|error:'
```

Expected: both print `BUILD SUCCEEDED`. (If there is no separate `ImportRecipeExtension` scheme, `bin/ios-build` already builds the extension as a dependency; the second command can be skipped.)

- [ ] **Step 3: Commit**

```bash
git add -A hauptgang-ios
git commit -m "feat(ios): restyle the share extension

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

---

### Task 11: Guard greps, tests, screenshots

**Files:**
- No source changes expected; fix anything the guards find in the file that owns it.

- [ ] **Step 1: Guard greps — all must print nothing**

```bash
cd hauptgang-ios
grep -rnE 'hauptgang[A-Z]' --include='*.swift' . ; echo "--- legacy tokens above (expect none)"
grep -rn 'design: .serif' --include='*.swift' . ; echo "--- serif above (expect none)"
grep -rnE 'Theme\.(Shadow|CornerRadius)' --include='*.swift' . ; echo "--- old Theme members above (expect none)"
grep -rn 'puffyButton\|PuffyButtonStyle' --include='*.swift' . ; echo "--- puffy above (expect none)"
grep -rnE '(foregroundColor|foregroundStyle|tint|fill|background)\((Color)?\.(red|yellow|green|orange)\)' --include='*.swift' . ; echo "--- bare system colours above (expect none)"
grep -rnE 'cornerRadius: [0-9]+' --include='*.swift' . | grep -v 'cornerRadius: 6)' ; echo "--- radius literals above (expect none except the 22pt step tile)"
grep -rn 'HauptgangBackground\|HauptgangPrimary' . ; echo "--- old colorset names above (expect none)"
cd ..
```

Fix every hit in its owning file, re-run until every section is empty.

- [ ] **Step 2: Full iOS checks**

```bash
bin/ios-format --lint && bin/ios-lint && bin/ios-build && bin/ios-test 2>&1 | tail -5
```

Expected: format-lint reports 0 files needing changes, lint prints no violations, `** BUILD SUCCEEDED **`, `** TEST SUCCEEDED **`.

- [ ] **Step 3: Screenshots**

Build, run and screenshot with the `xcodebuildmcp` CLI (see `hauptgang-ios/AGENTS.md` for the discovery flow):

```bash
xcodebuildmcp simulator list                       # pick a booted-or-available iPhone UDID → SIM
xcodebuildmcp simulator build-and-run \
  --project-path /Users/szymonnastaly/projects/maincourse/hauptgang-ios/Hauptgang.xcodeproj \
  --scheme Hauptgang --simulator-id SIM
xcodebuildmcp simulator screenshot --simulator-id SIM --return-format path
```

Navigate between screens with `xcodebuildmcp ui-automation snapshot-ui --simulator-id SIM` (lists tappable `elementRef`s) and `xcodebuildmcp ui-automation tap --simulator-id SIM --element-ref eN`. Take a screenshot of each of: Login, Onboarding welcome + one question screen, Recipes grid (must include at least one photo-less card to show a placeholder gradient), Recipe detail (duration panel, ingredients, steps), Shopping list with a checked item, Settings, Meal plan. The screenshot command prints a PNG path; open each with the Read tool. If the simulator has no signed-in account, run the Rails server locally (`bin/dev`, after `bin/rails db:seed`) and sign in as the seeded user `test@example.com` / `password123` from `db/seeds.rb`.

Check, per screenshot:

- Backgrounds are grey canvas, cards white with a visible hairline, no drop shadows on cards/chips/tiles.
- All body text is San Francisco; only times, servings, quantities, step numbers, counts and the invite link are monospaced.
- Tab bar, nav bar and List chrome look stock, tinted green.
- No brown, no serif, no bare system red/yellow/green.
- Placeholder cards show a warm gradient with white title.

Fix anything off in its owning view, rebuild, re-shoot.

- [ ] **Step 4: Commit any fixes**

```bash
git add -A hauptgang-ios
git commit -m "fix(ios): redesign polish from screenshot review

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

(Skip if the tree is clean.)

---

### Task 12: Documentation and the design skill

**Files:**
- Modify: `hauptgang-ios/AGENTS.md`
- Modify: `docs/web-design/README.md`
- Modify: `/Users/szymonnastaly/.claude/skills/maincourse-design/SKILL.md`
- Delete: `/Users/szymonnastaly/.claude/skills/maincourse-design/references/ios-legacy-tokens.md`

- [ ] **Step 1: `hauptgang-ios/AGENTS.md`**

Find the code-style rule that mentions `Color.hauptgangPrimary` / `.hauptgangPrimary` (grep `hauptgangPrimary`). Replace that bullet with:

```markdown
- Always use the explicit `Color.` prefix for design tokens (`Color.mcAccent`, not `.mcAccent`) — bare member syntax fails to type-check in `ShapeStyle` contexts such as `foregroundStyle`, `fill`, `tint` and `background(_:in:)`. Tokens, `Font.mcMono` and `Theme.Radius` live in `Hauptgang/Utilities/MainCourseTheme.swift`; button and field styles in `Hauptgang/Utilities/MainCourseStyles.swift`. IBM Plex Mono is for numerics only; every other text uses a semantic San Francisco style. No `design: .serif`, no drop shadows on flat surfaces, no dark mode.
```

Also update any sentence in that file that still says the iOS app uses a brown/Lato/Merriweather theme.

- [ ] **Step 2: `docs/web-design/README.md`**

In rule 1 ("The existing Rails ERB views are not the reference…") replace the parenthetical `(brown `#8B5E34`, Lato, Merriweather — see `ios-legacy-tokens.md` in the `maincourse-design` skill)` with `(brown `#8B5E34`, Lato, Merriweather)` and replace the final sentence "The iOS app was used for *functionality* only." with "The iOS app has since adopted the same tokens (`hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift`); see the iOS section of the `maincourse-design` skill."

- [ ] **Step 3: Skill — replace the "Web only" paragraph with an iOS section**

In `/Users/szymonnastaly/.claude/skills/maincourse-design/SKILL.md`, delete the paragraph beginning `**Web only.** The iOS app has not been redesigned…` and add this section after "## Component utilities" (before "## Helpers"):

```markdown
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
```

Also change the sentence in the skill's "Non-negotiables" that says "**Web patterns, not iOS patterns.** …" to keep it (it is about the web app) but add one bullet after it: "- **iOS patterns on iOS.** Tab bar, nav stack pushes, Lists and Forms stay native; only colours, type and radii are shared."

- [ ] **Step 4: Delete the legacy reference**

```bash
rm /Users/szymonnastaly/.claude/skills/maincourse-design/references/ios-legacy-tokens.md
grep -rn 'ios-legacy-tokens' /Users/szymonnastaly/.claude/skills/maincourse-design docs hauptgang-ios || echo "no dangling references"
```

Expected: `no dangling references`.

- [ ] **Step 5: Commit the repo changes**

```bash
git add hauptgang-ios/AGENTS.md docs/web-design/README.md
git commit -m "docs: describe the MainCourse tokens for iOS

Claude-Session: https://claude.ai/code/session_014ocGwUUH6RiXXWM6WPuK1C"
```

(The skill directory is outside the repo and is not committed.)

---

### Task 13: Full CI and hand-off

- [ ] **Step 1: Run the repo CI**

```bash
bin/ci > tmp/ci.log 2>&1; echo "exit=$?"
grep -n 'Continuous Integration failed' tmp/ci.log || echo "CI passed"
tail -30 tmp/ci.log
```

Expected: `exit=0`, `CI passed`. If a step fails, read the relevant section of `tmp/ci.log`, fix, and re-run.

- [ ] **Step 2: Review the branch**

```bash
git log --oneline main..ios-redesign
git diff --stat main..ios-redesign | tail -3
```

Expected: the spec commit plus one commit per task above. Report the list of commits, the screenshots taken in Task 11, and any deviations from the spec to the user.

# iOS localization

The iOS app uses Apple's String Catalogs with English (`en`) as the development
language. Language selection belongs to iOS: follow the system preference and
the built-in per-app language setting. There is no in-app language override.
The language setting becomes useful when a second supported language ships;
the foundation itself only ships English.

## Catalogs and bundles

- `hauptgang-ios/Hauptgang/Resources/Localizable.xcstrings`: app UI and app-owned errors.
- `hauptgang-ios/ImportRecipeExtension/Localizable.xcstrings`: share extension UI
  and its app-owned errors, including shared Swift sources such as `APIError`.
- `InfoPlist.xcstrings` in each of those directories: camera permission text and
  bundle display names. MainCourse stays untranslated as a brand name.

The extension is a separate bundle. Its default string lookup cannot use the
main app's catalog, so add every supported language to both targets together.
Shared source strings appear in both catalogs; keep their translations consistent.
`project.yml` owns the development language and compiler extraction setting.
XcodeGen discovers the catalog resources automatically.

## Writing localizable text

Use literal English as the key for ordinary interface text:

```swift
Text("Ingredients")
Button("Save") { save() }
Text("Invited by \(name)")
```

Reusable SwiftUI components accept `LocalizedStringKey` when their text is
app-owned. Use `LocalizedStringResource` when passing a deferred translation
across boundaries, and `String(localized: "...")` for view-model errors,
UIKit strings, and fallback text combined with server data. Localize at the
literal declaration rather than wrapping an arbitrary runtime `String` in a key.
Localize each branch of conditional strings explicitly, or give the property
a `LocalizedStringKey` type. Plain `String` properties otherwise bypass lookup.

Keep complete sentences in one entry. Use Swift interpolation (including
interpolated styled `Text`) so translators can reorder placeholders. Multiline
Swift literals with line continuations keep long sentences readable in code
without splitting their catalog entry. Add `comment:` for ambiguous wording.
Use catalog plural variations for counts, as in `RecipeDisplayFormatter.recipeCount`;
each translated language supplies its own categories. Never implement English-only
singular/plural branches in Swift.

Recipe names, ingredient text, URLs, cookbook names, and backend messages are
data: render them as strings, not localization keys. Use `Text(verbatim:)` for
fixed brand/sample content that deliberately stays literal. Protocol values,
analytics keys, accessibility identifiers, logs, and persisted data are not UI keys.
API errors use stable codes and client-owned messages; see `api-localization.md`.
Import failures use cached codes rather than English-message comparisons.

## Formatting

Use Foundation locale-aware format styles for display numbers, dates, and
durations. Recipe durations stay in minutes through `RecipeDisplayFormatter`.
`IngredientFormatter` localizes decimal separators while retaining cooking
fraction glyphs and the recipe's original units. This does not convert units.

Machine-readable API dates and decimal serialization stay locale-independent.
Shopping-list details are shared persisted plain text, so their numeric formatting
explicitly uses POSIX locale rather than changing with the creating user's language.

## Refreshing catalogs

Xcode can synchronize catalogs during development. The command-line build emits
compiler `.stringsdata` files but does not itself update the checked-in catalogs.
For command-line work:

```sh
bin/ios-build
bin/ios-localize --derived-data /path/to/the/builds/DerivedData/Hauptgang-...
```

Use the `-derivedDataPath` printed in the underlying Xcode build log linked by
`bin/ios-build`. XcodeBuildMCP uses its own DerivedData directory, which can differ
from Xcode's default. The sync command reads the two targets' compiler file lists,
checks that extraction output exists, and invokes Apple's `xcstringstool sync`. Always
build after source changes before syncing; Xcode preserves timestamps for unchanged
extraction output, so timestamps cannot reliably prove freshness. Compiler
output is important: lightweight source parsing cannot reliably infer interpolation
types or discover keys passed to custom SwiftUI components.

The sync command also fills missing English source values from the English keys.
This creates explicit English resources in both bundles for fallback and
pseudolocalization, while preserving existing plural variations and translations.

Review the catalog diff and commit it with the code. Sync preserves translations
and plural variations, removes unused untranslated keys, and marks translated
unused keys stale. Update manually maintained `InfoPlist.xcstrings` alongside the
corresponding `Info.plist` values. Build again after catalog edits to compile them
into the bundles.

## Adding and verifying a language

Add translations in Xcode's String Catalog editor, including both InfoPlist
catalogs. Use Xcode's Export/Import Localizations workflow for translator handoff.
English remains the source and fallback. Translate all plural forms and preserve
format specifiers and Markdown placeholders.

Run `bin/ios-test`, `bin/ios-lint`, and `bin/ios-format --lint`. Localization tests
exercise compiled English plurals, bundle membership, decimal formatting, duration
units, API error rendering, and the boundary between codes and compatibility prose.
Use Xcode's scheme Run > Options > App Language / App Region for simulator checks;
Double-Length Pseudolanguage reveals clipping before translations exist. Check
onboarding, login, recipe detail, shopping-list review, settings, alerts, and the
share extension on iPhone/iPad, with accessibility text sizes. For real language
rollouts, also verify switching through iOS Settings and any applicable RTL layout.

The backend error contract and language-ownership decisions are documented in
`api-localization.md` ([#125](https://github.com/SzymonNastaly/maincourse/issues/125)).
The translation rollout and web/Android foundations are tracked in
[#126](https://github.com/SzymonNastaly/maincourse/issues/126).

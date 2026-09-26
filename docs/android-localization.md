# Android localization

Android ships English (fallback), Polish (`pl`), and German (`de`) using native
resources in `maincourse-android/app/src/main/res/values{,-pl,-de}/`. Each locale
has UI strings, onboarding strings, API messages, and local-operation messages.
`xml/locales_config.xml` advertises the supported app languages; Gradle's
`androidResources.localeFilters` keeps dependency translations within that set.
Bundle language splitting is disabled so all three catalogs are installed together,
including for offline language changes, without a Play Core download dependency.

Language selection follows Android. Android 13+ exposes **Settings → Apps →
MainCourse → Language**; older versions follow the device language. Both
`MainActivity` and `RecipeShareActivity` use the same application resources and
normal configuration recreation. There is no separate in-app preference.
Notification channel names refresh on configuration changes.

## Writing text

Use `stringResource` / `pluralStringResource` in Compose, and explicit positional
format arguments (`%1$s`, `%2$d`). Keep complete sentences in resources. Styled
text can use XML `annotation` spans (the sign-in tagline is an example), letting
translators change word order without rebuilding sentences from fragments.
Add every new key to all three resource directories. Polish count plurals need
`one`, `few`, `many`, and `other`; German/English need `one` and `other`.

View models retain `UiMessage.Resource` or `UiMessage.Api`, not already translated
strings or an Activity context. Resolve them with `localized()` at the Compose
display boundary. A language change then also translates an existing error or
success message. `Throwable.userMessage` handles local/network exceptions and
captures API problems without displaying arbitrary exception/server prose.

The shared error-code manifest, exhaustive renderers, and Rails validation/import
contract are described in `api-localization.md`. No language header is required:
codes and typed parameters are translated on Android, including unknown/malformed
response fallbacks based on HTTP status. `LocalizedApiException` remains an
`HttpException` for authentication and oversized-payload recovery.

Use the current resource configuration (`appLocale()`) for display formatting,
including ingredient decimals. Cooking fraction glyphs remain intact. Wire
numbers/dates, user text, recipe units, and stored identifiers are not translated.
Shopping ingredient details are shared persisted text, so their numbers deliberately
use `Locale.ROOT`, independently of the creating device's language.

## Content boundaries and terminology

| English | Polish | German |
| --- | --- | --- |
| Recipe | Przepis | Rezept |
| Cookbook | Książka kucharska | Kochbuch |
| Shared cookbook | Wspólna książka kucharska | Gemeinsames Kochbuch |
| Shopping list | Lista zakupów | Einkaufsliste |
| Ingredients | Składniki | Zutaten |
| Servings / portions | Porcje | Portionen |
| Import | Import | Import |

Both translations use an informal, direct tone. MainCourse and provider brands
remain unchanged. The deletion confirmation token is still `DELETE`; its
translated prompt explicitly asks for that exact token.

Stored cookbook names and imported/user-authored recipes remain content, not
resource keys. The versioned English starter recipe stays aligned with the Rails
saved-copy source; its surrounding demo UI is translated. Translating starter
content requires coordinated preview/saved-copy versions. Per-device push bodies,
account-language email, default-name semantics, and store/purchase copy have
separate ownership and remain tracked in GitHub #126 / #125.

## Verification

Run `bin/android-build`, `bin/android-test`, `bin/android-test --device`,
`bin/android-gradle :app:assembleRelease`, and `bin/api-error-contract --check`.
The JVM resource test checks shipping-language completeness, plural categories,
and placeholder compatibility; Android lint also checks translation errors.
Device tests verify compiled resources, all API/validation/import mappings,
Polish plurals, fallback, and actual per-app language changes with retained error
state and the share UI. Formatter tests protect localized display versus stable
shopping payloads. The debug-only `MainCourseTestActivity` defaults existing UI
fixtures to English independently of the emulator's system language; explicit
per-app locale selections still use the platform configuration for switching tests.
For visual QA, check onboarding, authentication, long German
labels, recipes, shopping, settings, and the share sheet with larger text.

# Web localization

The Rails browser app ships English (`en`), German (`de`), and Polish (`pl`).
`config/locales/{en,de,pl}.yml` contains interface text; `rails-i18n` supplies
Rails validation messages, date/time formatting, sentence connectors, and plural
rules. English is the default and fallback. The glossary uses **Kochbuch / książka
kucharska**, **Rezept / przepis**, **Portion / porcja**, and **Einkaufsliste / lista
zakupów**.

## Language selection

`WebLocale` wraps browser requests with `I18n.with_locale`, restoring the previous
locale even after errors. A supported `web_locale` cookie takes precedence over
the browser's weighted `Accept-Language` preferences. Regional tags such as
`de-CH` and `pl-PL` resolve to their supported base language. Invalid ranges and
zero-weight entries are ignored; unmatched preferences fall back to English.

The language picker appears on authentication pages and in Settings. Its
CSRF-protected `PATCH /locale` stores a persistent, HTTP-only, same-site cookie;
“Browser language” clears it. This is a browser preference, not an account or
native-app setting. The form includes a same-origin return destination so invite,
password-reset and Apple sign-in links survive even without a referrer header.
Only allowlisted navigation query parameters are retained, never OAuth credentials
or exchange results.
It performs a full navigation so Turbo's document language and cached UI update
together. Responses declare `Content-Language` and vary by language and cookie.

API controllers inherit from `ActionController::API`, not `ApplicationController`,
so browser negotiation does not affect API codes, legacy English error fields,
or machine serialization. See `api-localization.md`. Password-reset email remains
explicitly English, including its duration formatting, until the separate
account-level communication-language work in #125 ships.

## Adding text

- Use semantic `web.*` keys and `t` in views, helpers and browser controllers.
  Translate full sentences with named interpolation, including link-bearing text.
- Keys ending in `_html` may receive Rails-generated safe tags or links. Let
  Rails escape user-supplied interpolation; never use `raw` or `html_safe` on data.
- Supply all plural categories: English/German `one`, `other`; Polish `one`,
  `few`, `many`, `other`. Use `count:` rather than Rails' English inflector.
- Pass localized text to Stimulus through data values. The servings stepper uses
  `Intl.PluralRules` with the same Rails-supplied noun forms, and `Intl.NumberFormat`
  with the selected interface locale. No separate JavaScript translation catalog.
- Add model attribute names under `activerecord.attributes` for translated form
  labels and validation errors.

Recipe names, instructions, ingredient units, tags, cookbook names and notes are
stored content, not translation keys. Language switches never rename them.
Pending-import labels and failure messages are presentation text: use import
status/codes rather than stored English names or error prose.

Ingredient display decimals use the selected locale and retain cooking fraction
glyphs. Shopping details remain shared plain text with stable English/POSIX-style
decimals: the server's hidden fields use `format_quantity(locale: :en)` and the
portion scaler serializes the same representation while localizing the preview.
No unit conversion is performed.

## Verification

`bin/ci` includes catalog key/placeholder/plural parity, weighted locale negotiation,
language persistence, translated pages and validations, escaping, and API locale
isolation. System coverage switches language before sign-in, scales German
quantities, checks persisted shopping details, and switches to Polish in the
mobile layout. Focused checks:

```sh
bin/rails test test/integration/web_localization_test.rb test/helpers/web_translations_test.rb
bin/rails test test/system/web_localization_test.rb
```

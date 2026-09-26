# API errors and language ownership

## Enforced contract and adding an error

`config/api_errors.yml` is the source of truth for API errors, asynchronous import
failures, validation fields, and validation rules. Every identifier has a meaning
and a parameter schema. `bin/api-error-contract --generate` writes the checked-in
Swift and Kotlin wire enums. Messages stay in native platform resources.

When adding an error:

1. Register its stable code and meaning in the manifest.
2. Run `bin/api-error-contract --generate`.
3. Handle the new enum case in **both** clients' `*Code+Message.swift` /
   `*CodeMessage.kt` files, including each needed English resource and translations
   for every shipping Android language (`android-localization.md`).
4. Build iOS and run `bin/ios-localize` to refresh the app and extension catalogs.
5. Run `bin/api-error-contract --check`, native builds/tests, and `bin/ci`.

The known-code renderers are deliberately exhaustive: no Swift `default` or Kotlin
`else` branch. Unknown future wire values are handled before the enum renderer,
so compatibility fallback cannot hide a missing known-code case. Keep each mapping
in its dedicated renderer and use explicit localized keys/resource IDs there.

The CI contract step checks enum generation freshness, mapping coverage in each
client, absence of catch-all cases, explicit localized messages in every arm,
English keys in **both** iOS catalogs, and referenced Android string resources.
Native compilers also enforce exhaustive switches. Mutation tests add a code,
update only one client, introduce a catch-all, and delete resources to verify these
checks actually fail. This checks source/resource completeness, not translation
quality or whether an older installed release supports a newer code.

Rails controllers must use `render_api_error "literal_code", status: ..., error: ...`.
The RuboCop rule rejects unregistered literal codes, dynamic code arguments, direct
error JSON, and error `head` responses (provider webhooks are excluded). The helper
validates the code and parameters again at runtime. The existing autocorrection can
convert inline `render json: { error_code: ... }` responses to this helper.

Validation details are dynamically produced by ActiveModel, so the serializer
validates every emitted field/rule against the manifest too. Unknown server-owned
fields/rules raise a contract error rather than silently becoming unhandled client
values. Current interpolation metadata is an optional integer `count`; the tool
rejects new parameter shapes until their typed decoding/rendering is implemented.
The recipe model validates persisted import codes against the same contract.

## Wire format

Native clients localize errors on device. Rails returns stable `error_code` values
and typed interpolation data. It does not negotiate an error-message language or
change `I18n.locale` from `Accept-Language`. This lets a shared recipe's import
failure render in each viewer's language and works identically for JSON, multipart,
and the iOS share extension. No client-language header is needed for these errors.

The existing `error` / `errors` English fields remain for installed clients.
They are compatibility prose, never identifiers or format strings. Codes are
additive: changing wording must not change a code; clients must tolerate new codes,
missing metadata, and non-JSON proxy responses. Deploy Rails before the new clients
to retain detailed messages throughout the rollout.

```json
{
  "error_code": "text_too_long",
  "error_params": { "count": 50000 },
  "error": "Text too long (max 50,000 chars)"
}
```

Model validations return `error_code: "validation_failed"`, legacy `errors`, and
an `error_details` array:

```json
{
  "error_code": "validation_failed",
  "errors": ["Name is too long (maximum is 50 characters)"],
  "error_details": [
    { "field": "name", "code": "too_long", "params": { "count": 50 } }
  ]
}
```

`BaseController#render_validation_errors` exports field names and symbolic Rails
validation types, with only integer `count` metadata. It never serializes rejected
values or the full validation options hash. Custom prose validations become
`invalid`; add an explicit semantic code when the client needs a more specific
recovery action. Shopping-list batch failures retain their legacy per-item details
and use `invalid_shopping_items` for the localized summary.

API dates, numbers, identifiers, user text, and recipe content keep their wire
representation regardless of language. A malformed or unsupported language header
cannot change authentication, validation, or JSON types.

## iOS rendering and compatibility

`APIClient` preserves HTTP semantics and the existing authentication, subscription,
and recipe-save control-flow cases. `APIProblem` renders validation and other
actionable errors through literal English catalog keys. The app and extension
compile this shared model into their own bundles. Parameterized messages use fixed,
localized format keys with typed arguments; raw server strings never become keys
or format strings.

Unknown codes, unknown fields/rules, malformed metadata, and code-less validation
responses produce a localized generic message. The HTTP status still determines
unauthorized, forbidden, missing-resource, server-error, and rate-limit fallbacks.
Code-less legacy password/OAuth login failures use the requested endpoint, never
English-word matching. Unknown authentication codes do not trigger a guessed
credential or account-creation flow.

Recipe list responses include `import_error_code`. Jobs persist `import_failed` or
`no_recipe_in_photo`, independently of the legacy `error_message`. The Rails
migration converts existing failed rows once. iOS schema V8 adds an optional cached
code without discarding recipe details or pending shopping-list edits. Old cached
rows and unknown future import codes render the generic localized import failure;
the next list sync supplies the code. Sentry's expected-photo-outcome filter also
uses the code, not English prose.

Android installs `ApiErrorCallAdapterFactory` on the production Retrofit instance.
Its `LocalizedApiException` remains an `HttpException` so existing status-based
recovery and authentication behavior survives. `userMessage` captures the problem
as a `UiMessage`; Compose resolves it through the current native resources when
displaying it, including after a language change. English, Polish, and German
resources ship together; see `android-localization.md`.
Missing, unknown, or malformed codes use localized HTTP-status fallbacks, including
server-unavailable and rate-limit messages for uncoded proxy responses.
The callback wrapper delegates cancellation and cloning and adds no retries.
Recipe cards render persisted import
codes through the same exhaustive import renderer. Older releases continue to use
the retained English fields.

## Language ownership for other server text

These decisions guide the language rollout; request errors do not require these
features to work:

- **Push:** use per-installation language on `DeviceToken`, not the user's last
  request language. A phone and iPad may have different app preferences. When
  translations ship, registration should send the app's selected *supported*
  language (on iOS, the bundle's preferred localization, not the device region),
  and refresh immediately on language changes, even if the push address is unchanged.
  Render at fanout per registration with an allowlisted Rails locale and English
  fallback for old/unsupported registrations. Both lifecycle campaigns and debounced
  shared-cookbook notifications need this; keep one logical delivery/frequency cap.
- **Email:** use an explicit account-level communication language, English by
  default. Device registration must not silently overwrite it. Password-reset mail
  currently has only English templates; its language setting and translations belong
  to the account/web rollout.
- **Default cookbook names:** retain stored names and never infer ownership of a
  name from equality with “My Recipes.” A translated default label needs a semantic
  default-name marker or an explicit presentation rule, while preserving renamed
  cookbooks. Do not rename shared data on a language switch.
- **Starter content:** translate versioned sample content deliberately alongside
  the client preview and Rails saved-copy source. Imported recipes, units, user
  names, and notes remain their authors' content.
- **Purchases:** RevenueCat paywall copy, StoreKit product/subscription metadata,
  and App Store listings have their own localization systems. Review those together
  with each supported-language release.

Follow-up implementation and real-language device QA are tracked in #126.

## Verification

Run `bin/ci`, `bin/ios-build`, `bin/ios-localize` (see `ios-localization.md`),
`bin/ios-test`, `bin/android-build`, and `bin/android-test`. Android device resource
checks use `bin/android-test --device` when a device is connected.
Rails integration tests cover error codes, safe validation metadata,
JSON/multipart behavior, unsupported language headers, and failed-import payloads.
iOS tests cover all three request paths, legacy/unknown/malformed responses, compiled
app/extension catalog lookup, and V6/V7-to-V8 store migration. A test-only Polish
bundle verifies translated messages and reordered interpolation without advertising
an incomplete language in the shipping app. Full per-app language-switch and
translated-screen acceptance remains part of the first-language rollout.

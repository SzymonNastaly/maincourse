# Store and purchase localization

Store listings, subscription names and the paywall are not part of the app
bundles. Each lives in its own service. The repo keeps the source text for every
language (`en`, `de`, `pl`) so a release can review and publish them together.
Nothing here is uploaded by CI; every push below changes customer-facing text
and is done by hand. The German and Polish copy uses the informal du/ty form, like
the apps.

| Text | Source in repo | Published to |
| --- | --- | --- |
| App Store name, subtitle, privacy URL | `metadata/app-info/<locale>.json` | App Store Connect app info |
| App Store description, keywords, What's New | `metadata/version/<version>/<locale>.json` | App Store Connect version |
| Subscription group and product names | `metadata/subscriptions/<locale>.json` | App Store Connect subscriptions |
| Paywall | `metadata/paywall/<locale>.json` | RevenueCat paywall for the `pro` offering |
| Play Store listing | `maincourse-android/fastlane/metadata/android/<locale>/` | Play Console main store listing |

App Store locales are `en-US`, `en-GB`, `de-DE` and `pl`; Play uses `en-US`,
`de-DE` and `pl-PL`; RevenueCat uses `en_US`, `de_DE` and `pl_PL`.

## App Store listing

New locales and per-version text can only be edited on a version that is still
editable in App Store Connect. The release flow publishes these files when it
stages the version (`asc release stage --metadata-dir ./metadata`, see
`screenshots.md`). To check or push them separately:

```sh
asc metadata validate --dir metadata --subscription-app --output table
asc metadata push --app 6758990872 --version 1.0.8 --platform IOS --dir metadata --dry-run --output table
```

Drop `--dry-run` after reviewing the plan. Copy the previous version's folder
when starting a new one and rewrite `whatsNew` in every locale.

## Subscriptions

Display names show in the purchase sheet and in Settings → Subscriptions.
Names are at most 30 characters, descriptions 45. They are version-scoped:

```sh
asc subscriptions versions list --subscription-id SUBSCRIPTION_ID
asc subscriptions versions localizations list --version-id VERSION_ID
asc subscriptions versions localizations create --version-id VERSION_ID \
  --locale de-DE --name "..." --description "..."
```

The group name uses `asc subscriptions groups versions localizations`. If the
current version is already approved, App Store Connect asks for a new
subscription version, which is reviewed together with the next app version.
After publishing, re-sync `hauptgang-ios/Hauptgang-StoreKit.storekit` from Xcode;
it is a synced configuration, so do not edit it by hand.

## Paywall

`PaywallView` (RevenueCatUI) renders the paywall configured in RevenueCat and
picks the device's language, falling back to `en_US`. The files map RevenueCat's
component IDs to text. Values that are only a `{{ product.* }}` variable are
filled in and localized by RevenueCat and stay the same in every file. Enter
the text in the dashboard's paywall editor (Localization) for the `pro` offering.

To compare with what is live, fetch the offering with the public iOS SDK key
from `Constants.RevenueCat` and read
`.offerings[0].paywall_components.components_localizations`:

```sh
curl -s -H "Authorization: Bearer $PUBLIC_SDK_KEY" -H "X-Platform: iOS" \
  "https://api.revenuecat.com/v1/subscribers/%24RCAnonymousID%3Acompare/offerings"
```

When the paywall layout changes in RevenueCat, its component IDs change too.
Refresh the files from that response before translating.

## Play Store listing

The files follow fastlane supply's layout (`title.txt` ≤ 30,
`short_description.txt` ≤ 80, `full_description.txt` ≤ 4000 characters). Download
the live listing to compare, then upload text only:

```sh
cd maincourse-android
bundle exec fastlane run download_from_play_store package_name:com.getmaincourse.app \
  json_key:play-service-account.json metadata_path:/tmp/play-listing
bundle exec fastlane run upload_to_play_store package_name:com.getmaincourse.app \
  json_key:play-service-account.json metadata_path:fastlane/metadata/android \
  skip_upload_apk:true skip_upload_aab:true skip_upload_changelogs:true \
  skip_upload_images:true skip_upload_screenshots:true
```

The upload needs the service account to have store-listing permission in Play
Console; the release setup in `android-release.md` does not grant it. Otherwise
paste the text into Play Console → Main store listing → Manage translations.

## Screenshots

`bin/screenshots capture --locale de-DE` (and `pl-PL`) captures the app in that
language with translated showcase recipes, and `render --locale` uses the
headlines in `screenshots/copy/<locale>.json`. Upload each locale's App Store
panels to its own localization; see `screenshots.md`.

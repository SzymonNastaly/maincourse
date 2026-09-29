# Push notifications

Push delivery is installation-based and provider-neutral. A user can have any
number of active `DeviceToken` rows, including APNs registrations from iPhone or
iPad and Firebase Installation IDs (FIDs) from Android. Every logical push fans
out to all active registrations; signing out one installation removes only that
registration.

## Server architecture

Clients register with `POST /api/v1/device_tokens`, sending `token`, `provider`,
`environment`, `time_zone`, and `language`. `provider` is `apns` or `fcm`; omitted values
default to `apns` for compatibility with older iOS releases. The `token` field is
an opaque provider address: an APNs device token for `apns`, and a Firebase
Installation ID for `fcm`.

`Push::Fanout` and `Push::Dispatcher` route a neutral alert and custom-data hash
through `Apns::Client` or `Fcm::Client`. A failure on one installation does not
stop the others. Provider responses known to mean that an address is invalid are
removed from `device_tokens`.

FCM uses the HTTP v1 `fid` target and Google service-account OAuth. Production
credentials live under `firebase.project_id` and `firebase.service_account_json`
in encrypted Rails credentials. The latter is the complete service-account JSON
stored as a YAML block scalar. Never commit the service-account JSON.

## Language

Each registration stores its installation's app language (`device_tokens.language`),
because a phone and an iPad on one account can use different app languages. Rails
allowlists it against `I18n.available_locales` (`de-CH` becomes `de`); unknown values
and older clients that send none are stored as `nil` and receive English.

`Push::Fanout` takes a block that builds the alert and evaluates it once per language,
inside `I18n.with_locale`, so every device gets text in its own language while the
notification remains one logical delivery with one `NotificationDelivery` row and
one frequency-cap slot. Push text lives under `push.*` in `config/locales/{en,de,pl}.yml`.
Lifecycle candidates therefore carry a translation key and parameters, not rendered
text. Recipe and cookbook names are interpolated as stored.

The clients send a language the app actually ships, never the device region: iOS
uses `Bundle.main.preferredLocalizations`, Android the `push_registration_language`
resource of the active resource set. A changed language re-uploads an otherwise
unchanged registration. Changing the per-app language restarts the iOS app and
recreates Android's `MainActivity`; both registration paths run again at that point.

The two sources of pushes are:

- Lifecycle campaigns, described in `docs/lifecycle-notifications.md`.
- Debounced shared-cookbook activity from `PendingNotification`: shopping-list
  additions and meal-plan additions/votes.

## Client registration

Both clients upload the current IANA time zone and refresh an unchanged
registration at least every 30 days. This keeps the server's 90-day active-device
window meaningful without retaining abandoned installations forever.

iOS registers only after notification authorization and deletes its APNs token
from Rails before sign-out. Android enables FID registration through
`firebase_messaging_installation_id_enabled`, receives changes through
`FirebaseMessagingService.onRegistered`, and uploads only while authenticated and
device notifications are enabled. Android calls FCM `unregister()` during sign-out
or invalid-session cleanup so a stale Rails row cannot keep reaching the previous
account.

Android Firebase config files are build-specific and ignored by git:
`maincourse-android/app/src/debug/google-services.json` and
`maincourse-android/app/src/release/google-services.json`. Both application IDs
must be registered in Firebase. Builds without either file still compile and test,
but push is inactive.

## Provisioning and rollout

1. Register `com.getmaincourse.app` and `com.getmaincourse.app.debug` as Android
   apps in the same Firebase project, then place each environment's ignored
   `google-services.json` at the path above.
2. Enable the Firebase Cloud Messaging HTTP v1 API and create a service account
   that can send FCM messages.
3. Add the Firebase project id and service-account JSON to encrypted Rails
   credentials as `firebase.project_id` and `firebase.service_account_json`.
4. Deploy the Rails migration and provider-aware server before distributing an
   Android build that registers with it.
5. Smoke-test with one account signed in on Android and iOS/iPadOS: trigger one
   lifecycle notification and one shared-cookbook notification, verify both
   installations receive it, then sign out Android and verify only the Apple
   installation continues to receive pushes.

## Preferences and routing

`lifecycle_notifications_enabled` is an account-wide preference. OS permission
and Android notification channels are installation-specific; denying permission
on one phone must not disable reminders on another device.

Lifecycle payloads carry `campaign`, `delivery_id`, optional `recipe_id`, and
optional `cookbook_id`. Taps select the referenced cookbook before opening the
recipe or shopping list, then report `delivery_id` as opened. Shared shopping-list
activity routes to that cookbook's list. Meal-plan activity opens the Android app's
home screen until Android exposes a meal-plan destination.

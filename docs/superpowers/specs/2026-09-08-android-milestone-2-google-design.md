# Android Milestone 2: Google Sign-In

## Scope

Add explicit Google sign-in to both standalone and onboarding authentication,
using Credential Manager and the existing Rails OAuth API. This is the second
slice after the onboarding/account core. Full milestone 2 also needs Apple and
real debug/release-signed provider verification. The user authorized autonomous
design and subagent execution; proceed through local implementation and review.

## Chosen approach

Use `GetSignInWithGoogleOption` for the user-initiated button. An automatic popup
and silent auto-selection would add lifecycle complexity without being needed
for visible iOS parity. Keep the explicit email form and its current behavior.
Do not introduce Firebase Auth, the deprecated GoogleSignIn SDK, extra Google
API scopes, or a second identity store.

Pin `androidx.credentials:credentials` and
`androidx.credentials:credentials-play-services-auth` to stable `1.6.0`, and
`com.google.android.libraries.identity.googleid:googleid` to `1.2.0`. Google Maven
metadata and published POMs were checked on 2026-09-08; this set is compatible
with the current Kotlin floor. The stable Play-services adapter itself declares
an identity-credentials alpha transitive dependency; accept the publisher's
dependency set rather than force independent overrides. Verify actual Gradle
resolution and leave all existing toolchain pins intact.

## Configuration

Request Google ID tokens for the existing public web/server audience:
`1048887933015-tga3ld77ufb2jfgugh5b5ddgo854uoto.apps.googleusercontent.com`.
It is already checked into the iOS project. This is public client configuration,
not a secret. Keep the public value in one Android configuration constant/resource
with provenance documented. The Android client registered in #92 is the separate
debug package/certificate registration, not the token audience.

No downloaded OAuth JSON, Google Services plugin, client secret, signing key, or
production credential change is required. Preserve debug/release API URL policy
and disabled backup. Unsigned release can compile with the same server audience,
but real release OAuth is unverified until its installed signing certificate is
registered. Do not create signing keys or alter Cloud/Play console configuration.

## Flow and ownership

The native auth surface shows a Google-branded `Continue with Google` button
above the email fields. Its activity is user-initiated only. Disable competing
email/provider/onboarding navigation actions while one auth attempt owns the
flow; dismissal restores the form without clearing typed email/password.

Generate a fresh 32-byte cryptographically random nonce for each provider attempt,
URL-safe Base64 without padding. Call Credential Manager with a foreground
Activity context and the configured server audience. Accept only the documented
Google ID token credential type, decode with the Google library, and reject
empty/malformed/unexpected credentials. Do not locally treat claims as proof of
authentication: Rails remains the verifier and issuer of MainCourse sessions.

The provider adapter belongs under `features/auth` or `data/auth`, behind a small
testable interface. Keep Activity-dependent chooser lifetime separate from the
retained Rails session coordinator. Activity destruction cancels an unresolved
chooser and returns to a retryable idle form on recreation; never keep a stale
Activity in an application singleton or relaunch the chooser from restored
state. Once the chooser has returned, the Rails exchange can finish in the
retained auth lifetime without needing that Activity. Process death drops the
attempt; a later explicit tap obtains a fresh credential and nonce.

After credential acquisition, prepare the existing optional onboarding payload
within its owned five-second budget, then send:

```json
{
  "provider": "google",
  "id_token": "<ephemeral signed provider token>",
  "nonce": "<this attempt's nonce>",
  "device_name": "<Android device label>",
  "onboarding_device_id": "<optional draft UUID>"
}
```

`POST /api/v1/oauth_session` is unauthenticated and sends no cookbook header.
No transport retries, redirects, or token replays are added. The returned
SessionResponse passes through the exact existing secure-store/expiry/session-
establishment path, then consumes onboarding and starts cookbook discovery.
Keep both Google token and nonce in private in-memory attempt objects, never
public StateFlow, saved state, routes, logs, analytics, preferences, or Room.
Clear references once the attempt completes/cancels. Login admission and an
attempt generation prevent a late chooser result from replacing another login.

## Errors and cleanup

- User dismissal/cancellation is a normal return to idle, not a scary server
  error. Coroutine cancellation remains cancellation.
- No account, unavailable/missing Google Play services, provider configuration
  failures, and malformed responses produce concise recoverable auth feedback.
- The existing Rails `409 account_link_required` tells the user to sign in with
  their password. Preserve the email form and do not merge identities locally.
- OAuth `401` is a signed-out auth error, not authenticated-session cleanup.
  `503` or transport failure allows an explicit new provider attempt; never replay
  an old token automatically. Preserve onboarding until a session is secured.
- Sign out, account deletion, and session invalidation notify Credential Manager
  with bounded best-effort `clearCredentialState`. Failure must not skip or block
  reliable credential/Room/image deletion or prevent email login on devices
  without Google services. Clearing selection does not revoke Google permissions
  or delete the user's Google account. No Google refresh token is stored.

## UI and branding

Use Google's current official full-color G asset with source attribution; it is
a provider brand mark, not a replacement icon family. Follow the current Google
button branding guidelines and use the light button. Keep native accessibility
labels, touch targets, loading feedback, keyboard/scroll behavior, MainCourse
light-only theme, and compact/tablet layout. Provider-specific brand typography
and colors belong in this one component, never generic app tokens.

## Verification

JVM tests cover exact OAuth payload/header isolation, no automatic replay,
nonce generation, adapter error mapping at a fake boundary, competing auth
attempts, delayed chooser/exchange, cancellation/reset, onboarding attribution,
secure session failure, and `409`/`401`/`503` behavior. Device tests cover the
Google credential parsing boundary and Compose button/form/error behavior,
Activity destruction/recreation, provider-state cleanup and missing services.
Use real controllers with a fake chooser/API for deterministic race tests.

Run full Android debug/release build/lint and JVM/device suites. Run Rails OAuth
controller, identity, and ID-token-verifier contracts; Rails production logic
should not need changing. If a shared contract must change, explicitly broaden
verification to affected web/iOS auth behavior before claiming completion.

Live local MainActivity can show the real Google chooser/add-account surface
and recover from cancellation. The current API37 emulator has Google Play
services but zero Google accounts. Successful real consent and returning-user
identity verification therefore require the owner's interactive Google login.
Do not read or automate their Google password, invent successful-provider
evidence, or claim unsigned release satisfies the signed-build gate. Preserve
the existing dedicated local email account and emulator defaults after checks.

Track implementation and evidence in a new GitHub issue linked from #92 and the
roadmap. Record local code/test completion separately from owner-controlled real
provider verification. Apple is the next slice and remains gated by #86 and a
registered HTTPS callback/signing association.

## Sources

- https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- https://developers.google.com/identity/branding-guidelines
- Google Maven metadata/POMs for Credential Manager 1.6.0 and googleid 1.2.0.
- `docs/oauth-sign-in.md`, `app/controllers/api/v1/oauth_sessions_controller.rb`,
  `app/services/oauth/id_token_verifier.rb`, `app/models/identity.rb`.

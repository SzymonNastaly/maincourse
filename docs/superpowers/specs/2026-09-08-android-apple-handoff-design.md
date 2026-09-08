# Android Apple Browser Handoff

## Goal

Complete milestone 2's Android Apple implementation using the existing grouped
Apple Services ID and Rails OAuth integration. Preserve the explicit new-account
decision implemented for #86/#99. Real provider, registered HTTPS, production
App Link and release-signing gates remain separate from local code verification.
The user authorized autonomous design and subagent execution, then automatic
continuation into milestone 3 without waiting on external setup gates.

## Chosen architecture

Reuse OmniAuth's existing `/auth/apple/callback`, rather than introduce a second
Apple callback URI or send web-audience tokens through the iOS-native endpoint.
Android creates an anonymous Rails transaction with a PKCE S256 challenge,
opens a Rails browser page, and receives a one-minute, single-use exchange code
after verified Apple authentication. The app exchanges it with its private
verifier for the normal Rails session. No API bearer or Apple credential goes
in a URL, browser storage, Android route, public UI state, or logs.

Use one small database-backed `AppleAuthTransaction` and the existing manual
Android container/session flow. No generic OAuth framework or background
authentication retry queue is needed.

## Rails transaction and endpoints

Add a primary-database migration/model with unique `handle_digest`, PKCE
`code_challenge`, an exact allowlisted `return_uri`, nullable user foreign key
(cascade on account deletion), nullable unique `exchange_digest`, `expires_at`,
`authorized_at`, `consumed_at`, `confirmation_required_at`, and timestamps.
Raw handle and exchange code are independently generated from 32 random bytes;
store only SHA-256 digests. No provider ID token, authorization code, refresh
token, or PKCE verifier belongs in this table. Existing Identity remains the
only owner of encrypted Apple refresh tokens.

- `POST /api/v1/apple_auth_transaction`: accepts `code_challenge` (43-character
  URL-safe unpadded SHA-256) and `callback` (`debug` or `release`). Requires
  neither API bearer nor cookbook membership. Reject invalid types/values.
  Return `201 { transaction_id, browser_url, expires_at }`. Lifetime is five
  minutes from creation; it never extends on retry/confirmation.
- `POST /api/v1/apple_auth_transaction/exchange`: accepts transaction_id,
  exchange_code, code_verifier, device_name, optional onboarding_device_id.
  Validate RFC7636 verifier length/alphabet, S256 against the saved challenge,
  the code digest, authorized state, expiry and unconsumed state under one row
  lock. Generate ApiToken, link onboarding and mark consumed atomically. Return
  the normal session body (`201`); never replay it on a repeated exchange.
- Invalid, expired, consumed, wrong-code or wrong-verifier exchanges receive a
  generic `400` auth failure; no user/identity information is disclosed and a
  wrong attempt does not consume a still-valid code. Use existing rate-limit
  patterns on anonymous endpoints outside local environments.

Allowed return URIs are server-selected, never arbitrary client URLs:

- `release`: `https://app.getmaincourse.com/android/auth/apple`.
- `debug`: `com.getmaincourse.app.debug:/oauth/apple`, permitted only when
  `Rails.env.local?`. This is a development convenience protected by PKCE, not
  verified App Link evidence. Production must never trust the debug certificate
  or redirect to this scheme.

Authorization-code expiry is the earlier of one minute after authorization or
the transaction expiry. Failed/repeated browser callbacks cannot mint another
exchange code for an already authorized/consumed transaction. Row locking covers
the state check, Identity authentication and authorization update so parallel
callbacks cannot perform identity side effects after another callback wins.
Abandoned records expire without a worker dependency; delete expired records
older than a day opportunistically in the create path at this app's scale.

Add parameter-log filtering for transaction_id, android_transaction,
exchange_code and code_verifier. Never include raw provider exceptions or
payloads in user-facing errors. All responses use `Cache-Control: no-store`.
Browser form pages use `Referrer-Policy: strict-origin`: only the public origin
can be sent, never the handle/code, and normal CSRF origin checks remain usable.
API, callback, failure and static return responses use `no-referrer`.

## Browser and callback

The browser URL is a same-origin Rails GET page for the opaque transaction
handle. It displays a normal native-web Continue with Apple button, a Cancel
action, and uses the existing authentication layout. The provider action is a
CSRF-protected POST to `/auth/apple?android_transaction=HANDLE`; OmniAuth
captures the handle in its request-phase session parameters. The GET itself
does not authenticate or create a user.

The callback reads the handle only from `env['omniauth.params']`, never callback
query/body parameters. Validate the transaction before Identity side effects.
On success issue the exchange code and redirect only to the stored allowed URI
with transaction_id and exchange_code. Do not create a Rails browser Session
for an Android-only handoff or reuse an existing web login as proof of Apple
authentication.

Unknown Apple identity: the first callback calls Identity with creation denied,
marks the transaction confirmation-required, and redirects to a dedicated
confirmation page carrying only the opaque handle. The existing unused-refresh-
token revocation still runs. The page explicitly offers Use existing account /
Cancel or Create new account with Apple. Creation starts a fresh CSRF-protected
Apple request with the handle and `allow_account_creation=true`; allow creation
only when both the stored confirmation-required state and captured request
intent agree. A forged initial creation flag must not skip the first decision.
Do not retain the first name/email/credential. Missing second-grant name remains
the documented external-provider check and editable profile fallback.

Cancellation/failure returns only fixed error codes via the transaction's
allowlisted URI. Hook OmniAuth failure handling narrowly for an Android
transaction before the default FailureEndpoint discards request-phase params;
other web/provider failures retain their existing behavior. An untrusted handle
is only a lookup key, never authorization. Failure must never authorize a row,
create an API token, accept an arbitrary redirect, or echo provider data.

## State and nonce hardening

OmniAuth OAuth2 generates state and validates it; Apple generates nonce but its
current gem checks nonce only when `nonce_supported` is truthy. Add a small
explicitly loaded Apple strategy subclass that retains the existing provider
name/callback/configuration and tightens **Android handoffs only**:

- Require and compare the expected session nonce even when nonce_supported is
  false or absent. Keep signature, issuer, audience and time checks intact.
- Consume state and nonce on every Android callback completion/failure.
- Missing or mismatched state/nonce must not reach identity authorization.

Use the existing middleware session for browser correlation and the database
state machine for the authoritative one-use guarantee. Parallel tabs may cancel
one another because OmniAuth has one session slot; surface retry rather than
weaken validation. Test real strategy nonce/state behavior with signed fixture
tokens and stubbed upstream endpoints, distinguishing it from ordinary
OmniAuth test-mode parameter-flow tests.

## Android flow

Add Apple to the existing nonsecret AuthenticationMethod state and auth button
group. Use the Apple brand mark already present in the repository with a
native black/white Continue with Apple component and documented brand exception.
Retain the Google and email controls and their current admission guards.

On an explicit tap, reserve the same root auth admission, create a fresh random
PKCE verifier and S256 challenge, and call the start endpoint. Hold the verifier,
transaction handle and deadline only in private retained ViewModel memory.
Publish a one-shot browser launch command with only its opaque URL; the current
Activity consumes it once and opens the system browser (`ACTION_VIEW` is enough,
no new browser dependency). Never retain an Activity in the ViewModel or
automatically relaunch the browser after recreation.

Unlike the Google chooser, browser handoff survives Activity recreation. While
waiting, show explicit Cancel and bounded waiting/expiry feedback on the auth
surface. Cancel invalidates the private attempt and releases email/Google
admission; the server record expires naturally. No automatic restart. If process
death loses the verifier, a returning callback is discarded and signed-out UI
offers a fresh attempt. It must not interrupt an already restored valid account.

Handle callback Intents in both cold `onCreate` and `onNewIntent`, with
singleTop delivery as appropriate. Main release filter accepts the exact HTTPS
host/path; debug manifest additionally accepts only the debug scheme. Strict
parsing rejects wrong scheme/host/path/port, userinfo, fragment, duplicate
parameters, missing/oversized values and mutually conflicting code/error fields.
Require the callback transaction handle to match the active in-memory attempt.
Unknown/stale callbacks never send a request or switch accounts.

After an accepted exchange code, prepare onboarding with the existing bounded
best-effort ordering, exchange exactly once, and reuse SessionController's
secure session establishment. Provider/API errors remain auth feedback, not an
authenticated-session purge. A network failure or lost exchange response must
not be retried automatically: start a fresh Apple attempt explicitly. Discard
private verifier/code references and cancel deadlines on success/cancel/error.

Only code and handle travel through the App Link; bearer credentials stay in
the HTTPS response and encrypted session store. A local debug HTTP API remains
allowed by the existing development policy, but real Apple web callbacks need
a registered HTTPS host. Browser pages should give an actionable setup error on
non-HTTPS development origins rather than pretending local Apple consent works.
Server provider-unavailable `503` leaves other login methods usable.

## Verification and rollout

Rails tests: exact start validation/return allowlist, hash-only storage, expired
cleanup, single-use concurrent exchange/rollback, wrong verifier/code/replay,
onboarding attribution, no cookbook header requirement; real middleware
state/strict-nonce failures including absent nonce_supported; captured handle
versus callback tampering; no browser Session; explicit creation/fresh callback,
failure/cancel fixed redirects, no leakage/log filtering, account deletion FK.

Android JVM/device tests: RFC7636 challenge vector, exact anonymous payloads,
strict callback parser, shared auth admission, one-time launch, rotation versus
process-death callback, cancel/expiry/late callback, onboarding/secure-store
failure, no automatic exchange replay, native controls and restored email/Google
flows. Inject browser/API boundaries for deterministic tests; no production
authentication bypass is introduced.

Run Rails CI, Android clean debug/release builds, both lint variants and JVM/
device tests, plus affected iOS auth checks for shared OAuth compatibility.
Inspect real MainActivity/browser landing and unsupported-local-origin feedback;
fake provider roundtrips do not prove real Apple/Hide My Email. Production App
Link verification still requires the owner's release certificate and published
assetlinks.json; add no debug certificate to the production host. Real Google/
Apple/release-signed checks remain issues #92/#86 until actually performed.

Record code gate and outstanding owner gates separately in the roadmap and
OAuth/Android guides, then continue milestone 3 automatically. No deployment,
publishing, secret edits, signing-key creation or production account operations
are authorized by this implementation plan.

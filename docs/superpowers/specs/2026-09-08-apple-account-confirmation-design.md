# Apple Account Creation Confirmation

## Goal and scope

Resolve #86 before Android's Apple browser handoff: an unknown Apple identity
must not silently create a second MainCourse account when Hide My Email returns
an address unlike an existing account. The identity boundary is shared by web
and iOS, so fix both entry points before enabling Android Apple. The user has
authorized autonomous design, subagent execution, and continuing into milestone 3.

Use an explicit new-account decision, not email guessing or automatic merging.
Returning `(provider, uid)` identities keep their existing user. Google and the
existing verified same-email OAuth-only linking rule remain unchanged. Password
account conflicts still instruct the user to use their password. Authenticated
provider linking is a separate feature, not required to stop silent creation.

## Server contract

Extend `Identity.authenticate!` with `allow_account_creation: false`. After
provider verification, stable identity lookup, and existing-email handling, the
branch that would create a user raises
`Oauth::AccountCreationConfirmationRequiredError` for Apple unless the value is
the boolean `true`. Apply this to all unknown Apple emails, not just private
relay domains: absence of a matching email never proves that a person is new.

Only the new-user branch is gated. The explicit flag must not bypass token
verification, change an existing identity's owner, bypass a matching password
account conflict, or merge cookbook data. No database migration is required.

For `POST /api/v1/oauth_session`, updated Apple clients send
`allow_account_creation: false` for their normal attempt and `true` only after
confirmation. JSON string `"true"`, null, and other truthy values do not grant
creation. The normal unconfirmed unknown-identity response is `409` with
`error_code: "apple_account_creation_confirmation_required"` and an explanation
that the Apple sign-in is not linked and a new account requires confirmation.

Legacy native clients omit this field and map unknown `409` codes to a generic
error. For that specific omission, return `422` with an actionable `error`
message: update the app to create a new Apple account, or use the prior sign-in
method to access an existing account. Their existing error parser displays
that message. Do not falsely use `account_link_required`, which claims a
matching-email account exists. Returning Apple identities and Google do not
take this compatibility branch.

Apple authorization codes are single use. An unconfirmed first attempt has
already exchanged its code; retain the existing ensure-path revocation of its
unpersisted refresh token. A confirmed attempt obtains a fresh Apple credential,
nonce, and authorization code; it must never replay the rejected credential.
No pending provider token is stored in a browser cookie or client persistence.

## Web flow

The normal Continue with Apple button keeps its current POST request. If the
verified callback needs confirmation, redirect to a dedicated read-only page in
the authentication layout. Explain:

> This Apple sign-in isn't connected to a MainCourse account. If you already use
> MainCourse, sign in the way you used before to keep your recipes. Creating a
> new account starts a separate cookbook.

Offer **Use existing account** (sign-in page) and **Create new account with
Apple**. The latter is a CSRF-protected fresh POST to
`/auth/apple?allow_account_creation=true`, with Turbo disabled. OmniAuth records
request-phase query parameters in its session and supplies `omniauth.params`
after state validation. Read the intent from that verified request-phase
context only, never callback form/query parameters. The Apple callback URL
itself remains unchanged and query-free. Direct GET of the confirmation page
does not authenticate or create anything.

Use existing MainCourse web tokens, authentication layout and button utilities.
No new identity data or secret is embedded in the page, URL, or flash. Keep
canonical-host/provider-enabled rules consistent with existing auth buttons.

## iOS flow

Map the new conflict code to a dedicated APIError case. Apple auth requests
explicitly encode false/true creation intent; Google requests omit it.
AuthService's successful token/user storage behavior stays unchanged.

On the new error, AuthViewModel exposes only a confirmation state. LoginView
shows a native confirmation alert with the same meaning as web. **Use existing
account** leaves a usable existing-account sign-in form and retains nonsecret
typed input. **Cancel** performs no new request. **Create new account** clears
the pending confirmation and starts a fresh Apple authorization attempt; only
its new credential is posted with creation allowed. If the person dismisses
Apple's sheet, return to idle and require a new explicit attempt.

Move Apple launch behind a small injectable `AppleSignInProviding` boundary so
the two-attempt flow, cancellation, failure, and single-use confirmation can be
tested with AuthViewModel. Keep provider acquisition/loading admission coherent
with the current email/Google controls; double taps must not launch competing
provider sheets. Neither the confirmation state nor the view retains the first
OAuth credential. No automatic re-prompt after restart, no keychain/provider
payload inspection, and no changes to Google authorization or AuthManager's
account ownership.

## Verification

Rails tests cover unknown relay and non-relay Apple identities blocked without
confirmation, explicit true creates exactly one account, string/null flags do
not bypass the gate, known UID returns the same user even when email changes,
matching password conflict and OAuth-only linking, Google unchanged, and
verification/nonce errors still rejected. Controller tests verify legacy422
message/new409 code, no token/user creation on rejection, refresh-token
revocation, web confirmation route/CSRF POST intent and rejection of callback
parameter tampering. Exercise request-phase OmniAuth parameter capture rather
than only stubbing a callback's final environment.

iOS tests cover response mapping, serialized Apple intent versus Google
omission, unknown-identity confirmation, cancel/use-existing, and a fresh second
provider credential/code/nonce after explicit confirmation. Confirm busy and
error recovery and that a successful known identity needs no extra prompt.
Run `bin/ios-test`, `bin/ios-build`, and Rails `bin/ci` with captured exit/logs.
Keep Android's existing Google/email API contract compatibility verified with
its JVM tests; no Android UI feature is added in this prerequisite slice.

Use dedicated local test data only. Provider endpoints are mocked in automated
tests; a real Apple account/Hide My Email roundtrip remains an explicit external
gate. Do not deploy, publish iOS, alter Apple console settings, edit secrets,
merge existing accounts, or claim this change repairs old duplicates. Record
the migration/deployment implication in OAuth docs: older installed iOS builds
can still sign into known Apple identities, while first-time Apple creation
requires the updated confirmation UI (or web).

After review, the Android browser handoff can reuse this creation rule. That
handoff is a separate design with TLS callbacks, state/nonce validation,
transaction binding, PKCE and an atomic single-use exchange. Remaining owner
provider gates do not stop milestone 3 implementation.

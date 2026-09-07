# Android Milestone 2 Core: Onboarding And Account

## Intent and sequencing

Deliver native onboarding and account management on top of milestone 1's email
session and cached browsing. The approved milestone sequence is core → Google →
Apple. This specification covers the first independently verifiable slice;
provider slices get their own focused designs. Milestone 2 remains open until
the roadmap's real-provider and release-signed verification gates pass.

The user approved this order and authorized autonomous spec review, planning,
and subagent implementation. Use the existing checkout on a feature branch,
preserving unrelated skill changes. Executable tasks belong in GitHub issues.

## Product behavior

### First-run onboarding

Once secure session restoration resolves, a signed-out first-run user sees the
brand welcome, then household, recipe-saving habits, dietary preferences, and
embedded authentication starting in signup mode. A returning signed-in user
goes directly to their cookbook. Welcome offers an existing-account sign-in
shortcut so returning users on a fresh installation need not answer questions.

Keep the iOS labels and exact Rails values:

- Household: Just me / 2 / 3–4 / 5+, sent as 1 / 2 / 3 / 5. One choice required
  to continue.
- Saving habits: Screenshots, Browser bookmarks, Notes app, Paprika / Crouton /
  etc., Physical cookbooks, I don't, I forget them. Values are `screenshots`,
  `browser_bookmarks`, `notes`, `recipe_apps`, `cookbooks`, `dont_save`.
  Multiple selections are allowed; at least one is required to continue.
- Diet: Vegetarian, Vegan, Gluten-free, Pescatarian, Halal, Kosher, Lactose-free.
  Values are `vegetarian`, `vegan`, `glutenFree`, `pescatarian`, `halal`, `kosher`,
  `lactoseFree`. Multiple selections are allowed; an empty list means no
  restrictions and is valid.

Back preserves answers. Skip on a question completes onboarding, discards the
draft and pending submission, and opens standalone authentication. The welcome
sign-in shortcut has the same completion semantics. Completing the questions
persists the auth step; closing the app there resumes embedded authentication.
Successful authentication completes onboarding and removes the draft and
anonymous ID. Signing out or deleting the account does not repeat onboarding.

Persist the step and nonsecret answers after each user change, including across
process death. Passwords retain milestone 1's volatile-only behavior. If writing
onboarding state fails, show retry and continue-without-saving choices; do not
block authentication on this optional questionnaire. An existing valid session
always takes precedence over onboarding storage errors. Missing/corrupt draft
data resets only onboarding state, never the encrypted session or recipe cache.

### Answer submission and attribution

Use a random installation-local UUID for `device_id`, generated for an onboarding
draft, not a hardware identifier or a RevenueCat dependency. Scope the draft to
the configured API origin to avoid crossing development environments.

Submit the current complete answer snapshot to `POST /api/v1/onboarding_response`
without Authorization or cookbook headers. Rails upserts by `device_id`.
Questions themselves work offline. When entering the auth step, start one
best-effort submission. Before the subsequent email auth request, join that
submission within a total five-second submission budget. If no attempt has run
for the current snapshot, attempt it then. A submission failure never becomes an
authentication failure; the auth request still carries `onboarding_device_id`.
Do not let a detached submission run indefinitely or resubmit after successful
authentication, when Rails' one-time linking opportunity has passed.

An explicit later auth attempt may retry an unsuccessful onboarding submission
before authentication; no background retry loop or durable mutation outbox is
introduced. Retain the anonymous ID and draft through failed authentication,
including an auth-screen restart. Consume them only after a securely stored
session has been established, regardless of cookbook refresh success. A timeout
can leave an unlinked anonymous response if the server committed late; this
analytics-only contract is best effort, as on iOS, and must not create duplicate
accounts or block login. Do not log answers or identifiers.

### Account settings

Extend Settings with a name row, account-wide Recipe reminders switch, and Manage
Account destination. Retain sign out and the development gallery.

Name editing uses a native dialog/sheet with Cancel and Save. Trim whitespace;
require a changed nonempty name of at most the Rails limit of 50 characters.
Keep the draft and an actionable error after validation/network failures.

Recipe reminders changes `lifecycle_notifications_enabled` on the shared Rails
account. Explain that it applies across the account and Android delivery is not
enabled yet. This slice does not request notification permission or register a
device token. Show the acknowledged server value, disable duplicate submission
while saving, and preserve the old value after a definite server rejection.

Account updates are online operations with no automatic mutation retries. Use
the returned `user` as authoritative and retain the existing token and expiry.
Persist that updated user through the encrypted session store before reporting
the update fully saved. If Rails accepted the update but local persistence
failed, explain that distinction and offer an explicit save retry; never claim
the server rolled back. The current in-memory user reflects the accepted server
value. A successful retry makes it survive restart. Account error feedback must
not overwrite unrelated recipe-list/detail errors.

### Account deletion

Settings → Manage Account → Delete Account follows visible iOS behavior. Explain
that personal data and solo-owned cookbooks are deleted, shared cookbooks with
collaborators transfer ownership, and deletion does not refund a subscription.
Require the exact typed phrase `DELETE`, then one explicit Delete My Account
action. A confirmation draft must not automatically dispatch a request after
recreation.

Send one `DELETE /api/v1/account` with the captured bearer and no cookbook
header. While pending, disable duplicate account mutations and deletion; present
progress and keep the operation in the retained session lifetime across Activity
recreation. Preserve the existing no-automatic-retry HTTP policy.

On `204`, immediately hide protected content and run the established protected
cleanup of credentials, Room, and images; it must complete despite ordinary
coroutine cancellation before another account can enter. Do not issue redundant
logout revocation after successful deletion. Local cleanup failures use the
existing explicit recovery surface. `401` invalidates the session but is not
proof that deletion succeeded. Other HTTP errors preserve the session and show
retryable failure. Transport timeout/cancellation cannot prove whether Rails
committed: explain that deletion could not be confirmed, allow deliberate retry
or sign out, and do not falsely announce success or automatically replay DELETE.

This slice inherits the explicitly tracked cross-process failed-cleanup protocol
gap in #94; it does not claim that non-cancellable coroutines survive process
termination or broken storage. Exercise ordinary deletion and cleanup errors,
and keep that limitation visible in the handoff.

## Architecture and ownership

Keep one app module, current pins, manual application container, and package-by-
feature structure. Add small onboarding state/store/controller files under
`features/onboarding` and `data/onboarding`, plus focused account state and UI
under `features/settings`. Existing API models and client gain the three routes
and optional onboarding field on sign-in/sign-up. No Rails endpoint or wire
contract change is needed for this slice.

Use a small versioned JSON onboarding document under `noBackupFilesDir`, written
with Android `AtomicFile` on IO. It contains only origin, UUID, step, choices,
and completion state. Serialize writes so an older draft cannot overwrite a
later skip/completion; stale asynchronous callbacks cannot recreate consumed
answers. Unknown JSON fields are tolerated and known values validated. No Room
migration or new persistence framework is needed.

The application ViewModel composes onboarding with the existing session
controller. Session restoration remains once-per-ViewModel and precedes routing;
optional onboarding loading must not reveal a first-run screen over a restoring
session. Existing sessions mark onboarding complete for future signed-out use.
Keep form state stable across loading/error transitions.

Account operations belong to the authenticated lifetime, with explicit small
account operation state. Serialize account mutations and guard results by
session generation/user identity. Cookbook switching must not cancel a valid
account update, and no account route sends `X-Cookbook-Id`. A late response or
encrypted-store write must never resurrect an old account after cleanup or
overwrite a subsequent login.

Targeted session identity adjustment is required: today `isCurrentLocked` uses
reference equality of the entire `SessionResponse`. Updating profile fields
must not stale all active recipe work. Use the existing user generation plus
stable authenticated session identity (user ID and unchanged token) to determine
ownership; profile replacements preserve that identity, while cleanup/relogin
changes the generation. Keep credential-store write ordering coordinated with
cleanup, and never expose token-based identity in public UI state.

Account operations must not hold the main transition mutex across HTTP calls.
Use existing tracked-job and cancellation conventions, capturing the cleanup
owner job to avoid self-joins. Keep request admission explicit during account
deletion, including interaction with a racing authenticated `401` and logout.
Avoid a general coordinator rewrite; extraction should directly support these
new responsibilities.

## Native presentation

Use existing MainCourse light-only tokens, native sans, Plex Mono numerics,
Material Symbols, and 8/10/12dp radii. Preserve brown cookbook branding. Use
native Material fields, choice chips, dialogs, progress, and Back behavior.
Onboarding and account forms are scrollable and width-constrained on tablets;
keyboard and 200% text must not hide actions. Retain the adaptive four-tab shell.
Accessibility includes selected-state semantics, labeled controls, error
announcements, and touch targets. No new visual design direction is required.

## Verification and acceptance

Meaningful JVM tests cover exact wire payloads/header isolation, optional-field
omission, `204`, errors and no retries; onboarding advancement/skip/resume and
submission-before-auth ordering; stable profile identity; concurrent
update/switch/logout/deletion; persistence failures; and deletion ambiguity.

Device tests cover AtomicFile draft restart/recovery and Compose welcome,
questions, validation, embedded/standalone auth, updated Settings, deletion
confirmation, and Activity/fresh-ViewModel restoration. Preserve existing
milestone 1 regression suites.

Live acceptance uses real MainActivity, Keystore, Room, and local Rails with
dedicated accounts: first-run answers linked to signup, skip, auth failure/retry,
process restart, existing-session upgrade, name/preference restart persistence,
offline failures, deletion and subsequent sign-in failure, and shared-data
ownership preservation. Inspect compact/tablet layouts and 200% text. Restore
the emulator settings and leave a usable dedicated local account installed.

Run Android debug/release builds, JVM/device tests, and both lint variants.
Run existing Rails onboarding, registration, session, and account contract tests;
expand affected Rails checks if a genuine backend change becomes necessary.
Pure Android implementation does not require an unchanged iOS build. Record
commands and results in the issue and update durable Android conventions and the
roadmap after review; do not mark full milestone 2 complete from this core gate.

## Next slices

Google uses Credential Manager and Rails' web/server audience with real console
and signing-identity verification. Apple needs a browser handoff and a deliberate
resolution to #86 before enablement. Their configuration and release-signed
gates remain tracked in #92 and the roadmap. Provider tasks can proceed after
this slice without Play registration; owner-controlled verification may remain
open while other product work continues.

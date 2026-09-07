# Android Milestone 1: Email Session And Cached Recipe Browsing

## Goal and approval

Build the first usable Android vertical slice against the existing Rails API:
email signup/sign-in → cookbook discovery/switching → recipe list/detail, with
offline access to previously fetched content. The user approved this approach
on 2026-09-07, including on-demand detail caching and emulator-first development
while Play registration is unavailable.

The milestone requires no Play, Google OAuth, Apple, Firebase, RevenueCat, or
physical-phone setup. The long-horizon roadmap remains
`docs/superpowers/plans/2026-09-07-native-android.md`; executable work belongs in
GitHub issues. This document records the design, not an implementation checklist.

## Scope

- Email signup and sign-in with validation, loading, retry, and useful errors.
- Secure persistence/restoration of the Rails user, opaque token, and expiry.
- Logout and expired/revoked-session handling.
- Cookbook discovery, persisted selection, switching, and membership-loss recovery.
- Cached adaptive recipe cards, refresh, images/placeholders, and recipe detail.
- Detail shows name, cover, prep/cook times, servings, ingredients, instructions,
  and notes. Missing optional fields have sensible presentation.
- Offline list access and offline detail for recipes previously opened.
- A minimal signed-in Settings surface showing the account and sign-out action.
- Preserve the four existing top-level destinations and the development gallery.
  Shopping and Search remain explicitly labeled previews until their milestones.

Onboarding, provider authentication, account editing/deletion, search, imports,
recipe mutations, shopping, subscriptions, and notifications retain their later
roadmap milestones.

## Architecture

Keep one `:app` module and the existing Compose/Navigation 3 shell. Organize code
into small auth, cookbook, recipe, and shared data packages. Use manual
application-level dependency construction; a dependency-injection framework is
not needed at this scale.

- **HTTP boundary:** OkHttp with cancellable coroutine calls and
  kotlinx.serialization JSON. DTOs mirror the Rails wire contract. Map transport,
  HTTP, validation, and decoding failures into explicit app errors. Configure
  finite timeouts, disable automatic connection-failure retries for mutations,
  and do not follow API redirects with bearer credentials.
- **Session store:** an Android Keystore AES-GCM key encrypts an atomically
  written private session file. Store the token, user, expiry, and API origin
  together; no plaintext credential preferences, saved-instance state, logs, or
  Room token columns. Decryption failure clears unusable state and requests login.
- **Room:** stores user-scoped cookbooks, selected cookbook, list summaries,
  fetched details, and cache metadata. Queries and primary keys include user ID
  and cookbook ID where applicable. Schema export begins at version 1 without a
  destructive-migration fallback. API-base-URL changes invalidate the old session
  and caches so local and production users cannot share accidental identities.
- **Repositories:** own persistence and remote reconciliation; screens never
  call HTTP or discover cookbook context independently.
- **Session coordinator/view model:** owns observable startup/auth state and
  the authenticated coroutine lifetime. It resolves cookbook context and manages
  switching, refresh, logout, and session invalidation. Compose receives state
  and actions; activity recreation retains ongoing work through the view model.
- **Images:** Coil uses a user/session-owned image loader/cache. Requests do not
  inherit the API bearer header, including for remote cover URLs. Logout and
  account changes dispose the loader and clear its disk/memory data after
  cancelling old requests. Switching cookbooks removes old images from view.

Keep contracts small and inject HTTP, persistence, clock, and coroutine behavior
where needed for meaningful tests. Pin resolved dependencies compatible with the
existing toolchain rather than upgrading the scaffold as part of this feature.

## Rails contracts

| Action | Existing endpoint | Behavior |
|---|---|---|
| Sign up | `POST /api/v1/registration` | Send name, email, password, password_confirmation, device_name; 201 session or 422 errors |
| Sign in | `POST /api/v1/session` | Send email, password, device_name; 201 session or 401 invalid credentials |
| Sign out | `DELETE /api/v1/session` | Revoke the current bearer token |
| Discover cookbooks | `GET /api/v1/cookbooks` | Complete accessible-cookbook array, independent of active cookbook |
| Recipe list | `GET /api/v1/recipes` | Complete unfiltered summary array for `X-Cookbook-Id` |
| Recipe detail | `GET /api/v1/recipes/:id` | Full detail within `X-Cookbook-Id`; 404 if absent |

Auth responses contain `token`, `expires_at`, and `user` with ID, name, email,
and notification preference. Tokens last 90 days and have no refresh flow.
Authenticated requests use `Authorization: Bearer …`. Cookbook-scoped requests
always carry a resolved `X-Cookbook-Id`; discovery and logout omit stale context.
Login's 401 is a form error, not an authenticated-session cleanup event.

The list endpoint is authoritative for membership in the list, but contains no
ingredients/instructions. Detail GETs are partial cache updates and cannot prune
other recipes. No batch cursor is needed for this slice. Existing pending/failed
imports from other clients show a status rather than masquerading as ready
recipes; Android import actions and polling remain later work.

The existing API is sufficient. Change Rails only if implementation uncovers an
actual contract gap, and then protect affected shared behavior with API tests.

## Startup, isolation, and failure behavior

1. Restore the encrypted session. If absent, corrupt, origin-mismatched, or
   expired, clear protected state and show authentication. Never render a cached
   account before resolving this step.
2. Establish the authenticated user scope and load only its cached cookbooks.
3. Refresh accessible cookbooks. Preserve an accessible persisted selection;
   otherwise choose the personal cookbook, then an accessible fallback. Persist
   the resolved choice before cookbook-scoped requests.
4. Display that cookbook's cached recipe list and attempt a refresh. Network
   failure becomes a visible cached/degraded or retryable empty-error state,
   rather than an indefinite startup splash.

Offline startup can use a nonexpired session and cached cookbook selection. With
no cached cookbook, show a retryable startup error and sign-out action. Remote
revocation or membership loss cannot be detected offline; the next authoritative
response reconciles it. An authenticated 401 clears protected state and returns
to sign-in; expiry is checked when resuming/using the session as well as startup.

A cookbook switch changes the displayed scope synchronously, clears detail
navigation, and cancels old cookbook work. Each request captures immutable
session/user/cookbook context; generation checks prevent late successes or errors
from modifying a newer scope. Cancellation must propagate rather than becoming a
user-facing server failure. Validate the request generation and perform cache
writes under serialized coordination so logout cannot race a late write.

On cookbook 403, immediately hide/purge the rejected cookbook's protected cache,
rediscover memberships, and resolve an accessible selection. If rediscovery
fails, retain a retryable state without redisplaying forbidden content. Do not
silently retry the same forbidden cookbook forever. On detail 404, remove the
stale recipe and return a clear unavailable state.

Logout first ends the old authenticated work lifetime and removes visible
content. Attempt remote revocation with a bounded timeout, and always clear the
local encrypted session, Room product cache, selection, and image caches. Offline
logout guarantees local removal but cannot promise server revocation; do not
retain an offline token-revocation queue. Cleanup completes before another
account is admitted. Password fields are never persisted across process death.

## Cache reconciliation and detail policy

Commit full list refreshes transactionally within the captured scope. A successful
empty response clears the list; transport, malformed, partial, or failed responses
never clear it. Authoritative cookbook discovery removes caches for memberships
no longer returned. Removing a recipe also removes its stored detail.

Cache detail on opening a recipe; show cached detail immediately and refresh when
online. Track the detail's own server `updated_at` independently from summary
refreshes. A newer summary must not falsely mark an older detail current. Offline
stale detail remains readable with an explicit saved/offline indication. If no
detail was saved, retain context and offer a connection/retry message rather than
inventing empty ingredients or instructions. Image cache availability is best
effort; placeholders remain valid offline.

Cache-first/on-demand detail is preferred over network-only browsing because it
meets the offline goal, and over eagerly downloading every detail because it
avoids an unnecessary initial sync and its failure modes. There is no mutation
outbox in this milestone.

## UI and navigation

Use existing MainCourse semantic tokens, light appearance, native Material
controls, Plex Mono numeric roles, and compact/rail adaptive navigation. Sign-in
and signup forms scroll with keyboard/large text and prevent duplicate submits.
Use resource strings, accessible labels, and native password autofill semantics.

Recipes show the active cookbook selector, adaptive cards, refresh, empty state,
and concise stale/error feedback with retry. Recipe detail is a typed navigation
destination carrying cookbook/recipe identity, with system and toolbar Back.
Restored navigation is validated against the restored user/cookbook; switching
scope invalidates incompatible entries. No auth token or password enters route
arguments. Settings provides the account identity and sign out. Keep the gallery
accessible through the development surface.

## Verification and acceptance

- JVM tests exercise HTTP payloads/headers, API errors, session expiry, startup
  selection, generation guards, and cancellation without requiring live services.
- Room instrumentation tests exercise restart persistence, user/cookbook
  isolation, transactional full replacement, partial detail upserts, and cleanup.
- Secure-store device tests cover round trip, absence of plaintext token storage,
  corruption/key failure handling, and removal.
- Compose tests cover signup/login validation, loading/error recovery, cookbook
  switching, list/detail, logout, Back/recreation, and compact/rail behavior.
- Integration verification uses local Rails and dedicated development users with
  personal/shared cookbooks and recipes. Exercise actual email auth, restart,
  offline cached start/detail, switching, empty content, server failure, 401, and
  membership 403. Record phone/tablet emulator evidence explicitly as emulator
  evidence and include large-text checks.
- Run Android build/unit/lint/instrumentation checks, including a minified
  unsigned release build. Run affected Rails tests for integration contracts;
  broader Rails/iOS checks are required if shared code changes.

The milestone is complete only when the roadmap's user flow and isolation/error
gate are verified. Play/provider setup and physical-device release validation
remain independent follow-ups in #92, not prerequisites for this acceptance.

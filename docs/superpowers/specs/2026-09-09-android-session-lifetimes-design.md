# Android Structured Session Lifetimes

## Evaluation outcome — revised scope

The hierarchical prototype (`457fb64`) removed seven registries but added 203
production lines, duplicated launch scaffolding, and introduced extra recovery
handoffs and timing dependencies. Review found that it did not meet the user's
library-first simplification goal. It is not the selected implementation.

Retain the existing ViewModel-parented coroutines and selective cancellation
groups. Keep only the independently demonstrated safety improvement: reject
late authentication, recipe/action and image results when the injected root Job
is no longer active. Preserve focused root-disposal regressions, and restore the
original TestScope-based test setup rather than hiding active work in
backgroundScope. Public API/state/behavior and existing generation/write guards
remain unchanged. No new dependencies or ownership framework are introduced.

The hierarchy below is the evaluated proposal, retained as design history, not
an implementation requirement. #104 completes with the smaller guard hardening
and this documented decision; reconsider child-lifetime restructuring only if
it provides a concrete maintenance reduction. Imports/sharing proceeds next.

## Goal

Apply the library-first review in #104 before imports/sharing adds more session
work. Replace overlapping manual Job registries with explicit parented coroutine
lifetimes using the existing kotlinx-coroutines 1.10.2 dependency. This is an
internal ownership refactor, not a new architecture framework or product change.
The user authorized autonomous specs, subagent implementation and reviews.

## Current problem

SessionController maintains sets for authenticated, catalog, cookbook, detail,
recipe refresh, hydration and image-preparation work. Tasks are registered in
multiple sets, removed by completion handlers, snapshotted and cancelled with
special owner exclusions. Coroutines already maintain a parent/child lifetime
graph. The duplicated graph makes teardown ordering harder to reason about and
has grown with each feature.

The underlying MainCourse requirements remain real: user/cookbook isolation,
captured-scope mutations, stale-result rejection, cancellation-safe local
cleanup, and acknowledged-write reconciliation. Preserve these rules and their
existing regression coverage rather than replace them with another framework.

## Target hierarchy

Keep the injected ViewModel scope as the root. Every created Job must have an
explicit parent from that root; no GlobalScope, unparented application scope,
or replacement Job passed to launch that detaches the coroutine.

- An admitted authenticated session owns a `SupervisorJob(rootJob)` and scope.
- Catalog discovery, account operations and recipe mutations belong to the
  session lifetime. Posted mutations and account saves survive cookbook switches
  in their captured scope, with publication guarded by current scope.
- A replaceable cookbook lifetime is a child of the session. Detail, refresh,
  hydration and image preparation belong under it. Search's existing single
  replaceable job must be cancelled/joined with the cookbook/session lifetime.
- Refresh replacement cancels the list/sweep group, not independent detail
  fetching. List-refresh completion still does not await the background sweep.
- Use explicit replaceable task/group references for catalog, detail, refresh
  and image preparation where needed. Coroutines own membership; remove the
  seven overlapping `MutableSet<Job>` registries and multi-registration helpers.

One small internal lifetime helper is acceptable if it reduces duplication.
Avoid a generic task registry, event bus, command framework or parallel second
ownership graph. Evaluate production lines across helper plus controller, not
just the smaller controller file; removing registries matters more than moving
code between files.

## Teardown placement and completion semantics

A child must never cancel-and-join a parent that is waiting for that child.
Move full cleanup orchestration to a root-scope command. Before cancelling work,
atomically close admission, invalidate generations and hide protected state.
Detach the old session lifetime from the active slot, cancel-and-join it, then
perform existing credential/Room/display-image/staged-image cleanup under
NonCancellable. Clear provider selection using its existing bounded best-effort
contract. Cleanup failures still expose recovery and block a new account.

Authenticated `401` and accepted account deletion can originate inside a
session child. Hand that outcome to a parent/root command, allowing the child
to finish before the parent is joined. Keep this private and small. Public
`Job.join()` contracts must still include required cleanup/recovery, not merely
enqueue it and return. Explicit cancellation after cleanup admission must not
skip local deletion; retain existing owner-cancellation tests.

Cookbook `403`, detail `404`, and scope replacement have the same ancestry
problem. Prefer recovery orchestration under the session/root parent after the
failing cookbook child has ended. If a small partial-teardown path needs an
explicit owner exclusion, document why; do not recreate a set-based registry.
Retiring parents must eventually complete and remain parented to the session.

Anonymous authentication/restore/cleanup commands remain root-owned, with their
existing singleton admission and explicit cancellation semantics. Their child
workers and joins must not allow a late auth/session-store write after cleanup.
ViewModel disposal cancels all derived lifetimes; ordinary Activity recreation
keeps the retained ViewModel and its valid in-flight work.

## Invariants that do not move

- Public controller/ViewModel actions, StateFlow shapes and observable completion
  timing remain compatible with the reviewed core recipe UI and provider flows.
- Keep user generation/token identity, cookbook/detail/catalog/hydration/query
  request versions and commit predicates. Cancellation alone cannot prevent a
  non-cooperative late response from publishing.
- Keep `credentialTransition`, CatalogRepository's serialized writes, and the
  read/mutation barrier unchanged. The barrier's own read registry is deliberate
  and is not part of this refactor.
- No network request runs under a state/cache mutex. Cleanup must join old
  writes before clearing the stores.
- Cookbook switching still cancels old read/image work but allows an accepted
  mutation's captured source/target cache reconciliation while its user lifetime
  remains valid. It must not publish source feedback into another scope.
- Preserve no automatic mutation retry, pending-purge hiding/recovery, photo
  ownership and retry, stable ingredient IDs, and offline search/hydration.
- No changes to HTTP/Retrofit/Room schema, auth protocol, UI features, import
  behavior, provider configuration, or the unrelated #105 feedback edge case.

## Verification

Use the existing focused SessionController and RecipeActionController suites
as the primary behavioral oracle. Add targeted proofs for the lifetime graph,
not tests that merely assert an implementation field is named differently:

- ViewModel/root disposal cancels session, cookbook, hydration, image and search
  work; no child survives through a detached parent.
- Logout/deletion join non-cooperative reads/writes, including same-user login
  afterward; no stale write/publication or cleanup self-join.
- Public returned jobs await required cleanup on `401`, accepted deletion,
  restore failure and cancelled logout.
- Cookbook switch cancels its reads/images/search, preserves posted recipe and
  account mutations, and leaves the new scope usable.
- Replacing list refresh does not cancel an independent detail; background
  hydration stays non-blocking and superseded status updates cannot win.
- `403`/`404` recovery can replace a cookbook lifetime without joining its own
  ancestor, duplicating cleanup, or stranding LOADING/HYDRATING state.
- Existing mutation/read barrier, pending purge, photo ownership, editor retry,
  Google chooser and Apple browser lifetimes remain green.

Run Android JVM tests, debug/release lint and builds, and the device suite with
a fresh no-build-cache gate. Perform a short real MainActivity/local Rails
smoke: restore, search/detail, switch while reading, edit, logout and re-login.
Use dedicated development accounts and restore emulator defaults. No backend
code change is planned, so unchanged Rails/iOS suites need not be rerun.

Record the ownership graph and measured change in docs/android.md and #104.
Keep credentials, Firebase/provider JSON, user skill changes and existing
temporary test assets untouched. After review, continue directly to the
milestone 3 import/share slice; do not pause for owner-only provider gates.

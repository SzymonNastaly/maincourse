# Android Milestone 3 Core Recipe Workflows

## Scope and sequencing

Deliver cookbook-scoped local search, editing and cover replacement, move/delete,
serving scaling, ingredient review/add, and cooking mode. Imports, camera intake,
and Android share targets follow as a separate milestone 3 slice. The user has
authorized autonomous specs, subagents, reviews, and continuation; do not wait
for milestone 2's real-provider/signing/App Link gates.

Keep one app module, the existing pinned toolchain, manual container, native
Material/MainCourse light-only styling and four destinations. Search becomes a
real destination; full Shopping UI/offline outbox remains milestone 4. Adding
reviewed ingredients is an online recipe action visible to web/iOS users now.
No hidden meal-planning, tags/favorites UI, or cover-removal feature is added.

## Search and cached detail refresh

Search works locally over recipe name, ingredient names/raw fallback, and
instructions. Normalize case/diacritics (including common Latin special letters),
rank name matches above ingredients above instructions, and return up to 50
results. Require all query terms to match; use prefix and bounded typo matching
as a fallback when direct matches are absent. Empty query shows the search
prompt, not all recipes. Failed imports are excluded; pending summaries may
match by name and retain their non-ready appearance. Results open the same
detail/action routes as the Recipes tab.

Use a pure in-memory scorer over scoped Room data, not another database/FTS
module. Query/scoring runs off Main and stale queries/results are cancelled or
version-rejected. Clear visible results immediately when account/cookbook scope
changes; retain only that scope's saved query. No other cookbook's text can
appear while a new scope resolves.

After a successful authoritative recipe-list refresh, run a cancellable,
non-blocking detail sweep using `GET /api/v1/recipes/batch?limit=100&cursor=…`.
Start at no cursor each sweep and stop on an empty page; a final nonempty page
still has a cursor. Reject repeated/malformed cursor progress and use a finite
two-minute sweep budget. Superseding refresh, switching cookbook, logout and
mutations invalidate old sweep work. Name search is immediately available;
ingredients/instructions arrive as details are cached. Failure shows a modest
incomplete/offline search indication and leaves cached results usable.

A full sweep is deliberate at this app's scale: ingredient parsing currently
changes child rows without advancing the recipe cursor revision (#102). Do not
persist a cursor or skip a fetched body solely because updated_at is unchanged.
Batch results are partial updates, never deletion authority. Cache only rows
belonging to the captured known membership/list, with completed import status;
new/unrecognized or pending rows wait for the next full list. Update details and
derived summary fields consistently, never overwrite a newer mutation with an
older read, and prune only from a successful full recipe-list response.

This extends milestone 1's on-demand caching: successfully swept completed
recipes can now open offline even if never opened before. Details that were
neither swept nor explicitly fetched still require a connection. Record this
changed cache convention in the Android guide.

## Recipe editing

Add Edit to a completed detail's actions. The editor exposes name, prep/cook
minutes, servings, ordered raw ingredient rows, ordered instruction rows, notes,
source URL, and cover add/replace via Android Photo Picker. Add/remove/reorder
rows with labeled native controls. Keep full original values until the user
changes them; trim rows and remove blank entries when saving.

Validation: nonempty trimmed name; optional nonnegative integer minutes;
optional positive integer servings; empty values clear nullable server fields.
Nonempty source URLs must be HTTP(S) with a host and no embedded credentials.
Explain invalid input rather than silently dropping it. Only changed, valid
drafts may save. There is no maximum title length invented beyond the API.

Send a JSON `PATCH /api/v1/recipes/:id` containing the complete editable text
snapshot. Nullable fields must explicitly encode null so clearing works; empty
ingredient/instruction arrays must encode `[]`. If a new cover is staged,
upload it afterward in a second multipart PATCH with part `cover_image`, matching
the existing API/iOS contract. Two requests are necessary to preserve empty-array
and null semantics without inventing a new multipart wire format.

After text success, reconcile cache/list/detail immediately. If cover upload
fails, explicitly say the details were saved but the photo was not confirmed;
retain the local image and offer photo-only retry, without repeating the text
PATCH. A transport failure may have committed; do not claim rollback or replay
automatically. When the response is acknowledged but local cache persistence
fails, offer refresh/local reconciliation instead of resending the mutation.

Keep nonsecret editor fields, row identities/order, and operation markers in
scoped saveable state across Activity/process recreation. Stage a photo into
app-private cache and save only the private path/key, never a temporary external
URI grant. Missing/evicted staged images produce a choose-again state while text
is retained. Do not serialize full network credentials or auto-replay a save
after recreation. A restored interrupted operation shows unconfirmed state;
the retained in-process operation, when present, remains the single owner.
Cancel discards the draft; disable editor navigation/double-submit during a
finite active save, then restore normal Back/error handling. Logout/lost scope
disposes the UI and removes staged user files.

Photo preparation uses the platform ImageDecoder and streams an input copy with
a 32 MiB bound. Check dimensions before decode (at most 100 megapixels and
32,000 pixels on either side), downsample the long edge to at most 3,000 pixels,
normalize orientation through ImageDecoder, and encode JPEG below 15,000,000
bytes (server limit is 15 MiB). Decode/compress/copy run off Main; unsupported
images fail visibly and intermediate files are removed. No broad storage or
camera permission is needed for cover Photo Picker. The helper can be reused
by the subsequent import slice.

## Move and delete

Expose actions from completed recipe cards/search results and detail. Native
confirmation identifies the recipe; Move lists other accessible cookbooks and
keeps the source header captured throughout `PATCH { cookbook_id: target }`.
The response lacks cookbook_id, so use the captured target for reconciliation.
Disable moving/editing pending or failed imports. Failed dismissal is part of
the import slice.

Wait for server acknowledgement before removing a recipe locally. Successful
delete `204` and delete `404` remove the known source row; only a successful
move removes the source and upserts the known target as a partial result. A
move's `404` is not proof of success and must not invent a destination. Network
or validation failures leave visible content plus actionable feedback. An
ambiguous move/delete offers refresh/check-state and a deliberate later action,
never automatic replay. Keep list/detail/search and cookbook counts coherent
through local effects plus bounded authoritative refresh.

If local removal fails after server success, hide the known removed source row
in-process, use the established purge recovery, and block conflicting mutations
until it resolves. This does not claim to solve the cross-process durable-purge
gap already tracked in #94. Preserve earlier degraded feedback when a detail404
settles an interrupted refresh, and give restored detail a retry/back surface
when cookbook resolution has failed (#96).

## Servings, ingredient review, and cooking

Enable scaling only for positive base servings. Current portions range 1–64,
default to base (bounded for the control), and remain a local detail preference;
do not PATCH the recipe merely by changing portions. Use BigDecimal for the
ratio and amounts. Match iOS formatting: common fraction glyphs when the entire
value is within strictly 0.005 of 0.25/0.5/0.75/0.3333/0.6667/0.125/0.375/
0.625/0.875; otherwise round HALF_UP to two decimals, strip trailing zeros,
use a decimal point without grouping, and use an en dash for ranges. Display
quantity + unit + name + optional note; use the raw ingredient when structure
is absent/unusable. Do not fabricate scaled quantities from arbitrary raw text.

Review ingredients before adding: all rows initially included, per-row native
checkboxes can exclude them, and the action reads Add N items. Parsed rows send
name plus details made from scaled quantity/unit and note joined with a comma;
unparsed rows send raw name and no fabricated details. Generate stable per-row
UUID client IDs for one review submission and retain them with the exact posted
snapshot across recreation/ambiguous retry.

Online `POST /api/v1/shopping_list_items` sends
`{ items: [{client_id,name,details,checked_at:null,source_recipe_id}] }` with the
captured cookbook. On `201`, show the acknowledged added count. On definite
rejection retain review and explain errors; API errors may contain objects, so
the shared error decoder must preserve `error`/`error_code`/`limit` even when
`errors` is not a string array (also needed by the import slice). Freeze the
submitted snapshot during an ambiguous attempt and allow an explicit identical
retry with the same IDs; do not queue or retry automatically. The server upsert
provides duplicate-safe identity for this action, not a general offline outbox.

Cooking mode toggles FLAG_KEEP_SCREEN_ON only while this recipe's detail is
visible and active. Clear it on Back, tab/scope change, logout and disposal;
restore a local toggle across rotation without letting a stale recipe keep
another screen awake. No timer/widget/App Intent feature is added.

## Ownership and structure

Keep network mutations, search and sweep lifetimes under authenticated session
ownership. Every request captures user generation, bearer and cookbook; every
cache write rechecks current scope/mutation ownership. Read sweeps and refreshes
started before an acknowledged mutation cannot overwrite it. Cancel/join or
version-barrier stale work before the commit, without holding state locks over
HTTP. Logout cancels/joins actions before clearing Room and staged files; late
results cannot recreate prior-account data. Cookbook switch may allow a posted
mutation to finish in its captured scope, but never publish into the new scope.

Add focused `features/search`, recipe action/editor/formatting files, and a
small image-preparation owner. Extract mutation orchestration to a dedicated
RecipeActionController with narrow session-owned hooks; do not grow every new
operation inside SessionController or introduce a general workflow framework.
Use existing Room JSON records and membership keys; no search schema migration
or persistent cursor is required. Saveable draft state is user/cookbook/recipe
scoped and carries no bearer. Keep app-resource cleanup integrated with the
existing explicit cleanup-failure state.

## Verification and handoff

Meaningful JVM tests cover wire null/array/multipart/header contracts, tolerant
error bodies, search normalization/ranking/typos, fraction/range arithmetic,
batch termination/staleness/cancellation, mutation/read/logout/switch races,
partial-photo and local-cache failures, stable-ID ingredient retries, and
absence of automatic mutation replay. Real Room tests cover partial upsert and
summary/detail consistency, empty full pruning, membership/target isolation and
late-response guards. Image device tests cover bounded copying, orientation,
downsample, cleanup and malformed data.

Compose/device tests cover Search, editor validation/order, Photo Picker result
recreation, move/delete confirmations, portions/review/cooking, failures and
fresh-ViewModel restoration. Inspect phone/tablet, keyboard and 200% text.
Live MainActivity/local Rails uses dedicated fixtures for search beyond names,
offline cached detail, edit and photo success/partial failure, move between two
books, deletion, adding selected scaled ingredients without duplication, and
cooking flag cleanup. Use existing Rails recipe/shopping contract suites.

Run Android clean debug/release build/lint/JVM/device gates and affected Rails
checks; unchanged iOS code does not need repeated builds solely for parity
reference. Record durable cache/search/mutation conventions and issue evidence.
Then proceed to milestone 3 imports/sharing. Real-provider gates remain open and
user Firebase configuration remains untouched for the later notification slice.

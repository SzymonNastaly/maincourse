# Task 2 report — durable scoped cache and encrypted session

## Outcome

Implemented the Android-free `SessionStore` and `CatalogStore` contracts, an
Android Keystore/AtomicFile session implementation, and a Room v1 catalog cache.
The cache keys product data by user/cookbook scope, records authoritative empty
recipe fetches, preserves retained detail JSON and its independent `updated_at`,
and cascades recipe/membership removals through details, fetch state, and selected
cookbooks. Relative image paths are serialized and restored unchanged.

Pinned KSP 2.3.11 and Room 2.8.4 without changing Kotlin 2.2.10, AGP 9.4.0,
SDK levels, or JVM 17. KSP and Room's Kotlin code generation ran successfully
with AGP's built-in Kotlin. Room schema v1 is exported under `app/schemas/` and
the database builder has no destructive-migration fallback.

## TDD evidence

### Initial RED

After adding only Room/KSP configuration and the two instrumentation suites:

```text
ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SERIAL="emulator-5554" \
  ./bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.data.cache.RoomCatalogStoreTest,com.getmaincourse.app.data.session.EncryptedSessionStoreTest \
  --console=plain

> Task :app:compileDebugAndroidTestKotlin FAILED
Unresolved reference 'MainCourseDatabase'.
Unresolved reference 'CatalogStore'.
Unresolved reference 'EncryptedSessionStore'.
BUILD FAILED in 43s
```

This was the expected missing-implementation failure. It also established that
the pinned KSP/Room plugins configured and reached Kotlin test compilation.

During self-review, an atomic-recovery regression test was added and observed
failing before its fix:

```text
tests="6" failures="1"
interruptedAtomicWriteRecoversTheAuthenticatedBackup
expected:<StoredSession(...)> but was:<null>
```

Removing the premature base-file existence check lets `AtomicFile.openRead()`
restore an interrupted write's authenticated backup.

Intermediate feedback was not hidden: the first implementation compile exposed
an inferred non-`Unit` `clear()` return, and the first secure-suite launch exposed
a non-void expression-bodied JUnit test. Both were corrected before behavioral
GREEN runs.

### GREEN

Focused API 37 instrumentation on `MainCourse_Phone_API37` / `emulator-5554`:

```text
# RoomCatalogStoreTest
BUILD SUCCESSFUL in 3s
7 tests, 0 failures

# EncryptedSessionStoreTest
BUILD SUCCESSFUL in 5s
6 tests, 0 failures
```

The Room suite covers compound user/cookbook isolation, two cookbooks, preserved
relative image paths, never-loaded versus authoritative empty, full replacement,
retained detail freshness, no invented summary on detail save, direct recipe and
cookbook removal, membership cascade, selection persistence, reopen, and clear.

The secure suite covers encrypted round trip, absence of plaintext token/origin/
email, altered ciphertext, missing key, unusable-record cleanup, explicit clear,
visible write failure with no fallback, and interrupted AtomicFile recovery.

Final host checks (each run once after focused instrumentation):

```text
ANDROID_HOME="$HOME/Library/Android/sdk" ./bin/android-test
BUILD SUCCESSFUL in 7s
# testDebugUnitTest + lintDebug

ANDROID_HOME="$HOME/Library/Android/sdk" ./bin/android-build
BUILD SUCCESSFUL in 611ms
# assembleDebug
```

`git diff --check` also completed with no output.

## Changed files

- `.superpowers/sdd/android-milestone-1-plan/task-2-report.md`
- `maincourse-android/build.gradle.kts`
- `maincourse-android/gradle/libs.versions.toml`
- `maincourse-android/app/build.gradle.kts`
- `maincourse-android/app/schemas/com.getmaincourse.app.data.cache.MainCourseDatabase/1.json`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/session/SessionStore.kt`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/session/EncryptedSessionStore.kt`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/CatalogStore.kt`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/CatalogEntities.kt`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/CatalogDao.kt`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/MainCourseDatabase.kt`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/RoomCatalogStore.kt`
- `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/data/cache/RoomCatalogStoreTest.kt`
- `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/data/session/EncryptedSessionStoreTest.kt`

## Downstream signatures and concerns

The public, Android-free contracts are exactly:

```kotlin
data class StoredSession(val baseUrl: String, val response: SessionResponse)
interface SessionStore { suspend fun read(): StoredSession?; suspend fun write(session: StoredSession); suspend fun clear() }
data class RecipeScope(val userId: Long, val cookbookId: Long)
data class CachedRecipes(val items: List<RecipeSummary>, val fetched: Boolean)
interface CatalogStore {
    suspend fun cookbooks(userId: Long): List<Cookbook>
    suspend fun replaceCookbooks(userId: Long, items: List<Cookbook>)
    suspend fun selectedCookbookId(userId: Long): Long?
    suspend fun selectCookbook(userId: Long, cookbookId: Long)
    suspend fun recipes(scope: RecipeScope): CachedRecipes
    suspend fun replaceRecipes(scope: RecipeScope, items: List<RecipeSummary>)
    suspend fun detail(scope: RecipeScope, recipeId: Long): RecipeDetail?
    suspend fun saveDetail(scope: RecipeScope, detail: RecipeDetail)
    suspend fun removeRecipe(scope: RecipeScope, recipeId: Long)
    suspend fun removeCookbook(scope: RecipeScope)
    suspend fun clear()
}
```

Concrete construction is `EncryptedSessionStore(context)` and
`RoomCatalogStore(MainCourseDatabase.open(context))`. The default protected file
is `noBackupFilesDir/session.enc`, and the default Keystore alias is
`com.getmaincourse.app.session`. Test-only isolation is supported by optional
`keyAlias`, `fileName`, JSON, and IO-dispatcher constructor arguments.

Room foreign keys intentionally require cookbook membership to be stored before
selecting it or caching recipes for it. This matches the startup sequence; a
missing membership fails rather than creating an accidental scope. Complete
cookbook replacement and `removeCookbook` cascade the scoped rows and selection.
Recipe reads and full replacements are transactional snapshots; Room suspend
operations retain coroutine cancellation/rollback behavior. Session operations
are serialized by a cancellable mutex, run on `Dispatchers.IO`, and explicitly
rethrow cancellation rather than treating it as corruption.

API-base-origin mismatch/expiry policy remains coordinator work; this task stores
the origin and response together so that policy can be applied. No UI,
coordinator, backup rules, provider configuration, credentials, or unrelated
pre-existing skill-file changes were touched.

## Review round 1 — 2026-09-07

### Changes

- Narrowed encrypted-session invalidation to missing/permanently invalid keys,
  invalid framing, AEAD authentication failure, and serialization corruption.
  Genuine absence returns `null`; transient KeyStore/provider/non-absence file IO
  failures propagate and leave the encrypted record available for retry.
- Session and cache JSON now always use `ignoreUnknownKeys = true`, including
  when callers supply a base `Json` configuration.
- Added a minimal internal key-lookup lambda used only to exercise transient
  AndroidKeyStore failure. The public `SessionStore` and constructor remain
  unchanged.
- Added KDoc to `CatalogStore.selectCookbook` and `replaceRecipes`: both require
  a membership previously stored by `replaceCookbooks`; this includes an empty
  recipe replacement. Room keeps and enforces the foreign keys.
- Made malformed cache recovery scope-safe and transactional. A malformed recipe
  summary clears that scope's complete list and fetched marker; malformed detail
  clears only the matching detail payload; malformed cookbook JSON removes only
  that membership and lets foreign-key cascades remove its selection/cache.
  Conditional payload deletes and encompassing Room transactions prevent a
  recovery read from deleting a concurrent replacement. Only
  `SerializationException` is recovered; cancellation and database failures
  propagate.
- Removed the duplicate `detailUpdatedAt` SQL column from the still-unshipped v1
  schema; the preserved detail JSON remains the single source of its `updatedAt`.
- Replaced recipe `NOT IN` pruning with transactional snapshot → scoped delete →
  replacement upsert → fetched marker, preserving retained detail without SQLite
  bind-limit risk.
- Secure-store teardown now uses `AtomicFile.delete()` so base, `.new`, and
  `.bak` artifacts are removed.

### RED evidence

The first focused secure-store run failed at Android-test compilation because the
new transient-key test requested the not-yet-implemented internal seam:

```text
./bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.data.session.EncryptedSessionStoreTest
> Task :app:compileDebugAndroidTestKotlin FAILED
No parameter with name 'keyLookup' found.
BUILD FAILED
```

After the first narrow exception implementation, a focused IO regression was
observed failing before the absence classification was corrected:

```text
tests="9" failures="1"
transientFileReadFailurePropagatesWithoutBeingReportedAsAbsence
Expected file read failure, got null
```

A final ordering regression was also observed before moving file absence ahead
of key access: with no record and an injected transient KeyStore failure, the
store threw instead of returning `null`. The file is now checked first, while a
present valid record still propagates transient key failures unchanged.

The cache recovery tests were also run before implementation:

```text
tests="12" failures="4"
malformedCookbookRemovesOnlyThatMembershipCacheAndSelection
malformedDetailClearsOnlyThatDetailAndPreservesSummariesAndPeers
malformedRecipeListInvalidatesOnlyItsScopeAndFetchedState
unknownJsonFieldsAreIgnoredAcrossCachedPayloads
BUILD FAILED
```

The first three exposed uncaught `JsonDecodingException`; the fourth reported
the unknown `future_field` and requested `ignoreUnknownKeys = true`.

### GREEN and verification evidence

Focused instrumentation on `MainCourse_Phone_API37` / `emulator-5554`:

```text
# RoomCatalogStoreTest
tests="12" failures="0"
BUILD SUCCESSFUL in 4s

# EncryptedSessionStoreTest
tests="10" failures="0"
BUILD SUCCESSFUL in 4s
```

The secure suite now proves a valid encrypted record survives transient
KeyStore and file-read failures, while true absence/corruption/missing key retain
their earlier behavior. The Room suite adds membership rejection, unknown-field
compatibility, and malformed list/detail/cookbook recovery with cross-user
isolation.

Final project checks after production changes:

```text
ANDROID_HOME="$HOME/Library/Android/sdk" ./bin/android-test
BUILD SUCCESSFUL in 2s

ANDROID_HOME="$HOME/Library/Android/sdk" ./bin/android-build
BUILD SUCCESSFUL in 362ms
```

### Task 3 contract

Public store method signatures are unchanged and remain Android-free. Task 3
fakes must reject `selectCookbook` and `replaceRecipes` (including an empty list)
unless `(userId, cookbookId)` exists in the latest stored memberships. Room's
concrete rejection is `SQLiteConstraintException`; Android-free fakes should
model the rejection behavior without depending on that Android exception type.

`SessionStore.read()` now has three observable outcomes for Task 3: a session;
`null` for genuine absence or unusable protected state that has been removed;
or a thrown retryable storage failure for transient KeyStore/provider/non-absence
IO problems. The coordinator must not translate that thrown failure into logout.

Cache reads recover only serialization corruption: malformed complete recipe
lists become `CachedRecipes(emptyList(), fetched = false)`, malformed details
become `null` while retaining summaries, and malformed cookbook memberships are
removed with their own selection/cache. Other exceptions continue outward.

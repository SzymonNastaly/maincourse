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

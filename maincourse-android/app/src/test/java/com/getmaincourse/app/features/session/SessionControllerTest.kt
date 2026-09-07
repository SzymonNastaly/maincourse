package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.cache.CachedRecipes
import com.getmaincourse.app.data.cache.CatalogStore
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CookbookMember
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    @Test
    fun restoreShowsCachedPersonalCookbookBeforeOfflineRefreshFails() = runTest {
        var recipeCalls = 0
        val store = FakeCatalogStore().apply {
            replaceCookbooks(USER.id, listOf(SHARED, PERSONAL))
            selected[USER.id] = 999L
            replaceRecipes(RecipeScope(USER.id, PERSONAL.id), listOf(SOUP))
        }
        val api = FakeApi().apply {
            cookbooksBlock = { throw IOException("offline") }
            recipesBlock = { _, _ -> recipeCalls++; emptyList() }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        controller.restore().join()

        assertEquals(SessionPhase.READY, controller.state.value.phase)
        assertEquals(PERSONAL.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SOUP), controller.state.value.recipes)
        assertEquals(LoadStatus.DEGRADED, controller.state.value.catalogStatus)
        assertTrue(controller.state.value.canRetry)
        assertEquals(0, recipeCalls)
    }

    @Test
    fun restoreWithoutCachedMembershipShowsRetryAndResetInsteadOfSpinning() = runTest {
        val api = FakeApi().apply { cookbooksBlock = { throw IOException("offline") } }
        val controller = controller(api = api, session = SESSION)

        controller.restore().join()

        assertEquals(SessionPhase.LOADING_COOKBOOKS, controller.state.value.phase)
        assertEquals(LoadStatus.ERROR, controller.state.value.catalogStatus)
        assertTrue(controller.state.value.canRetry)
        assertTrue(controller.state.value.canReset)
    }

    @Test
    fun refreshRetriesEmptyOfflineStartupAndLoadsTheCatalog() = runTest {
        var discoveryCalls = 0
        val api = FakeApi().apply {
            cookbooksBlock = {
                discoveryCalls++
                if (discoveryCalls == 1) throw IOException("offline") else listOf(PERSONAL)
            }
            recipesBlock = { _, _ -> listOf(SOUP) }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        controller.refresh().join()

        assertEquals(SessionPhase.READY, controller.state.value.phase)
        assertEquals(LoadStatus.FRESH, controller.state.value.catalogStatus)
        assertEquals(listOf(SOUP), controller.state.value.recipes)
    }

    @Test
    fun authoritativeMembershipRemovalDropsOldCacheAndFallsBackToPersonal() = runTest {
        val sharedScope = RecipeScope(USER.id, SHARED.id)
        val store = FakeCatalogStore().apply {
            replaceCookbooks(USER.id, listOf(SHARED))
            selectCookbook(USER.id, SHARED.id)
            replaceRecipes(sharedScope, listOf(SALAD))
        }
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(SOUP) }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        controller.restore().join()

        assertEquals(PERSONAL.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SOUP), controller.state.value.recipes)
        assertFalse(store.memberships(USER.id).contains(SHARED.id))
        assertTrue(store.recipes(sharedScope).items.isEmpty())
    }

    @Test
    fun expiredAndWrongOriginSessionsArePurgedBeforeSignedOutIsPublished() = runTest {
        for (stored in listOf(
            SESSION.copy(response = SESSION.response.copy(expiresAt = NOW.toString())),
            SESSION.copy(response = SESSION.response.copy(expiresAt = "not-an-instant")),
            SESSION.copy(baseUrl = "https://other.example/"),
        )) {
            val sessionStore = FakeSessionStore(stored)
            val catalogStore = FakeCatalogStore().apply { replaceCookbooks(USER.id, listOf(PERSONAL)) }
            var imageCleanups = 0
            val controller = controller(
                sessionStore = sessionStore,
                catalogStore = catalogStore,
                imageCleanup = { imageCleanups++ },
            )

            controller.restore().join()

            assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
            assertNull(controller.state.value.user)
            assertTrue(sessionStore.cleared)
            assertTrue(catalogStore.cleared)
            assertEquals(1, imageCleanups)
        }
    }

    @Test
    fun restoreStorageFailureKeepsProtectedDataUntilRetryOrExplicitReset() = runTest {
        val sessionStore = FakeSessionStore(SESSION).apply { readFailure = IOException("keystore unavailable") }
        val catalogStore = FakeCatalogStore().apply { replaceCookbooks(USER.id, listOf(PERSONAL)) }
        var imageCleanups = 0
        val controller = controller(
            sessionStore = sessionStore,
            catalogStore = catalogStore,
            imageCleanup = { imageCleanups++ },
        )

        controller.restore().join()

        assertEquals(SessionPhase.RESTORE_FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.canRetry)
        assertTrue(controller.state.value.canReset)
        assertFalse(sessionStore.cleared)
        assertFalse(catalogStore.cleared)
        assertEquals(0, imageCleanups)

        sessionStore.readFailure = null
        controller.reset().join()
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertTrue(sessionStore.cleared)
        assertTrue(catalogStore.cleared)
        assertEquals(1, imageCleanups)
    }

    @Test
    fun signIn401IsAFormErrorAndDoesNotRunAuthenticatedCleanup() = runTest {
        val sessionStore = FakeSessionStore(null)
        val catalogStore = FakeCatalogStore()
        val api = FakeApi().apply { signInBlock = { throw ApiFailure(401, "Invalid credentials") } }
        val controller = controller(api = api, sessionStore = sessionStore, catalogStore = catalogStore)
        controller.restore().join()
        sessionStore.cleared = false
        catalogStore.cleared = false

        controller.signIn(SIGN_IN).join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertEquals("Invalid credentials", controller.state.value.authError)
        assertFalse(sessionStore.cleared)
        assertFalse(catalogStore.cleared)
    }

    @Test
    fun signInAndSignUpPersistSessionBeforeLoadingCatalog() = runTest {
        for (signUp in listOf(false, true)) {
            val sessionStore = FakeSessionStore(null)
            val catalogStore = FakeCatalogStore()
            val api = FakeApi().apply { cookbooksBlock = { listOf(PERSONAL) } }
            val controller = controller(api = api, sessionStore = sessionStore, catalogStore = catalogStore)
            controller.restore().join()
            sessionStore.cleared = false

            if (signUp) controller.signUp(SIGN_UP).join() else controller.signIn(SIGN_IN).join()

            assertEquals(SESSION, sessionStore.value)
            assertEquals(SessionPhase.READY, controller.state.value.phase)
            assertEquals(USER, controller.state.value.user)
            assertEquals(PERSONAL.id, controller.state.value.activeCookbookId)
        }
    }

    @Test
    fun authenticated401PurgesSessionCatalogAndImages() = runTest {
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> throw ApiFailure(401, "expired") }
        }
        val sessionStore = FakeSessionStore(SESSION)
        val catalogStore = FakeCatalogStore()
        var imageCleanups = 0
        val controller = controller(api, sessionStore, catalogStore) { imageCleanups++ }

        controller.restore().join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertTrue(sessionStore.cleared)
        assertTrue(catalogStore.cleared)
        assertEquals(1, imageCleanups)
    }

    @Test
    fun forbiddenCookbookIsPurgedAndFailedRediscoveryCannotExposeItsCache() = runTest {
        var discoveries = 0
        val api = FakeApi().apply {
            cookbooksBlock = {
                discoveries++
                if (discoveries == 1) listOf(PERSONAL) else throw IOException("offline")
            }
            recipesBlock = { _, _ -> throw ApiFailure(403, "forbidden") }
        }
        val store = FakeCatalogStore()
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        controller.restore().join()

        assertEquals(2, discoveries)
        assertNull(controller.state.value.activeCookbookId)
        assertTrue(controller.state.value.recipes.isEmpty())
        assertEquals(LoadStatus.ERROR, controller.state.value.catalogStatus)
        assertFalse(store.memberships(USER.id).contains(PERSONAL.id))
    }

    @Test
    fun forbiddenCookbookPerformsOneRediscoveryAndFallsBackToAnotherMembership() = runTest {
        var discoveries = 0
        val api = FakeApi().apply {
            cookbooksBlock = {
                discoveries++
                listOf(PERSONAL, SHARED)
            }
            recipesBlock = { _, cookbookId ->
                if (cookbookId == PERSONAL.id) throw ApiFailure(403, "forbidden") else listOf(SALAD)
            }
        }
        val store = FakeCatalogStore()
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        controller.restore().join()

        assertEquals(2, discoveries)
        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SALAD), controller.state.value.recipes)
        assertEquals(LoadStatus.FRESH, controller.state.value.catalogStatus)
        assertFalse(store.memberships(USER.id).contains(PERSONAL.id))
    }

    @Test
    fun repeatedForbiddenResponsesDoNotLoopRediscovery() = runTest {
        var discoveries = 0
        val api = FakeApi().apply {
            cookbooksBlock = {
                discoveries++
                listOf(PERSONAL, SHARED)
            }
            recipesBlock = { _, _ -> throw ApiFailure(403, "forbidden") }
        }
        val controller = controller(api = api, session = SESSION)

        controller.restore().join()

        assertEquals(2, discoveries)
        assertNull(controller.state.value.activeCookbookId)
        assertEquals(LoadStatus.ERROR, controller.state.value.catalogStatus)
        assertTrue(controller.state.value.canRetry)
    }

    @Test
    fun detail404RemovesStaleSummaryAndShowsUnavailable() = runTest {
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(SOUP) }
            recipeBlock = { _, _, _ -> throw ApiFailure(404, "missing") }
        }
        val store = FakeCatalogStore()
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()

        controller.openRecipe(SOUP.id).join()

        assertTrue(controller.state.value.recipes.isEmpty())
        assertEquals(DetailStatus.UNAVAILABLE, controller.state.value.detail?.status)
        assertTrue(store.recipes(RecipeScope(USER.id, PERSONAL.id)).items.isEmpty())
    }

    @Test
    fun uncachedOfflineDetailNeverInventsEmptyRecipeContent() = runTest {
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(SOUP) }
            recipeBlock = { _, _, _ -> throw IOException("offline") }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        controller.openRecipe(SOUP.id).join()

        assertEquals(DetailStatus.ERROR, controller.state.value.detail?.status)
        assertNull(controller.state.value.detail?.recipe)
    }

    @Test
    fun cachedDetailRemainsVisibleWhenRefreshFails() = runTest {
        val scope = RecipeScope(USER.id, PERSONAL.id)
        val store = FakeCatalogStore().apply {
            replaceCookbooks(USER.id, listOf(PERSONAL))
            replaceRecipes(scope, listOf(SOUP))
            saveDetail(scope, SOUP_DETAIL)
        }
        val api = FakeApi().apply {
            cookbooksBlock = { throw IOException("offline") }
            recipesBlock = { _, _ -> throw IOException("offline") }
            recipeBlock = { _, _, _ -> throw IOException("offline") }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()

        controller.openRecipe(SOUP.id).join()

        assertEquals(DetailStatus.SAVED_OFFLINE, controller.state.value.detail?.status)
        assertEquals(SOUP_DETAIL, controller.state.value.detail?.recipe)
    }

    @Test
    fun detailRefreshIsPartialAndDoesNotPruneOtherListRecipes() = runTest {
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(SOUP, SALAD) }
            recipeBlock = { _, _, _ -> SOUP_DETAIL }
        }
        val store = FakeCatalogStore()
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()

        controller.openRecipe(SOUP.id).join()

        assertEquals(listOf(SOUP, SALAD), controller.state.value.recipes)
        assertEquals(listOf(SOUP, SALAD), store.recipes(RecipeScope(USER.id, PERSONAL.id)).items)
        assertEquals(SOUP_DETAIL, controller.state.value.detail?.recipe)
    }

    @Test
    fun failedRefreshPreservesCacheWhileSuccessfulEmptyRefreshClearsIt() = runTest {
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(SOUP) }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        api.recipesBlock = { _, _ -> throw ApiFailure(500, "server") }
        controller.refresh().join()
        assertEquals(listOf(SOUP), controller.state.value.recipes)
        assertEquals(LoadStatus.DEGRADED, controller.state.value.recipeStatus)

        api.recipesBlock = { _, _ -> emptyList() }
        controller.refresh().join()
        assertTrue(controller.state.value.recipes.isEmpty())
        assertTrue(controller.state.value.recipesFetched)
        assertEquals(LoadStatus.FRESH, controller.state.value.recipeStatus)
    }

    @Test
    fun pendingRecipeIsNotOpenedAsCompleteDetail() = runTest {
        val pending = SOUP.copy(importStatus = "pending")
        var detailCalls = 0
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(pending) }
            recipeBlock = { _, _, _ -> detailCalls++; SOUP_DETAIL }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        controller.openRecipe(pending.id).join()

        assertEquals(DetailStatus.NOT_READY, controller.state.value.detail?.status)
        assertNull(controller.state.value.detail?.recipe)
        assertEquals(0, detailCalls)
    }

    @Test
    fun cookbookSwitchClearsOldRecipesSynchronouslyAndIgnoresCancelledResponse() = runTest {
        val sharedResponse = CompletableDeferred<List<RecipeSummary>>()
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL, SHARED) }
            recipesBlock = { _, cookbookId ->
                if (cookbookId == PERSONAL.id) listOf(SOUP) else sharedResponse.await()
            }
        }
        val store = FakeCatalogStore()
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()

        val switching = controller.switchCookbook(SHARED.id)
        runCurrent()
        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertTrue(controller.state.value.recipes.isEmpty())

        val back = controller.switchCookbook(PERSONAL.id)
        runCurrent()
        sharedResponse.complete(listOf(SALAD))
        advanceUntilIdle()
        switching.join()
        back.join()

        assertEquals(PERSONAL.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SOUP), controller.state.value.recipes)
        assertTrue(store.recipes(RecipeScope(USER.id, SHARED.id)).items.isEmpty())
    }

    @Test
    fun concurrentSwitchesCannotPersistTheOlderSelectionOrStartItsRefreshLate() = runTest {
        val sharedSelectionStarted = CompletableDeferred<Unit>()
        val releaseSharedSelection = CompletableDeferred<Unit>()
        val store = FakeCatalogStore()
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL, SHARED) }
            recipesBlock = { _, cookbookId -> if (cookbookId == PERSONAL.id) listOf(SOUP) else listOf(SALAD) }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()
        store.allRecipeWrites.clear()
        store.beforeSelect = { cookbookId ->
            if (cookbookId == SHARED.id) {
                sharedSelectionStarted.complete(Unit)
                releaseSharedSelection.await()
            }
        }

        val older = controller.switchCookbook(SHARED.id)
        sharedSelectionStarted.await()
        val newer = controller.switchCookbook(PERSONAL.id)
        runCurrent()
        releaseSharedSelection.complete(Unit)
        older.join()
        newer.join()

        assertEquals(PERSONAL.id, controller.state.value.activeCookbookId)
        assertEquals(PERSONAL.id, store.selected[USER.id])
        assertFalse(store.allRecipeWrites.any { it.first.cookbookId == SHARED.id })
    }

    @Test
    fun logoutCancelsInflightRefreshAndCleansEverythingBeforeNextLogin() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        var cancelled = false
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ ->
                requestStarted.complete(Unit)
                try {
                    awaitCancellation()
                } catch (failure: CancellationException) {
                    cancelled = true
                    throw failure
                }
            }
        }
        val sessionStore = FakeSessionStore(SESSION)
        val store = FakeCatalogStore()
        var imageCleanups = 0
        val controller = controller(api, sessionStore, store) { imageCleanups++ }

        val restoring = controller.restore()
        requestStarted.await()
        controller.logout().join()
        restoring.join()

        assertTrue(cancelled)
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertTrue(store.cleared)
        assertTrue(sessionStore.cleared)
        assertEquals(1, imageCleanups)
        assertTrue(store.allRecipeWrites.isEmpty())
    }

    @Test
    fun duplicateAuthenticationSubmissionsAreIgnored() = runTest {
        val response = CompletableDeferred<SessionResponse>()
        val api = FakeApi().apply { signInBlock = { response.await() } }
        val controller = controller(api = api)
        controller.restore().join()

        val first = controller.signIn(SIGN_IN)
        runCurrent()
        val duplicate = controller.signIn(SIGN_IN)
        runCurrent()
        response.complete(SESSION.response)
        first.join()
        duplicate.join()

        assertEquals(1, api.signInCalls)
        assertEquals(SessionPhase.READY, controller.state.value.phase)
    }

    @Test
    fun logoutTimesOutRevocationAndStillFinishesLocalCleanup() = runTest {
        var revokeCancelled = false
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            signOutBlock = {
                try {
                    awaitCancellation()
                } finally {
                    revokeCancelled = true
                }
            }
        }
        val sessionStore = FakeSessionStore(SESSION)
        val store = FakeCatalogStore()
        var cleanupObservedClearedStores = false
        val controller = controller(api, sessionStore, store) {
            cleanupObservedClearedStores = sessionStore.cleared && store.cleared
        }
        controller.restore().join()

        controller.logout().join()

        assertTrue(revokeCancelled)
        assertTrue(cleanupObservedClearedStores)
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
    }

    @Test
    fun cleanupFailureIsVisibleAndBlocksNewAuthenticationUntilLogoutRetrySucceeds() = runTest {
        val sessionStore = FakeSessionStore(SESSION).apply { clearFailure = IOException("disk") }
        val store = FakeCatalogStore()
        val api = FakeApi().apply { cookbooksBlock = { listOf(PERSONAL) } }
        val controller = controller(api, sessionStore, store)
        controller.restore().join()

        controller.logout().join()
        assertEquals(SessionPhase.CLEANUP_FAILED, controller.state.value.phase)
        assertTrue(controller.state.value.canRetry)

        controller.signIn(SIGN_IN).join()
        assertEquals(0, api.signInCalls)

        sessionStore.clearFailure = null
        controller.logout().join()
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
    }

    @Test
    fun failedSessionWriteDoesNotAdmitAccountAndAttemptsProtectedCleanup() = runTest {
        val sessionStore = FakeSessionStore(null).apply { writeFailure = IOException("full") }
        val store = FakeCatalogStore()
        val controller = controller(sessionStore = sessionStore, catalogStore = store)
        controller.restore().join()
        sessionStore.cleared = false
        store.cleared = false

        controller.signIn(SIGN_IN).join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertNull(controller.state.value.user)
        assertTrue(sessionStore.cleared)
        assertTrue(store.cleared)
        assertTrue(controller.state.value.authError != null)
    }

    @Test
    fun expiryCheckOnResumeClearsAuthenticatedState() = runTest {
        val clock = MutableClock(NOW.minusSeconds(60))
        val controller = controller(session = SESSION, clock = clock)
        controller.restore().join()
        assertEquals(SessionPhase.READY, controller.state.value.phase)

        clock.instant = Instant.parse(SESSION.response.expiresAt)
        controller.checkExpiry().join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertNull(controller.state.value.user)
    }

    @Test
    fun repositoryCancellationPropagatesAndPreventsLateRecipeWrite() = runTest {
        val response = CompletableDeferred<List<RecipeSummary>>()
        val store = FakeCatalogStore().apply { replaceCookbooks(USER.id, listOf(PERSONAL)) }
        val api = FakeApi().apply { recipesBlock = { _, _ -> response.await() } }
        val repository = CatalogRepository(api, store)
        val request = async { repository.refreshRecipes(SESSION.response, RecipeScope(USER.id, PERSONAL.id)) }
        runCurrent()

        request.cancel()
        response.complete(listOf(SOUP))
        try {
            request.await()
        } catch (_: CancellationException) {
            // Expected.
        }

        assertTrue(request.isCancelled)
        assertTrue(store.allRecipeWrites.isEmpty())
    }

    @Test
    fun repositoryChecksCancellationWhenApiIgnoresItBeforeWriting() = runTest {
        val response = CompletableDeferred<List<RecipeSummary>>()
        val store = FakeCatalogStore().apply { replaceCookbooks(USER.id, listOf(PERSONAL)) }
        val api = FakeApi().apply {
            recipesBlock = { _, _ -> withContext(NonCancellable) { response.await() } }
        }
        val repository = CatalogRepository(api, store)
        val request = async { repository.refreshRecipes(SESSION.response, RecipeScope(USER.id, PERSONAL.id)) }
        runCurrent()

        request.cancel()
        response.complete(listOf(SOUP))
        try {
            request.await()
        } catch (_: CancellationException) {
            // Expected.
        }

        assertTrue(request.isCancelled)
        assertTrue(store.allRecipeWrites.isEmpty())
    }

    private fun CoroutineScope.controller(
        api: FakeApi = FakeApi(),
        sessionStore: FakeSessionStore = FakeSessionStore(null),
        catalogStore: FakeCatalogStore = FakeCatalogStore(),
        session: StoredSession? = null,
        clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC),
        imageCleanup: suspend () -> Unit = {},
    ): SessionController {
        if (session != null) sessionStore.value = session
        return SessionController(
            api = api,
            sessionStore = sessionStore,
            catalogRepository = CatalogRepository(api, catalogStore),
            baseUrl = BASE_URL,
            clock = clock,
            scope = this,
            imageCleanup = imageCleanup,
            revokeTimeoutMillis = 100,
        )
    }

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = Clock.fixed(instant, zone)
        override fun instant(): Instant = instant
    }

    private class FakeSessionStore(var value: StoredSession?) : SessionStore {
        var readFailure: Throwable? = null
        var writeFailure: Throwable? = null
        var clearFailure: Throwable? = null
        var cleared = false

        override suspend fun read(): StoredSession? {
            readFailure?.let { throw it }
            return value
        }

        override suspend fun write(session: StoredSession) {
            writeFailure?.let { throw it }
            value = session
        }

        override suspend fun clear() {
            clearFailure?.let { throw it }
            value = null
            cleared = true
        }
    }

    private class FakeCatalogStore : CatalogStore {
        private val cookbookItems = mutableMapOf<Long, List<Cookbook>>()
        val selected = mutableMapOf<Long, Long>()
        private val recipeItems = mutableMapOf<RecipeScope, CachedRecipes>()
        private val details = mutableMapOf<Pair<RecipeScope, Long>, RecipeDetail>()
        val allRecipeWrites = mutableListOf<Pair<RecipeScope, List<RecipeSummary>>>()
        var beforeSelect: suspend (Long) -> Unit = {}
        var cleared = false

        fun memberships(userId: Long) = cookbookItems[userId].orEmpty().map { it.id }

        override suspend fun cookbooks(userId: Long) = cookbookItems[userId].orEmpty()

        override suspend fun replaceCookbooks(userId: Long, items: List<Cookbook>) {
            cookbookItems[userId] = items
            val retained = items.map { it.id }.toSet()
            recipeItems.keys.filter { it.userId == userId && it.cookbookId !in retained }.forEach(recipeItems::remove)
            details.keys.filter { it.first.userId == userId && it.first.cookbookId !in retained }.forEach(details::remove)
            if (selected[userId] !in retained) selected.remove(userId)
        }

        override suspend fun selectedCookbookId(userId: Long) = selected[userId]

        override suspend fun selectCookbook(userId: Long, cookbookId: Long) {
            require(cookbookId in memberships(userId)) { "membership must be stored before selection" }
            beforeSelect(cookbookId)
            selected[userId] = cookbookId
        }

        override suspend fun recipes(scope: RecipeScope) = recipeItems[scope] ?: CachedRecipes(emptyList(), false)

        override suspend fun replaceRecipes(scope: RecipeScope, items: List<RecipeSummary>) {
            require(scope.cookbookId in memberships(scope.userId)) { "membership must be stored before recipes" }
            val retained = items.map { it.id }.toSet()
            details.keys.filter { it.first == scope && it.second !in retained }.forEach(details::remove)
            recipeItems[scope] = CachedRecipes(items, true)
            allRecipeWrites += scope to items
        }

        override suspend fun detail(scope: RecipeScope, recipeId: Long) = details[scope to recipeId]

        override suspend fun saveDetail(scope: RecipeScope, detail: RecipeDetail) {
            if (recipeItems[scope]?.items?.any { it.id == detail.id } == true) {
                details[scope to detail.id] = detail
            }
        }

        override suspend fun removeRecipe(scope: RecipeScope, recipeId: Long) {
            val current = recipeItems[scope] ?: CachedRecipes(emptyList(), false)
            recipeItems[scope] = current.copy(items = current.items.filterNot { it.id == recipeId })
            details.remove(scope to recipeId)
        }

        override suspend fun removeCookbook(scope: RecipeScope) {
            cookbookItems[scope.userId] = cookbookItems[scope.userId].orEmpty().filterNot { it.id == scope.cookbookId }
            recipeItems.remove(scope)
            details.keys.filter { it.first == scope }.forEach(details::remove)
            if (selected[scope.userId] == scope.cookbookId) selected.remove(scope.userId)
        }

        override suspend fun clear() {
            cookbookItems.clear()
            selected.clear()
            recipeItems.clear()
            details.clear()
            cleared = true
        }
    }

    private class FakeApi : MainCourseApi {
        var signInCalls = 0
        var signInBlock: suspend (SignInRequest) -> SessionResponse = { SESSION.response }
        var signUpBlock: suspend (SignUpRequest) -> SessionResponse = { SESSION.response }
        var signOutBlock: suspend (String) -> Unit = {}
        var cookbooksBlock: suspend (String) -> List<Cookbook> = { listOf(PERSONAL) }
        var recipesBlock: suspend (String, Long) -> List<RecipeSummary> = { _, _ -> emptyList() }
        var recipeBlock: suspend (String, Long, Long) -> RecipeDetail = { _, _, _ -> SOUP_DETAIL }

        override suspend fun signIn(request: SignInRequest): SessionResponse {
            signInCalls++
            return signInBlock(request)
        }

        override suspend fun signUp(request: SignUpRequest) = signUpBlock(request)
        override suspend fun signOut(token: String) = signOutBlock(token)
        override suspend fun cookbooks(token: String) = cookbooksBlock(token)
        override suspend fun recipes(token: String, cookbookId: Long) = recipesBlock(token, cookbookId)
        override suspend fun recipe(token: String, cookbookId: Long, recipeId: Long) = recipeBlock(token, cookbookId, recipeId)
    }

    private companion object {
        const val BASE_URL = "https://app.getmaincourse.com/"
        val NOW: Instant = Instant.parse("2026-09-07T12:00:00Z")
        val USER = User(7, "Cook", "cook@example.com", true)
        val SESSION = StoredSession(
            BASE_URL,
            SessionResponse("secret-token", "2026-12-06T12:00:00Z", USER),
        )
        val MEMBER = CookbookMember(7, "cook@example.com", "owner")
        val PERSONAL = Cookbook(1, "My Recipes", true, 1, listOf(MEMBER))
        val SHARED = Cookbook(2, "Family", false, 1, listOf(MEMBER))
        val SOUP = RecipeSummary(10, "Soup", 10, 20, false, null, null, "completed", null, "2026-09-07T10:00:00Z")
        val SALAD = RecipeSummary(11, "Salad", 5, null, false, null, null, "completed", null, "2026-09-07T11:00:00Z")
        val SOUP_DETAIL = RecipeDetail(
            id = SOUP.id,
            name = SOUP.name,
            prepTime = 10,
            cookTime = 20,
            servings = 4,
            favorite = false,
            ingredients = listOf("1 onion"),
            structuredIngredients = emptyList(),
            instructions = listOf("Simmer"),
            notes = null,
            sourceUrl = null,
            tags = emptyList(),
            coverImageUrl = null,
            coverImages = null,
            createdAt = "2026-09-01T10:00:00Z",
            updatedAt = "2026-09-07T10:00:00Z",
        )
        val SIGN_IN = SignInRequest("cook@example.com", "password", "Pixel")
        val SIGN_UP = SignUpRequest("Cook", "cook@example.com", "password", "password", "Pixel")
    }
}

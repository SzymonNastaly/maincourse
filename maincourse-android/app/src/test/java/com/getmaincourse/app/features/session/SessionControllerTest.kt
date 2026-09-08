package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.cache.CachedRecipes
import com.getmaincourse.app.data.cache.CatalogStore
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.AccountAttributes
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CookbookMember
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.OnboardingResponse
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
import com.getmaincourse.app.features.settings.AccountOperation
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    fun newerCatalogRefreshPreventsDelayedDiscoveryFromPruningItsStateAndCache() = runTest {
        val firstDiscoveryStarted = CompletableDeferred<Unit>()
        val releaseFirstDiscovery = CompletableDeferred<Unit>()
        var discoveries = 0
        val store = FakeCatalogStore().apply {
            replaceCookbooks(USER.id, listOf(PERSONAL))
            replaceRecipes(RecipeScope(USER.id, PERSONAL.id), listOf(SOUP))
        }
        val api = FakeApi().apply {
            cookbooksBlock = {
                discoveries++
                if (discoveries == 1) {
                    firstDiscoveryStarted.complete(Unit)
                    withContext(NonCancellable) { releaseFirstDiscovery.await() }
                    listOf(PERSONAL)
                } else {
                    listOf(SHARED)
                }
            }
            recipesBlock = { _, cookbookId -> if (cookbookId == SHARED.id) listOf(SALAD) else listOf(SOUP) }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        val restoring = controller.restore()
        firstDiscoveryStarted.await()
        val retry = controller.refresh()
        runCurrent()
        releaseFirstDiscovery.complete(Unit)
        restoring.join()
        retry.join()

        assertEquals(2, discoveries)
        assertEquals(LoadStatus.FRESH, controller.state.value.catalogStatus)
        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SALAD), controller.state.value.recipes)
        assertEquals(listOf(SHARED.id), store.memberships(USER.id))
        assertTrue(store.recipes(RecipeScope(USER.id, PERSONAL.id)).items.isEmpty())
    }

    @Test
    fun newerCatalogRefreshPreventsDelayedFailureFromDegradingFreshState() = runTest {
        val firstDiscoveryStarted = CompletableDeferred<Unit>()
        val releaseFirstDiscovery = CompletableDeferred<Unit>()
        var discoveries = 0
        val store = FakeCatalogStore().apply {
            replaceCookbooks(USER.id, listOf(PERSONAL))
            replaceRecipes(RecipeScope(USER.id, PERSONAL.id), listOf(SOUP))
        }
        val api = FakeApi().apply {
            cookbooksBlock = {
                discoveries++
                if (discoveries == 1) {
                    firstDiscoveryStarted.complete(Unit)
                    withContext(NonCancellable) { releaseFirstDiscovery.await() }
                    throw IOException("old offline response")
                }
                listOf(SHARED)
            }
            recipesBlock = { _, _ -> listOf(SALAD) }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        val restoring = controller.restore()
        firstDiscoveryStarted.await()
        val retry = controller.refresh()
        runCurrent()
        releaseFirstDiscovery.complete(Unit)
        restoring.join()
        retry.join()

        assertEquals(2, discoveries)
        assertEquals(LoadStatus.FRESH, controller.state.value.catalogStatus)
        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SALAD), controller.state.value.recipes)
        assertNull(controller.state.value.message)
        assertFalse(controller.state.value.canRetry)
    }

    @Test
    fun sameCookbookDiscoveryKeepsVisibleRecipesAndDetailWhileRefreshing() = runTest {
        val discoveryStarted = CompletableDeferred<Unit>()
        val releaseDiscovery = CompletableDeferred<Unit>()
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val recipeScope = RecipeScope(USER.id, PERSONAL.id)
        val store = FakeCatalogStore().apply {
            replaceCookbooks(USER.id, listOf(PERSONAL))
            selectCookbook(USER.id, PERSONAL.id)
            replaceRecipes(recipeScope, listOf(SOUP))
            saveDetail(recipeScope, SOUP_DETAIL)
        }
        val api = FakeApi().apply {
            cookbooksBlock = {
                discoveryStarted.complete(Unit)
                releaseDiscovery.await()
                listOf(PERSONAL)
            }
            recipesBlock = { _, _ ->
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                listOf(SOUP)
            }
            recipeBlock = { _, _, _ -> SOUP_DETAIL }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        val restoring = controller.restore()
        discoveryStarted.await()
        controller.openRecipe(SOUP.id).join()
        assertEquals(DetailStatus.FRESH, controller.state.value.detail?.status)

        releaseDiscovery.complete(Unit)
        refreshStarted.await()

        assertEquals(listOf(SOUP), controller.state.value.recipes)
        assertEquals(DetailStatus.FRESH, controller.state.value.detail?.status)

        releaseRefresh.complete(Unit)
        restoring.join()
    }

    @Test
    fun slowDiscoveryDoesNotOverrideAConcurrentExplicitCookbookSwitch() = runTest {
        val discoveryStarted = CompletableDeferred<Unit>()
        val releaseDiscovery = CompletableDeferred<Unit>()
        val detailStarted = CompletableDeferred<Unit>()
        val detailCancellationObserved = CompletableDeferred<Unit>()
        val releaseDetail = CompletableDeferred<Unit>()
        val store = FakeCatalogStore().apply {
            replaceCookbooks(USER.id, listOf(PERSONAL, SHARED))
            selectCookbook(USER.id, PERSONAL.id)
            replaceRecipes(RecipeScope(USER.id, PERSONAL.id), listOf(SOUP))
            replaceRecipes(RecipeScope(USER.id, SHARED.id), listOf(SALAD))
        }
        val api = FakeApi().apply {
            cookbooksBlock = {
                discoveryStarted.complete(Unit)
                releaseDiscovery.await()
                listOf(PERSONAL, SHARED)
            }
            recipesBlock = { _, cookbookId ->
                if (cookbookId == SHARED.id) listOf(SALAD) else listOf(SOUP)
            }
            recipeBlock = { _, _, _ ->
                detailStarted.complete(Unit)
                try {
                    awaitCancellation()
                } catch (failure: CancellationException) {
                    detailCancellationObserved.complete(Unit)
                    withContext(NonCancellable) { releaseDetail.await() }
                    throw failure
                }
            }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        val restoring = controller.restore()
        discoveryStarted.await()
        val detail = controller.openRecipe(SOUP.id)
        detailStarted.await()
        val switching = controller.switchCookbook(SHARED.id)
        detailCancellationObserved.await()

        releaseDiscovery.complete(Unit)
        runCurrent()
        releaseDetail.complete(Unit)
        detail.join()
        switching.join()
        restoring.join()

        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertEquals(SHARED.id, store.selected[USER.id])
        assertEquals(listOf(SALAD), controller.state.value.recipes)
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
        assertEquals("Could not read the saved session", controller.state.value.message)
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
    fun authenticated401CleanupFinishesWhenItsDetailJobIsCancelledWhileJoiningARefresh() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshCancellationObserved = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        var recipeCalls = 0
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ ->
                recipeCalls++
                if (recipeCalls == 1) {
                    listOf(SOUP)
                } else {
                    refreshStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (failure: CancellationException) {
                        refreshCancellationObserved.complete(Unit)
                        withContext(NonCancellable) { releaseRefresh.await() }
                        throw failure
                    }
                }
            }
            recipeBlock = { _, _, _ -> throw ApiFailure(401, "expired") }
        }
        val sessionStore = FakeSessionStore(SESSION)
        val store = FakeCatalogStore()
        var imageCleanups = 0
        val controller = controller(api, sessionStore, store) { imageCleanups++ }
        controller.restore().join()

        val refresh = controller.refresh()
        refreshStarted.await()
        val expiredDetail = controller.openRecipe(SOUP.id)
        refreshCancellationObserved.await()
        val close = controller.closeRecipe()
        runCurrent()
        releaseRefresh.complete(Unit)
        refresh.join()
        expiredDetail.join()
        close.join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertTrue(sessionStore.cleared)
        assertTrue(store.cleared)
        assertEquals(1, imageCleanups)

        api.recipesBlock = { _, _ -> emptyList() }
        controller.signIn(SIGN_IN).join()
        assertEquals(SessionPhase.READY, controller.state.value.phase)
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
    fun detail404DuringListRefreshClearsTheRefreshLoadingState() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        var recipeCalls = 0
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ ->
                recipeCalls++
                if (recipeCalls == 1) {
                    listOf(SOUP)
                } else {
                    refreshStarted.complete(Unit)
                    awaitCancellation()
                }
            }
            recipeBlock = { _, _, _ -> throw ApiFailure(404, "missing") }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        val refresh = controller.refresh()
        refreshStarted.await()
        controller.openRecipe(SOUP.id).join()
        refresh.join()

        assertTrue(controller.state.value.recipesFetched)
        assertEquals(LoadStatus.FRESH, controller.state.value.recipeStatus)
        assertEquals(DetailStatus.UNAVAILABLE, controller.state.value.detail?.status)
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
    fun cancellingLogoutWhileItJoinsAuthenticatedWorkStillFinishesLocalCleanup() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshCancellationObserved = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        var recipeCalls = 0
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ ->
                recipeCalls++
                if (recipeCalls == 1) {
                    listOf(SOUP)
                } else {
                    refreshStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (failure: CancellationException) {
                        refreshCancellationObserved.complete(Unit)
                        withContext(NonCancellable) { releaseRefresh.await() }
                        throw failure
                    }
                }
            }
        }
        val sessionStore = FakeSessionStore(SESSION)
        val store = FakeCatalogStore()
        var imageCleanups = 0
        val controller = controller(api, sessionStore, store) { imageCleanups++ }
        controller.restore().join()

        val refresh = controller.refresh()
        refreshStarted.await()
        val logout = controller.logout()
        refreshCancellationObserved.await()
        logout.cancel()
        releaseRefresh.complete(Unit)
        refresh.join()
        logout.join()

        assertTrue(logout.isCancelled)
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertTrue(sessionStore.cleared)
        assertTrue(store.cleared)
        assertEquals(1, imageCleanups)

        api.recipesBlock = { _, _ -> emptyList() }
        controller.signIn(SIGN_IN).join()
        assertEquals(SessionPhase.READY, controller.state.value.phase)
    }

    @Test
    fun cancellingControllerScopeDuringLogoutStillFinishesLocalCleanup() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshCancellationObserved = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        var recipeCalls = 0
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ ->
                recipeCalls++
                if (recipeCalls == 1) {
                    listOf(SOUP)
                } else {
                    refreshStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (failure: CancellationException) {
                        refreshCancellationObserved.complete(Unit)
                        withContext(NonCancellable) { releaseRefresh.await() }
                        throw failure
                    }
                }
            }
        }
        val sessionStore = FakeSessionStore(SESSION)
        val store = FakeCatalogStore()
        val controllerJob = Job()
        val controllerScope = CoroutineScope(coroutineContext + controllerJob)
        var imageCleanups = 0
        val controller = controller(
            api = api,
            sessionStore = sessionStore,
            catalogStore = store,
            imageCleanup = { imageCleanups++ },
            controllerScope = controllerScope,
        )
        controller.restore().join()

        val refresh = controller.refresh()
        refreshStarted.await()
        val logout = controller.logout()
        refreshCancellationObserved.await()
        controllerJob.cancel()
        releaseRefresh.complete(Unit)
        refresh.join()
        logout.join()

        assertTrue(logout.isCancelled)
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertTrue(sessionStore.cleared)
        assertTrue(store.cleared)
        assertEquals(1, imageCleanups)
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
        assertEquals("Your session has expired", controller.state.value.authError)
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

    @Test
    fun resetDuringSlowRestorePreventsTheReadSessionFromBeingAdmitted() = runTest {
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val sessionStore = FakeSessionStore(SESSION).apply {
            beforeRead = {
                readStarted.complete(Unit)
                releaseRead.await()
            }
        }
        val controller = controller(sessionStore = sessionStore)

        val restoring = controller.restore()
        readStarted.await()
        val resetting = controller.reset()
        runCurrent()
        releaseRead.complete(Unit)
        restoring.join()
        resetting.join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertNull(controller.state.value.user)
    }

    @Test
    fun restoreIsIgnoredDuringSlowLogoutRevocation() = runTest {
        val revokeStarted = CompletableDeferred<Unit>()
        val releaseRevoke = CompletableDeferred<Unit>()
        val sessionStore = FakeSessionStore(SESSION)
        val api = FakeApi().apply {
            signOutBlock = {
                revokeStarted.complete(Unit)
                releaseRevoke.await()
            }
        }
        val controller = controller(api = api, sessionStore = sessionStore)
        controller.restore().join()

        val logout = controller.logout()
        revokeStarted.await()
        controller.restore().join()

        assertEquals(1, sessionStore.readCalls)
        releaseRevoke.complete(Unit)
        logout.join()
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
    }

    @Test
    fun restoreIsIgnoredDuringAuthenticationAndAfterSignedOutFormError() = runTest {
        val authResponse = CompletableDeferred<SessionResponse>()
        val sessionStore = FakeSessionStore(null)
        val api = FakeApi().apply { signInBlock = { authResponse.await() } }
        val controller = controller(api = api, sessionStore = sessionStore)
        controller.restore().join()
        val readsAfterStartup = sessionStore.readCalls
        sessionStore.value = SESSION

        val authentication = controller.signIn(SIGN_IN)
        runCurrent()
        controller.restore().join()
        assertEquals(readsAfterStartup, sessionStore.readCalls)

        authResponse.completeExceptionally(ApiFailure(401, "bad credentials"))
        authentication.join()
        controller.restore().join()

        assertEquals(readsAfterStartup, sessionStore.readCalls)
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertEquals("bad credentials", controller.state.value.authError)
    }

    @Test
    fun overlappingCleanupCannotAdmitThenClearANewLogin() = runTest {
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        var cleanupCalls = 0
        val api = FakeApi().apply { cookbooksBlock = { listOf(PERSONAL) } }
        val sessionStore = FakeSessionStore(SESSION)
        val controller = controller(api = api, sessionStore = sessionStore) {
            cleanupCalls++
            if (cleanupCalls == 1) {
                cleanupStarted.complete(Unit)
                releaseCleanup.await()
            }
        }
        controller.restore().join()

        val logout = controller.logout()
        cleanupStarted.await()
        val reset = controller.reset()
        runCurrent()
        controller.signIn(SIGN_IN).join()
        assertEquals(0, api.signInCalls)

        releaseCleanup.complete(Unit)
        logout.join()
        reset.join()
        controller.signIn(SIGN_IN).join()

        assertEquals(1, api.signInCalls)
        assertEquals(SessionPhase.READY, controller.state.value.phase)
    }

    @Test
    fun failedRecipeRemovalIsContainedAndRetryableWithMissingContentHidden() = runTest {
        val store = FakeCatalogStore().apply { removeRecipeFailure = IOException("disk") }
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(SOUP) }
            recipeBlock = { _, _, _ -> throw ApiFailure(404, "missing") }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()

        controller.openRecipe(SOUP.id).join()

        assertTrue(controller.state.value.recipes.isEmpty())
        assertEquals(DetailStatus.UNAVAILABLE, controller.state.value.detail?.status)
        assertEquals(LoadStatus.ERROR, controller.state.value.recipeStatus)
        assertTrue(controller.state.value.canRetry)
        assertTrue(controller.state.value.canReset)
    }

    @Test
    fun failedRecipePurgeRetryDoesNotReplaceAnUnrelatedOpenDetail() = runTest {
        val store = FakeCatalogStore().apply { removeRecipeFailure = IOException("disk") }
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(SOUP, SALAD) }
            recipeBlock = { _, _, recipeId ->
                if (recipeId == SOUP.id) throw ApiFailure(404, "missing")
                SOUP_DETAIL.copy(id = SALAD.id, name = SALAD.name)
            }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()
        controller.openRecipe(SOUP.id).join()
        controller.openRecipe(SALAD.id).join()

        controller.refresh().join()

        assertEquals(SALAD.id, controller.state.value.detail?.recipeId)
        assertEquals(DetailStatus.FRESH, controller.state.value.detail?.status)
    }

    @Test
    fun peerCompletingTheSamePendingPurgeDoesNotDropACookbookSwitch() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        var removals = 0
        val store = FakeCatalogStore().apply { removeRecipeFailure = IOException("disk") }
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL, SHARED) }
            recipesBlock = { _, cookbookId -> if (cookbookId == SHARED.id) listOf(SALAD) else listOf(SOUP) }
            recipeBlock = { _, _, _ -> throw ApiFailure(404, "missing") }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()
        controller.openRecipe(SOUP.id).join()
        store.removeRecipeFailure = null
        store.beforeRemoveRecipe = {
            removals++
            if (removals == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            } else {
                secondStarted.complete(Unit)
                releaseSecond.await()
            }
        }

        val refresh = controller.refresh()
        firstStarted.await()
        val switch = controller.switchCookbook(SHARED.id)
        runCurrent()
        releaseFirst.complete(Unit)
        secondStarted.await()
        releaseSecond.complete(Unit)
        refresh.join()
        switch.join()

        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SALAD), controller.state.value.recipes)
    }

    @Test
    fun failedForbiddenPurgeIsRetriedBeforeDiscoveryOrCachedContentReuse() = runTest {
        var discoveries = 0
        val store = FakeCatalogStore().apply { removeCookbookFailure = IOException("disk") }
        val api = FakeApi().apply {
            cookbooksBlock = {
                discoveries++
                listOf(PERSONAL)
            }
            recipesBlock = { _, _ -> throw ApiFailure(403, "forbidden") }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()

        assertNull(controller.state.value.activeCookbookId)
        assertTrue(controller.state.value.recipes.isEmpty())
        assertEquals(LoadStatus.ERROR, controller.state.value.catalogStatus)
        assertEquals(1, discoveries)

        store.removeCookbookFailure = null
        api.cookbooksBlock = { discoveries++; listOf(SHARED) }
        api.recipesBlock = { _, _ -> listOf(SALAD) }
        controller.refresh().join()

        assertEquals(2, discoveries)
        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SALAD), controller.state.value.recipes)
    }

    @Test
    fun refreshFromDegradedCatalogRediscoveryReconcilesMemberships() = runTest {
        val store = FakeCatalogStore().apply {
            replaceCookbooks(USER.id, listOf(PERSONAL))
            replaceRecipes(RecipeScope(USER.id, PERSONAL.id), listOf(SOUP))
        }
        var offline = true
        val api = FakeApi().apply {
            cookbooksBlock = { if (offline) throw IOException("offline") else listOf(SHARED) }
            recipesBlock = { _, _ -> listOf(SALAD) }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()
        assertEquals(LoadStatus.DEGRADED, controller.state.value.catalogStatus)

        offline = false
        controller.refresh().join()

        assertEquals(LoadStatus.FRESH, controller.state.value.catalogStatus)
        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SALAD), controller.state.value.recipes)
        assertFalse(store.memberships(USER.id).contains(PERSONAL.id))
    }

    @Test
    fun successfulDiscoveryDoesNotEraseDegradedRecipeFailure() = runTest {
        val scope = RecipeScope(USER.id, PERSONAL.id)
        val store = FakeCatalogStore().apply {
            replaceCookbooks(USER.id, listOf(PERSONAL))
            replaceRecipes(scope, listOf(SOUP))
        }
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> throw IOException("recipe offline") }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        controller.restore().join()

        assertEquals(LoadStatus.FRESH, controller.state.value.catalogStatus)
        assertEquals(LoadStatus.DEGRADED, controller.state.value.recipeStatus)
        assertEquals("Could not refresh recipes", controller.state.value.message)
        assertTrue(controller.state.value.canRetry)
    }

    @Test
    fun invalidSwitchDoesNotInvalidateAnInProgressValidSwitch() = runTest {
        val selectionStarted = CompletableDeferred<Unit>()
        val releaseSelection = CompletableDeferred<Unit>()
        val store = FakeCatalogStore()
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL, SHARED) }
            recipesBlock = { _, cookbookId -> if (cookbookId == SHARED.id) listOf(SALAD) else listOf(SOUP) }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()
        store.beforeSelect = { cookbookId ->
            if (cookbookId == SHARED.id) {
                selectionStarted.complete(Unit)
                releaseSelection.await()
            }
        }

        val validSwitch = controller.switchCookbook(SHARED.id)
        selectionStarted.await()
        controller.switchCookbook(999L).join()
        releaseSelection.complete(Unit)
        validSwitch.join()

        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SALAD), controller.state.value.recipes)
        assertEquals(LoadStatus.FRESH, controller.state.value.recipeStatus)
    }

    @Test
    fun delayed404CleanupCannotPublishAcrossSwitchAwayAndBack() = runTest {
        val removalStarted = CompletableDeferred<Unit>()
        val releaseRemoval = CompletableDeferred<Unit>()
        val store = FakeCatalogStore().apply {
            beforeRemoveRecipe = {
                removalStarted.complete(Unit)
                withContext(NonCancellable) { releaseRemoval.await() }
            }
        }
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL, SHARED) }
            recipesBlock = { _, cookbookId -> if (cookbookId == PERSONAL.id) listOf(SOUP) else listOf(SALAD) }
            recipeBlock = { _, _, _ -> throw ApiFailure(404, "missing") }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()

        val missing = controller.openRecipe(SOUP.id)
        removalStarted.await()
        val away = controller.switchCookbook(SHARED.id)
        val back = controller.switchCookbook(PERSONAL.id)
        runCurrent()
        releaseRemoval.complete(Unit)
        missing.join()
        away.join()
        back.join()
        controller.refresh().join()

        assertEquals(PERSONAL.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SOUP), controller.state.value.recipes)
        assertNull(controller.state.value.detail)
        assertEquals(LoadStatus.FRESH, controller.state.value.recipeStatus)
    }

    @Test
    fun logoutDuringDetailCancellationCannotPublishNotReadyState() = runTest {
        val detailStarted = CompletableDeferred<Unit>()
        val releaseDetail = CompletableDeferred<Unit>()
        val pending = SALAD.copy(importStatus = "pending")
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(SOUP, pending) }
            recipeBlock = { _, _, _ ->
                detailStarted.complete(Unit)
                withContext(NonCancellable) { releaseDetail.await() }
                SOUP_DETAIL
            }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()
        val firstDetail = controller.openRecipe(SOUP.id)
        detailStarted.await()
        val pendingOpen = controller.openRecipe(pending.id)
        runCurrent()
        val logout = controller.logout()
        runCurrent()
        releaseDetail.complete(Unit)
        firstDetail.join()
        pendingOpen.join()
        logout.join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertNull(controller.state.value.detail)
    }

    @Test
    fun invalidatedDetailLoadDoesNotStartApiAfterItsCacheRead() = runTest {
        val cacheReadStarted = CompletableDeferred<Unit>()
        val releaseCacheRead = CompletableDeferred<Unit>()
        val store = FakeCatalogStore().apply {
            beforeDetailRead = {
                cacheReadStarted.complete(Unit)
                withContext(NonCancellable) { releaseCacheRead.await() }
            }
        }
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ -> listOf(SOUP) }
        }
        val controller = controller(api = api, catalogStore = store, session = SESSION)
        controller.restore().join()

        val detail = controller.openRecipe(SOUP.id)
        cacheReadStarted.await()
        val logout = controller.logout()
        runCurrent()
        releaseCacheRead.complete(Unit)
        detail.join()
        logout.join()

        assertEquals(0, api.recipeCalls)
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
    }

    @Test
    fun unfetchedCachedMembershipEndsOfflineStartupInRecipeError() = runTest {
        val store = FakeCatalogStore().apply { replaceCookbooks(USER.id, listOf(PERSONAL)) }
        val api = FakeApi().apply { cookbooksBlock = { throw IOException("offline") } }
        val controller = controller(api = api, catalogStore = store, session = SESSION)

        controller.restore().join()

        assertEquals(SessionPhase.READY, controller.state.value.phase)
        assertEquals(LoadStatus.DEGRADED, controller.state.value.catalogStatus)
        assertEquals(LoadStatus.ERROR, controller.state.value.recipeStatus)
        assertTrue(controller.state.value.canRetry)
    }

    @Test
    fun accountNameUpdatePersistsAuthoritativeUserWithoutChangingCredentialsAndRestores() = runTest {
        val updated = USER.copy(name = "Ada")
        val api = FakeApi().apply { updateAccountBlock = { _, _ -> updated } }
        val sessionStore = FakeSessionStore(SESSION)
        val controller = controller(api = api, sessionStore = sessionStore, session = SESSION)
        controller.restore().join()

        controller.updateName("Ada").join()

        assertEquals(updated, controller.state.value.user)
        assertEquals(SESSION.response.token, sessionStore.value?.response?.token)
        assertEquals(SESSION.response.expiresAt, sessionStore.value?.response?.expiresAt)
        assertEquals(updated, sessionStore.value?.response?.user)
        assertEquals(AccountOperation.IDLE, controller.accountState.value.operation)

        val restored = controller(api = api, sessionStore = sessionStore)
        restored.restore().join()
        assertEquals(updated, restored.state.value.user)
    }

    @Test
    fun profileReplacementDoesNotInvalidateConcurrentRecipeSuccess() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        var recipeCalls = 0
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL) }
            recipesBlock = { _, _ ->
                recipeCalls++
                if (recipeCalls == 1) listOf(SOUP) else {
                    refreshStarted.complete(Unit)
                    releaseRefresh.await()
                    listOf(SALAD)
                }
            }
            updateAccountBlock = { _, _ -> USER.copy(name = "Ada") }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        val refresh = controller.refresh()
        refreshStarted.await()
        controller.updateName("Ada").join()
        releaseRefresh.complete(Unit)
        refresh.join()

        assertEquals("Ada", controller.state.value.user?.name)
        assertEquals(listOf(SALAD), controller.state.value.recipes)
        assertEquals(LoadStatus.FRESH, controller.state.value.recipeStatus)
    }

    @Test
    fun cookbookSwitchDoesNotCancelAccountSave() = runTest {
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        val api = FakeApi().apply {
            cookbooksBlock = { listOf(PERSONAL, SHARED) }
            recipesBlock = { _, cookbookId -> if (cookbookId == PERSONAL.id) listOf(SOUP) else listOf(SALAD) }
            updateAccountBlock = { _, _ ->
                updateStarted.complete(Unit)
                releaseUpdate.await()
                USER.copy(name = "Ada")
            }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        val saving = controller.updateName("Ada")
        updateStarted.await()
        controller.switchCookbook(SHARED.id).join()
        releaseUpdate.complete(Unit)
        saving.join()

        assertEquals("Ada", controller.state.value.user?.name)
        assertEquals(SHARED.id, controller.state.value.activeCookbookId)
        assertEquals(listOf(SALAD), controller.state.value.recipes)
    }

    @Test
    fun blockedAccountWriteCannotResurrectOldSessionAcrossLogoutAndLaterLogin() = runTest {
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val updated = USER.copy(name = "Ada")
        val nextUser = User(8, "Next", "next@example.com", false)
        val nextSession = SESSION.response.copy(token = "next-token", user = nextUser)
        val sessionStore = FakeSessionStore(SESSION).apply {
            beforeWrite = { written ->
                if (written.response.user == updated) {
                    writeStarted.complete(Unit)
                    withContext(NonCancellable) { releaseWrite.await() }
                }
            }
        }
        val api = FakeApi().apply {
            updateAccountBlock = { _, _ -> updated }
            signInBlock = { nextSession }
        }
        val controller = controller(api = api, sessionStore = sessionStore, session = SESSION)
        controller.restore().join()

        val saving = controller.updateName("Ada")
        writeStarted.await()
        val logout = controller.logout()
        runCurrent()
        assertEquals(SessionPhase.SIGNING_OUT, controller.state.value.phase)
        releaseWrite.complete(Unit)
        saving.join()
        logout.join()
        controller.signIn(SIGN_IN).join()

        assertEquals(nextUser, controller.state.value.user)
        assertEquals(nextSession, sessionStore.value?.response)
        assertEquals(AccountOperation.IDLE, controller.accountState.value.operation)
    }

    @Test
    fun rejectedPreferenceUpdateIsNotOptimisticAndRetainsAccount() = runTest {
        val requestStarted = CompletableDeferred<AccountUpdateRequest>()
        val releaseRequest = CompletableDeferred<Unit>()
        val api = FakeApi().apply {
            updateAccountBlock = { _, request ->
                requestStarted.complete(request)
                releaseRequest.await()
                throw ApiFailure(422, "Preference was rejected")
            }
        }
        val sessionStore = FakeSessionStore(SESSION)
        val controller = controller(api = api, sessionStore = sessionStore, session = SESSION)
        controller.restore().join()

        val saving = controller.updateLifecycleNotifications(false)
        val request = requestStarted.await()
        assertEquals(AccountAttributes(lifecycleNotificationsEnabled = false), request.user)
        assertEquals(true, controller.state.value.user?.lifecycleNotificationsEnabled)
        assertEquals(AccountOperation.SAVING, controller.accountState.value.operation)
        releaseRequest.complete(Unit)
        saving.join()

        assertEquals(USER, controller.state.value.user)
        assertEquals(SESSION, sessionStore.value)
        assertEquals("Preference was rejected", controller.accountState.value.error)
        assertFalse(controller.accountState.value.canRetryPersistence)
        assertEquals(SessionPhase.READY, controller.state.value.phase)
    }

    @Test
    fun acceptedAccountUpdateWithFailedEncryptedWriteRetriesOnlyPersistence() = runTest {
        val updated = USER.copy(name = "Ada")
        val sessionStore = FakeSessionStore(SESSION).apply { writeFailure = IOException("disk full") }
        val api = FakeApi().apply { updateAccountBlock = { _, _ -> updated } }
        val controller = controller(api = api, sessionStore = sessionStore, session = SESSION)
        controller.restore().join()

        controller.updateName("Ada").join()

        assertEquals(updated, controller.state.value.user)
        assertEquals(SESSION, sessionStore.value)
        assertTrue(controller.accountState.value.canRetryPersistence)
        assertNull(controller.accountState.value.error)
        assertEquals(1, api.updateAccountCalls)

        sessionStore.writeFailure = null
        controller.retryAccountPersistence().join()

        assertEquals(updated, sessionStore.value?.response?.user)
        assertEquals(1, api.updateAccountCalls)
        assertFalse(controller.accountState.value.canRetryPersistence)
        assertNull(controller.accountState.value.error)
    }

    @Test
    fun localPersistenceRetryPreservesALaterOperationErrorAndDoesNotRepeatPatch() = runTest {
        val updated = USER.copy(name = "Ada")
        val sessionStore = FakeSessionStore(SESSION).apply { writeFailure = IOException("disk full") }
        val api = FakeApi().apply { updateAccountBlock = { _, _ -> updated } }
        val controller = controller(api = api, sessionStore = sessionStore, session = SESSION)
        controller.restore().join()
        controller.updateName("Ada").join()
        api.updateAccountBlock = { _, _ -> throw IOException("offline") }

        controller.updateLifecycleNotifications(false).join()

        assertEquals("Could not update account", controller.accountState.value.error)
        assertTrue(controller.accountState.value.canRetryPersistence)
        sessionStore.writeFailure = null

        controller.retryAccountPersistence().join()

        assertEquals(updated, sessionStore.value?.response?.user)
        assertEquals(2, api.updateAccountCalls)
        assertFalse(controller.accountState.value.canRetryPersistence)
        assertEquals("Could not update account", controller.accountState.value.error)
    }

    @Test
    fun duplicateAccountMutationAndNoOpValuesDoNotIssueExtraPatches() = runTest {
        val updateStarted = CompletableDeferred<Unit>()
        val releaseUpdate = CompletableDeferred<Unit>()
        val api = FakeApi().apply {
            updateAccountBlock = { _, _ ->
                updateStarted.complete(Unit)
                releaseUpdate.await()
                USER.copy(name = "Ada")
            }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        controller.updateName(USER.name!!).join()
        controller.updateLifecycleNotifications(USER.lifecycleNotificationsEnabled).join()
        assertEquals(0, api.updateAccountCalls)

        val first = controller.updateName("Ada")
        updateStarted.await()
        controller.updateLifecycleNotifications(false).join()
        controller.updateName("Grace").join()
        assertEquals(1, api.updateAccountCalls)
        releaseUpdate.complete(Unit)
        first.join()
        assertEquals("Ada", controller.state.value.user?.name)
    }

    @Test
    fun account422AndTransportErrorsRetainSessionAndRecipeFeedback() = runTest {
        val api = FakeApi().apply { recipesBlock = { _, _ -> throw IOException("recipe offline") } }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()
        val originalRecipeMessage = controller.state.value.message
        assertEquals("Could not refresh recipes", originalRecipeMessage)

        api.updateAccountBlock = { _, _ -> throw ApiFailure(422, "Name is invalid") }
        controller.updateName("Ada").join()
        assertEquals("Name is invalid", controller.accountState.value.error)
        assertEquals(USER, controller.state.value.user)
        assertEquals(originalRecipeMessage, controller.state.value.message)

        api.updateAccountBlock = { _, _ -> throw IOException("offline") }
        controller.updateName("Ada").join()
        assertEquals("Could not update account", controller.accountState.value.error)
        assertEquals(USER, controller.state.value.user)
        assertEquals(SessionPhase.READY, controller.state.value.phase)
    }

    @Test
    fun account401RunsProtectedCleanup() = runTest {
        val sessionStore = FakeSessionStore(SESSION)
        val catalogStore = FakeCatalogStore()
        var imageCleanups = 0
        val api = FakeApi().apply { updateAccountBlock = { _, _ -> throw ApiFailure(401, "expired") } }
        val controller = controller(api, sessionStore, catalogStore, session = SESSION) { imageCleanups++ }
        controller.restore().join()

        controller.updateName("Ada").join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertNull(controller.state.value.user)
        assertTrue(sessionStore.cleared)
        assertTrue(catalogStore.cleared)
        assertEquals(1, imageCleanups)
        assertEquals(AccountOperation.IDLE, controller.accountState.value.operation)
        assertNull(controller.accountState.value.error)
    }

    @Test
    fun mismatchedAccountResponseCannotReplaceOrPersistCurrentUser() = runTest {
        val sessionStore = FakeSessionStore(SESSION)
        val api = FakeApi().apply {
            updateAccountBlock = { _, _ -> User(99, "Other", "other@example.com", false) }
        }
        val controller = controller(api = api, sessionStore = sessionStore, session = SESSION)
        controller.restore().join()

        controller.updateName("Ada").join()

        assertEquals(USER, controller.state.value.user)
        assertEquals(SESSION, sessionStore.value)
        assertEquals("The server returned a different account", controller.accountState.value.error)
    }

    @Test
    fun successfulDeletionPurgesWithoutRevocationAndAllowsLaterAuthentication() = runTest {
        val sessionStore = FakeSessionStore(SESSION)
        val catalogStore = FakeCatalogStore()
        var imageCleanups = 0
        val api = FakeApi().apply {
            deleteAccountBlock = {}
            cookbooksBlock = { listOf(PERSONAL) }
        }
        val controller = controller(api, sessionStore, catalogStore, session = SESSION) { imageCleanups++ }
        controller.restore().join()

        controller.deleteAccount().join()

        assertEquals(1, api.deleteAccountCalls)
        assertEquals(0, api.signOutCalls)
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertTrue(sessionStore.cleared)
        assertTrue(catalogStore.cleared)
        assertEquals(1, imageCleanups)

        controller.signIn(SIGN_IN).join()
        assertEquals(SessionPhase.READY, controller.state.value.phase)
    }

    @Test
    fun deletionFailureAndAmbiguousTimeoutRetainSessionWithoutAutomaticRetry() = runTest {
        val api = FakeApi().apply { deleteAccountBlock = { throw ApiFailure(500, "Delete failed") } }
        val sessionStore = FakeSessionStore(SESSION)
        val controller = controller(
            api = api,
            sessionStore = sessionStore,
            session = SESSION,
            deleteTimeoutMillis = 100,
        )
        controller.restore().join()

        controller.deleteAccount().join()
        assertEquals("Delete failed", controller.accountState.value.error)
        assertEquals(SessionPhase.READY, controller.state.value.phase)
        assertEquals(SESSION, sessionStore.value)

        api.deleteAccountBlock = { throw IOException("connection lost") }
        controller.deleteAccount().join()
        assertTrue(controller.accountState.value.error?.contains("could not be confirmed") == true)
        assertEquals(SessionPhase.READY, controller.state.value.phase)

        api.deleteAccountBlock = { awaitCancellation() }
        controller.deleteAccount().join()
        assertTrue(controller.accountState.value.error?.contains("could not be confirmed") == true)
        assertEquals(SessionPhase.READY, controller.state.value.phase)
        assertEquals(3, api.deleteAccountCalls)
        assertFalse(sessionStore.cleared)
    }

    @Test
    fun statuslessApiFailureLeavesDeletionUnconfirmedWithoutAutomaticReplay() = runTest {
        val api = FakeApi().apply {
            deleteAccountBlock = { throw ApiFailure(null, "Network request failed") }
        }
        val sessionStore = FakeSessionStore(SESSION)
        val controller = controller(api = api, sessionStore = sessionStore, session = SESSION)
        controller.restore().join()

        controller.deleteAccount().join()
        advanceUntilIdle()

        assertEquals(1, api.deleteAccountCalls)
        assertEquals(SessionPhase.READY, controller.state.value.phase)
        assertEquals(USER, controller.state.value.user)
        assertEquals(SESSION, sessionStore.value)
        assertFalse(sessionStore.cleared)
        assertEquals(AccountOperation.IDLE, controller.accountState.value.operation)
        assertEquals(
            "Account deletion could not be confirmed. Retry deliberately or sign out.",
            controller.accountState.value.error,
        )
    }

    @Test
    fun deletion401InvalidatesSessionWithoutClaimingConfirmedDeletion() = runTest {
        val api = FakeApi().apply { deleteAccountBlock = { throw ApiFailure(401, "expired") } }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        controller.deleteAccount().join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertEquals("expired", controller.state.value.authError)
        assertNull(controller.accountState.value.error)
    }

    @Test
    fun callerCancellationAfterServerAcceptedDeletionCannotSkipCleanup() = runTest {
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val sessionStore = FakeSessionStore(SESSION).apply {
            beforeClear = {
                cleanupStarted.complete(Unit)
                withContext(NonCancellable) { releaseCleanup.await() }
            }
        }
        val catalogStore = FakeCatalogStore()
        var imageCleanups = 0
        val api = FakeApi().apply { deleteAccountBlock = {} }
        val controller = controller(api, sessionStore, catalogStore, session = SESSION) { imageCleanups++ }
        controller.restore().join()

        val deleting = controller.deleteAccount()
        cleanupStarted.await()
        deleting.cancel()
        releaseCleanup.complete(Unit)
        deleting.join()

        assertTrue(deleting.isCancelled)
        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertTrue(sessionStore.cleared)
        assertTrue(catalogStore.cleared)
        assertEquals(1, imageCleanups)
    }

    @Test
    fun deletionBlocksCompetingAccountSubmissions() = runTest {
        val deleteStarted = CompletableDeferred<Unit>()
        val releaseDelete = CompletableDeferred<Unit>()
        val api = FakeApi().apply {
            deleteAccountBlock = {
                deleteStarted.complete(Unit)
                releaseDelete.await()
            }
        }
        val controller = controller(api = api, session = SESSION)
        controller.restore().join()

        val deletion = controller.deleteAccount()
        deleteStarted.await()
        assertEquals(AccountOperation.DELETING, controller.accountState.value.operation)
        controller.deleteAccount().join()
        controller.updateName("Ada").join()
        assertEquals(1, api.deleteAccountCalls)
        assertEquals(0, api.updateAccountCalls)
        releaseDelete.complete(Unit)
        deletion.join()
    }

    @Test
    fun racingAccount401AndLogoutCompletesWithoutSelfJoinOrResurrection() = runTest {
        val accountStarted = CompletableDeferred<Unit>()
        val releaseAccount = CompletableDeferred<Unit>()
        val sessionStore = FakeSessionStore(SESSION)
        val api = FakeApi().apply {
            updateAccountBlock = { _, _ ->
                accountStarted.complete(Unit)
                withContext(NonCancellable) { releaseAccount.await() }
                throw ApiFailure(401, "expired")
            }
        }
        val controller = controller(api = api, sessionStore = sessionStore, session = SESSION)
        controller.restore().join()

        val account = controller.updateName("Ada")
        accountStarted.await()
        val logout = controller.logout()
        runCurrent()
        releaseAccount.complete(Unit)
        account.join()
        logout.join()

        assertEquals(SessionPhase.SIGNED_OUT, controller.state.value.phase)
        assertNull(sessionStore.value)
        assertEquals(AccountOperation.IDLE, controller.accountState.value.operation)
    }

    @Test
    fun clearAccountErrorDoesNotDiscardAcceptedPersistenceRetry() = runTest {
        val sessionStore = FakeSessionStore(SESSION).apply { writeFailure = IOException("disk") }
        val api = FakeApi().apply { updateAccountBlock = { _, _ -> USER.copy(name = "Ada") } }
        val controller = controller(api = api, sessionStore = sessionStore, session = SESSION)
        controller.restore().join()
        controller.updateName("Ada").join()

        controller.clearAccountError().join()

        assertNull(controller.accountState.value.error)
        assertTrue(controller.accountState.value.canRetryPersistence)
    }

    private fun CoroutineScope.controller(
        api: FakeApi = FakeApi(),
        sessionStore: FakeSessionStore = FakeSessionStore(null),
        catalogStore: FakeCatalogStore = FakeCatalogStore(),
        session: StoredSession? = null,
        clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC),
        controllerScope: CoroutineScope = this,
        deleteTimeoutMillis: Long = 30_000,
        imageCleanup: suspend () -> Unit = {},
    ): SessionController {
        if (session != null) sessionStore.value = session
        return SessionController(
            api = api,
            sessionStore = sessionStore,
            catalogRepository = CatalogRepository(api, catalogStore),
            baseUrl = BASE_URL,
            clock = clock,
            scope = controllerScope,
            imageCleanup = imageCleanup,
            revokeTimeoutMillis = 100,
            deleteTimeoutMillis = deleteTimeoutMillis,
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
        var readCalls = 0
        var beforeRead: suspend () -> Unit = {}
        var beforeWrite: suspend (StoredSession) -> Unit = {}
        var beforeClear: suspend () -> Unit = {}

        override suspend fun read(): StoredSession? {
            readCalls++
            readFailure?.let { throw it }
            val result = value
            beforeRead()
            return result
        }

        override suspend fun write(session: StoredSession) {
            writeFailure?.let { throw it }
            beforeWrite(session)
            value = session
        }

        override suspend fun clear() {
            clearFailure?.let { throw it }
            beforeClear()
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
        var beforeDetailRead: suspend () -> Unit = {}
        var beforeRemoveRecipe: suspend () -> Unit = {}
        var removeRecipeFailure: Throwable? = null
        var removeCookbookFailure: Throwable? = null
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

        override suspend fun detail(scope: RecipeScope, recipeId: Long): RecipeDetail? {
            beforeDetailRead()
            return details[scope to recipeId]
        }

        override suspend fun saveDetail(scope: RecipeScope, detail: RecipeDetail) {
            if (recipeItems[scope]?.items?.any { it.id == detail.id } == true) {
                details[scope to detail.id] = detail
            }
        }

        override suspend fun removeRecipe(scope: RecipeScope, recipeId: Long) {
            removeRecipeFailure?.let { throw it }
            beforeRemoveRecipe()
            val current = recipeItems[scope] ?: CachedRecipes(emptyList(), false)
            recipeItems[scope] = current.copy(items = current.items.filterNot { it.id == recipeId })
            details.remove(scope to recipeId)
        }

        override suspend fun removeCookbook(scope: RecipeScope) {
            removeCookbookFailure?.let { throw it }
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
        var signOutCalls = 0
        var updateAccountCalls = 0
        var deleteAccountCalls = 0
        var recipeCalls = 0
        var signInBlock: suspend (SignInRequest) -> SessionResponse = { SESSION.response }
        var signUpBlock: suspend (SignUpRequest) -> SessionResponse = { SESSION.response }
        var signOutBlock: suspend (String) -> Unit = {}
        var cookbooksBlock: suspend (String) -> List<Cookbook> = { listOf(PERSONAL) }
        var recipesBlock: suspend (String, Long) -> List<RecipeSummary> = { _, _ -> emptyList() }
        var recipeBlock: suspend (String, Long, Long) -> RecipeDetail = { _, _, _ -> SOUP_DETAIL }
        var updateAccountBlock: suspend (String, AccountUpdateRequest) -> User = { _, _ -> USER }
        var deleteAccountBlock: suspend (String) -> Unit = {}

        override suspend fun signIn(request: SignInRequest): SessionResponse {
            signInCalls++
            return signInBlock(request)
        }

        override suspend fun signInWithGoogle(request: com.getmaincourse.app.data.model.GoogleSignInRequest): SessionResponse =
            error("Unused in session controller tests")

        override suspend fun signUp(request: SignUpRequest) = signUpBlock(request)
        override suspend fun signOut(token: String) {
            signOutCalls++
            signOutBlock(token)
        }
        override suspend fun updateAccount(token: String, request: AccountUpdateRequest): User {
            updateAccountCalls++
            return updateAccountBlock(token, request)
        }

        override suspend fun deleteAccount(token: String) {
            deleteAccountCalls++
            deleteAccountBlock(token)
        }

        override suspend fun submitOnboarding(request: OnboardingRequest): OnboardingResponse =
            error("Unused in session controller tests")

        override suspend fun cookbooks(token: String) = cookbooksBlock(token)
        override suspend fun recipes(token: String, cookbookId: Long) = recipesBlock(token, cookbookId)
        override suspend fun recipe(token: String, cookbookId: Long, recipeId: Long): RecipeDetail {
            recipeCalls++
            return recipeBlock(token, cookbookId, recipeId)
        }
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

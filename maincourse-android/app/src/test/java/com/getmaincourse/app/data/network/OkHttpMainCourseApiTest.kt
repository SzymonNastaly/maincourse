package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OkHttpMainCourseApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MainCourseApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = OkHttpMainCourseApi(server.url("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun signInSendsRailsPayloadAndParsesCreatedSession() = runBlocking {
        server.enqueue(
            jsonResponse(
                201,
                """
                {
                  "token": "opaque-token",
                  "expires_at": "2026-12-06T10:15:30.000Z",
                  "user": {
                    "id": 42,
                    "name": null,
                    "email": "cook@example.com",
                    "lifecycle_notifications_enabled": false
                  }
                }
                """.trimIndent(),
            ),
        )

        val response = api.signIn(SignInRequest("cook@example.com", "secret", "Pixel 9"))

        assertEquals("opaque-token", response.token)
        assertEquals("2026-12-06T10:15:30.000Z", response.expiresAt)
        assertEquals(42L, response.user.id)
        assertNull(response.user.name)
        assertFalse(response.user.lifecycleNotificationsEnabled)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/session", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Cookbook-Id"))
        assertEquals(
            Json.parseToJsonElement(
                """{"email":"cook@example.com","password":"secret","device_name":"Pixel 9"}""",
            ),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun signUpSendsSnakeCaseConfirmationAndOptionalName() = runBlocking {
        server.enqueue(
            jsonResponse(
                201,
                """
                {
                  "token": "new-token",
                  "expires_at": "2026-12-06T10:15:30Z",
                  "user": {
                    "id": 43,
                    "name": "Sam",
                    "email": "sam@example.com",
                    "lifecycle_notifications_enabled": true
                  }
                }
                """.trimIndent(),
            ),
        )

        api.signUp(
            SignUpRequest(
                name = "Sam",
                email = "sam@example.com",
                password = "password123",
                passwordConfirmation = "password123",
                deviceName = "Pixel 9",
            ),
        )

        val request = server.takeRequest()
        assertEquals("/api/v1/registration", request.path)
        assertEquals(
            Json.parseToJsonElement(
                """
                {
                  "name":"Sam",
                  "email":"sam@example.com",
                  "password":"password123",
                  "password_confirmation":"password123",
                  "device_name":"Pixel 9"
                }
                """.trimIndent(),
            ),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun errorAndErrorsBodiesBecomeApiFailuresWithHttpStatus() = runBlocking {
        server.enqueue(jsonResponse(401, """{"error":"Invalid email or password"}"""))
        server.enqueue(
            jsonResponse(
                422,
                """{"errors":["Email has already been taken","Password is too short"]}""",
            ),
        )

        val signInFailure = captureApiFailure {
            api.signIn(SignInRequest("cook@example.com", "wrong", "Pixel 9"))
        }
        val signUpFailure = captureApiFailure {
            api.signUp(
                SignUpRequest(
                    name = null,
                    email = "cook@example.com",
                    password = "short",
                    passwordConfirmation = "short",
                    deviceName = "Pixel 9",
                ),
            )
        }

        assertEquals(401, signInFailure.status)
        assertEquals("Invalid email or password", signInFailure.message)
        assertEquals(422, signUpFailure.status)
        assertEquals(
            "Email has already been taken\nPassword is too short",
            signUpFailure.message,
        )
    }

    @Test
    fun cookbookDiscoveryParsesTheCompleteArrayWithoutCookbookContext() = runBlocking {
        server.enqueue(
            jsonResponse(
                200,
                """
                [
                  {
                    "id": 1,
                    "name": "My Recipes",
                    "personal": true,
                    "recipe_count": 2,
                    "members": [{"id": 7, "email": "cook@example.com", "role": "owner"}]
                  },
                  {
                    "id": 8,
                    "name": "Family",
                    "personal": false,
                    "recipe_count": 0,
                    "members": [
                      {"id": 7, "email": "cook@example.com", "role": "collaborator"},
                      {"id": 9, "email": "friend@example.com", "role": "owner"}
                    ]
                  }
                ]
                """.trimIndent(),
            ),
        )

        val cookbooks = api.cookbooks("test-token")

        assertEquals(listOf(1L, 8L), cookbooks.map { it.id })
        assertEquals(2, cookbooks.first().recipeCount)
        assertEquals(listOf("collaborator", "owner"), cookbooks.last().members.map { it.role })
        val request = server.takeRequest()
        assertEquals("/api/v1/cookbooks", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun recipesUseCookbookHeaderAndParseNullableSummaryFieldsAndRelativeImages() = runBlocking {
        server.enqueue(
            jsonResponse(
                200,
                """
                [
                  {
                    "id": 101,
                    "name": "Soup",
                    "prep_time": null,
                    "cook_time": 30,
                    "favorite": false,
                    "cover_image_url": "/rails/active_storage/legacy-card",
                    "cover_images": {
                      "thumb": "/rails/active_storage/thumb",
                      "card": "/rails/active_storage/card",
                      "hero": "/rails/active_storage/hero"
                    },
                    "import_status": "completed",
                    "error_message": null,
                    "updated_at": "2026-09-07T08:00:00.000Z"
                  },
                  {
                    "id": 102,
                    "name": "Pending",
                    "prep_time": null,
                    "cook_time": null,
                    "favorite": false,
                    "cover_image_url": null,
                    "cover_images": null,
                    "import_status": "pending",
                    "error_message": null,
                    "updated_at": "2026-09-07T09:00:00.000Z"
                  }
                ]
                """.trimIndent(),
            ),
        )

        val recipes = api.recipes("recipe-token", 77L)

        assertEquals(listOf(101L, 102L), recipes.map { it.id })
        assertNull(recipes.first().prepTime)
        assertEquals("/rails/active_storage/card", recipes.first().coverImages?.card)
        assertNull(recipes.last().coverImages)
        assertEquals("2026-09-07T09:00:00.000Z", recipes.last().updatedAt)
        val request = server.takeRequest()
        assertEquals("/api/v1/recipes", request.path)
        assertEquals("Bearer recipe-token", request.getHeader("Authorization"))
        assertEquals("77", request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun recipeParsesFullDetailWithItsOwnUpdatedAt() = runBlocking {
        server.enqueue(
            jsonResponse(
                200,
                """
                {
                  "id": 101,
                  "name": "Soup",
                  "prep_time": null,
                  "cook_time": 30,
                  "servings": null,
                  "favorite": true,
                  "ingredients": ["1 onion", "2 cups stock"],
                  "structured_ingredients": [
                    {
                      "id": 501,
                      "position": 0,
                      "amount": "1.0",
                      "amount_max": null,
                      "unit": null,
                      "name": "onion",
                      "note": null,
                      "raw": "1 onion"
                    }
                  ],
                  "instructions": ["Chop", "Simmer"],
                  "notes": null,
                  "source_url": null,
                  "tags": [{"id": 4, "name": "Dinner"}],
                  "cover_image_url": "/rails/active_storage/legacy-hero",
                  "cover_images": {
                    "thumb": "/rails/active_storage/thumb",
                    "card": "/rails/active_storage/card",
                    "hero": "/rails/active_storage/hero"
                  },
                  "created_at": "2026-09-01T08:00:00.000Z",
                  "updated_at": "2026-09-07T10:00:00.000Z"
                }
                """.trimIndent(),
            ),
        )

        val recipe = api.recipe("detail-token", 77L, 101L)

        assertEquals(listOf("1 onion", "2 cups stock"), recipe.ingredients)
        assertEquals(listOf("Chop", "Simmer"), recipe.instructions)
        assertNull(recipe.servings)
        assertNull(recipe.notes)
        assertEquals("1.0", recipe.structuredIngredients.first().amount)
        assertEquals("Dinner", recipe.tags.single().name)
        assertEquals("/rails/active_storage/hero", recipe.coverImages?.hero)
        assertEquals("2026-09-07T10:00:00.000Z", recipe.updatedAt)
        val request = server.takeRequest()
        assertEquals("/api/v1/recipes/101", request.path)
        assertEquals("Bearer detail-token", request.getHeader("Authorization"))
        assertEquals("77", request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun signOutAcceptsEmptySuccessAndOmitsCookbookContext() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        api.signOut("logout-token")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/session", request.path)
        assertEquals("Bearer logout-token", request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun bearerAndCookbookHeadersDoNotLeakIntoLaterAuthRequests() = runBlocking {
        server.enqueue(jsonResponse(200, "[]"))
        server.enqueue(
            jsonResponse(
                201,
                """
                {
                  "token":"new-token",
                  "expires_at":"2026-12-06T10:15:30Z",
                  "user":{
                    "id":7,
                    "name":"Cook",
                    "email":"cook@example.com",
                    "lifecycle_notifications_enabled":true
                  }
                }
                """.trimIndent(),
            ),
        )

        api.recipes("old-token", 77L)
        api.signIn(SignInRequest("cook@example.com", "secret", "Pixel 9"))

        val scoped = server.takeRequest()
        val authentication = server.takeRequest()
        assertEquals("Bearer old-token", scoped.getHeader("Authorization"))
        assertEquals("77", scoped.getHeader("X-Cookbook-Id"))
        assertNull(authentication.getHeader("Authorization"))
        assertNull(authentication.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun malformedJsonBecomesStatuslessApiFailure() = runBlocking {
        server.enqueue(jsonResponse(200, "{not-json"))

        val failure = captureApiFailure { api.cookbooks("test-token") }

        assertNull(failure.status)
        assertEquals("Invalid response from server", failure.message)
    }

    @Test
    fun redirectsAreNotFollowedWithBearerCredentials() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", server.url("/credential-leak"))
                .setBody("""{"error":"Moved"}"""),
        )

        val failure = captureApiFailure { api.cookbooks("redirect-token") }

        assertEquals(302, failure.status)
        assertEquals(1, server.requestCount)
        assertEquals("/api/v1/cookbooks", server.takeRequest().path)
    }

    @Test
    fun cancellingCoroutineCancelsCallAndPreservesCancellation() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val request = async(start = CoroutineStart.UNDISPATCHED) { api.cookbooks("cancel-token") }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        request.cancelAndJoin()

        assertTrue(request.isCancelled)
        try {
            request.await()
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // Expected: cancellation must not be wrapped in ApiFailure.
        }
    }

    @Test
    fun failedPostIsNotRetriedImplicitly() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(
            jsonResponse(
                201,
                """
                {
                  "token":"unexpected",
                  "expires_at":"2026-12-06T10:15:30Z",
                  "user":{
                    "id":7,
                    "name":null,
                    "email":"cook@example.com",
                    "lifecycle_notifications_enabled":true
                  }
                }
                """.trimIndent(),
            ),
        )

        val failure = captureApiFailure {
            api.signIn(SignInRequest("cook@example.com", "secret", "Pixel 9"))
        }

        assertNull(failure.status)
        assertEquals(1, server.requestCount)
    }

    private fun jsonResponse(status: Int, body: String): MockResponse =
        MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private suspend fun captureApiFailure(block: suspend () -> Unit): ApiFailure =
        try {
            block()
            fail("Expected ApiFailure")
            error("unreachable")
        } catch (failure: ApiFailure) {
            failure
        }
}

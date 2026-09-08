package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.AccountAttributes
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartRequest
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.OnboardingAnswers
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.ShoppingItemRequest
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient
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
    private val handle = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val code = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
    private val challenge = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
    private val verifier = "ddddddddddddddddddddddddddddddddddddddddddd"
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
    fun googleSignInSendsAnonymousExactProviderPayloadAndOmitsAbsentOnboardingId() = runBlocking {
        server.enqueue(jsonResponse(201, sessionJson()))

        api.signInWithGoogle(GoogleSignInRequest("id-token", "raw-nonce", "Pixel 9"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/oauth_session", request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Cookbook-Id"))
        assertEquals(
            Json.parseToJsonElement(
                """{"provider":"google","id_token":"id-token","nonce":"raw-nonce","device_name":"Pixel 9"}""",
            ),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun googleSignInPreservesOAuthFailuresWithoutRetrying() = runBlocking {
        listOf(401, 409, 503).forEach { status ->
            server.enqueue(jsonResponse(status, """{"error":"oauth-$status"}"""))
            val failure = captureApiFailure {
                api.signInWithGoogle(
                    GoogleSignInRequest("token-$status", "nonce-$status", "Pixel", "draft-id"),
                )
            }
            assertEquals(status, failure.status)
            assertEquals("oauth-$status", failure.message)
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun appleStartSendsAnAnonymousExactPayloadAndAcceptsOnlyTheExpectedBrowserUrl() = runBlocking {
        server.enqueue(
            jsonResponse(
                201,
                """{"transaction_id":"$handle","browser_url":"${server.url("/android/apple/sign_in?transaction_id=$handle")}","expires_at":"2026-09-08T00:05:00Z"}""",
            ),
        )

        val response = api.startAppleAuthentication(AppleAuthenticationStartRequest(challenge, "debug"))

        assertEquals(handle, response.transactionId)
        assertEquals("2026-09-08T00:05:00Z", response.expiresAt)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/apple_auth_transaction", request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Cookbook-Id"))
        assertEquals(
            Json.parseToJsonElement("""{"code_challenge":"$challenge","callback":"debug"}"""),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun appleStartRejectsMalformedOrArbitraryBrowserUrls() = runBlocking {
        listOf(
            "https://attacker.example/android/apple/sign_in?transaction_id=$handle",
            server.url("/wrong?transaction_id=$handle").toString(),
            server.url("/android/apple/sign_in?transaction_id=BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB").toString(),
            server.url("/android/apple/sign_in?transaction_id=$handle&next=https://attacker.example").toString(),
        ).forEach { browserUrl ->
            server.enqueue(
                jsonResponse(
                    201,
                    """{"transaction_id":"$handle","browser_url":"$browserUrl","expires_at":"2026-09-08T00:05:00Z"}""",
                ),
            )

            val failure = captureApiFailure {
                api.startAppleAuthentication(AppleAuthenticationStartRequest(challenge, "debug"))
            }

            assertNull(failure.status)
            assertEquals("Invalid response from server", failure.message)
        }
        assertEquals(4, server.requestCount)
    }

    @Test
    fun appleProviderUnavailableIsRecoverableAndNotRetried() = runBlocking {
        server.enqueue(jsonResponse(503, """{"error":"Apple sign-in is unavailable"}"""))

        val failure = captureApiFailure {
            api.startAppleAuthentication(AppleAuthenticationStartRequest(challenge, "release"))
        }

        assertEquals(503, failure.status)
        assertEquals("Apple sign-in is unavailable", failure.message)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun appleExchangeSendsOnlyTheEphemeralProofAndDoesNotRetry() = runBlocking {
        server.enqueue(jsonResponse(201, sessionJson()))

        val response = api.exchangeAppleAuthentication(
            AppleAuthenticationExchangeRequest(handle, code, verifier, "Android", "draft-id"),
        )

        assertEquals(7L, response.user.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/apple_auth_transaction/exchange", request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Cookbook-Id"))
        assertEquals(
            Json.parseToJsonElement(
                """{"transaction_id":"$handle","exchange_code":"$code","code_verifier":"$verifier","device_name":"Android","onboarding_device_id":"draft-id"}""",
            ),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun failedApplePostsAreNeverRetriedAcrossResolvedRoutes() = runBlocking {
        assertFailedPostDoesNotTryAnotherResolvedRoute { api ->
            api.startAppleAuthentication(AppleAuthenticationStartRequest(challenge, "release"))
        }
        assertFailedPostDoesNotTryAnotherResolvedRoute { api ->
            api.exchangeAppleAuthentication(
                AppleAuthenticationExchangeRequest(handle, code, verifier, "Android"),
            )
        }
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
                onboardingDeviceId = "install-43",
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
                  "device_name":"Pixel 9",
                  "onboarding_device_id":"install-43"
                }
                """.trimIndent(),
            ),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun updateAccountUnwrapsUserAndNameOnlyPayloadOmitsUnsetPreferenceAndCookbook() = runBlocking {
        server.enqueue(
            jsonResponse(
                200,
                """
                {
                  "user": {
                    "id": 42,
                    "name": "Ada",
                    "email": "ada@example.com",
                    "lifecycle_notifications_enabled": true
                  }
                }
                """.trimIndent(),
            ),
        )

        val user = api.updateAccount(
            "account-token",
            AccountUpdateRequest(AccountAttributes(name = "Ada")),
        )

        assertEquals(42L, user.id)
        assertEquals("Ada", user.name)
        assertTrue(user.lifecycleNotificationsEnabled)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/account", request.path)
        assertEquals("Bearer account-token", request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Cookbook-Id"))
        assertEquals("""{"user":{"name":"Ada"}}""", request.body.readUtf8())
    }

    @Test
    fun updateAccountSendsFalseLifecyclePreferenceRatherThanOmittingIt() = runBlocking {
        server.enqueue(
            jsonResponse(
                200,
                """
                {
                  "user": {
                    "id": 42,
                    "name": "Ada",
                    "email": "ada@example.com",
                    "lifecycle_notifications_enabled": false
                  }
                }
                """.trimIndent(),
            ),
        )

        val user = api.updateAccount(
            "account-token",
            AccountUpdateRequest(AccountAttributes(lifecycleNotificationsEnabled = false)),
        )

        assertFalse(user.lifecycleNotificationsEnabled)
        val request = server.takeRequest()
        assertEquals(
            """{"user":{"lifecycle_notifications_enabled":false}}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun deleteAccountAcceptsEmpty204AndOmitsCookbookContext() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        api.deleteAccount("delete-token")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/account", request.path)
        assertEquals("Bearer delete-token", request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun submitOnboardingSendsExactRailsValuesWithRequiredEmptyDietAndNoProtectedHeaders() = runBlocking {
        server.enqueue(
            jsonResponse(
                201,
                """
                {
                  "id": 81,
                  "device_id": "install-81",
                  "answers": {
                    "household_size": 3,
                    "save_today": ["screenshots", "browser_bookmarks", "notes", "recipe_apps", "cookbooks", "dont_save"],
                    "diet": []
                  }
                }
                """.trimIndent(),
            ),
        )
        val answers = OnboardingAnswers(
            householdSize = 3,
            saveToday = listOf(
                "screenshots",
                "browser_bookmarks",
                "notes",
                "recipe_apps",
                "cookbooks",
                "dont_save",
            ),
        )

        val response = api.submitOnboarding(OnboardingRequest("install-81", answers))

        assertEquals(81L, response.id)
        assertEquals("install-81", response.deviceId)
        assertEquals(answers, response.answers)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/onboarding_response", request.path)
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Cookbook-Id"))
        assertEquals(
            Json.parseToJsonElement(
                """
                {
                  "device_id":"install-81",
                  "answers": {
                    "household_size":3,
                    "save_today":["screenshots","browser_bookmarks","notes","recipe_apps","cookbooks","dont_save"],
                    "diet":[]
                  }
                }
                """.trimIndent(),
            ),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun accountAndOnboardingFailuresPreserve401422429And5xxBoundaries() = runBlocking {
        server.enqueue(jsonResponse(401, """{"error":"Session invalid"}"""))
        server.enqueue(jsonResponse(422, """{"errors":["Name is too long"]}"""))
        server.enqueue(
            jsonResponse(
                429,
                """{"error":"Too many onboarding submissions. Try again later."}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val unauthorized = captureApiFailure {
            api.updateAccount("expired", AccountUpdateRequest(AccountAttributes(name = "Ada")))
        }
        val invalid = captureApiFailure {
            api.updateAccount("valid", AccountUpdateRequest(AccountAttributes(name = "A".repeat(51))))
        }
        val rateLimited = captureApiFailure {
            api.submitOnboarding(OnboardingRequest("install-rate", OnboardingAnswers(diet = emptyList())))
        }
        val unavailable = captureApiFailure { api.deleteAccount("valid") }

        assertEquals(401, unauthorized.status)
        assertEquals("Session invalid", unauthorized.message)
        assertEquals(422, invalid.status)
        assertEquals("Name is too long", invalid.message)
        assertEquals(429, rateLimited.status)
        assertEquals("Too many onboarding submissions. Try again later.", rateLimited.message)
        assertEquals(503, unavailable.status)
        assertEquals("Request failed with HTTP status 503", unavailable.message)
    }

    @Test
    fun accountMutationIsNotRetriedWhenConnectionDropsAfterRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val failure = captureApiFailure {
            api.updateAccount("account-token", AccountUpdateRequest(AccountAttributes(name = "Ada")))
        }

        assertNull(failure.status)
        assertEquals("Network request failed", failure.message)
        assertEquals(1, server.requestCount)
        assertEquals("PATCH", server.takeRequest().method)
    }

    @Test
    fun cancellingOnboardingRequestCancelsCallAndPreservesCancellation() = runBlocking {
        val callCancelled = CountDownLatch(1)
        val trackedApi = OkHttpMainCourseApi(
            server.url("/"),
            OkHttpClient.Builder()
                .eventListener(
                    object : EventListener() {
                        override fun canceled(call: Call) {
                            callCancelled.countDown()
                        }
                    },
                )
                .build(),
        )
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val request = async(Dispatchers.Default) {
            trackedApi.submitOnboarding(
                OnboardingRequest("install-cancel", OnboardingAnswers(diet = emptyList())),
            )
        }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        request.cancel()

        assertTrue("The active OkHttp call was not cancelled", callCancelled.await(1, TimeUnit.SECONDS))
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
    fun recipeBatchAlwaysRequestsOneHundredAndEncodesTheOptionalCursor() = runBlocking {
        server.enqueue(
            jsonResponse(
                200,
                """{"recipes":[${recipeDetailJson()}],"next_cursor":"next/value+="}""",
            ),
        )
        server.enqueue(jsonResponse(200, """{"recipes":[],"next_cursor":null}"""))

        val first = api.recipeBatch("batch-token", 77L)
        val second = api.recipeBatch("batch-token", 77L, "next/value+=")

        assertEquals(listOf(101L), first.recipes.map { it.id })
        assertEquals("next/value+=", first.nextCursor)
        assertTrue(second.recipes.isEmpty())
        assertNull(second.nextCursor)
        val firstRequest = server.takeRequest()
        assertEquals("/api/v1/recipes/batch?limit=100", firstRequest.path)
        assertEquals("Bearer batch-token", firstRequest.getHeader("Authorization"))
        assertEquals("77", firstRequest.getHeader("X-Cookbook-Id"))
        assertEquals(
            "next/value+=",
            server.takeRequest().requestUrl?.queryParameter("cursor"),
        )
    }

    @Test
    fun updateRecipeSendsCompleteTextSnapshotIncludingNullsAndEmptyArrays() = runBlocking {
        server.enqueue(jsonResponse(200, recipeDetailJson(name = "Clearable soup")))

        val response = api.updateRecipe(
            token = "edit-token",
            cookbookId = 77L,
            recipeId = 101L,
            request = RecipeUpdateRequest(
                name = "Clearable soup",
                prepTime = null,
                cookTime = null,
                servings = null,
                ingredients = emptyList(),
                instructions = emptyList(),
                notes = null,
                sourceUrl = null,
            ),
        )

        assertEquals("Clearable soup", response.name)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/recipes/101", request.path)
        assertEquals("Bearer edit-token", request.getHeader("Authorization"))
        assertEquals("77", request.getHeader("X-Cookbook-Id"))
        assertEquals(
            Json.parseToJsonElement(
                """
                {
                  "name":"Clearable soup",
                  "prep_time":null,
                  "cook_time":null,
                  "servings":null,
                  "ingredients":[],
                  "instructions":[],
                  "notes":null,
                  "source_url":null
                }
                """.trimIndent(),
            ),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun updateRecipeCoverStreamsJpegFileAsAuthenticatedCoverImagePart() = runBlocking {
        server.enqueue(jsonResponse(200, recipeDetailJson()))
        val directory = Files.createTempDirectory("maincourse-cover-test").toFile()
        val image = directory.resolve("cover.jpg").apply { writeBytes("jpeg-stream-body".toByteArray()) }

        try {
            api.updateRecipeCover("upload-token", 77L, 101L, image)

            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/recipes/101", request.path)
            assertEquals("Bearer upload-token", request.getHeader("Authorization"))
            assertEquals("77", request.getHeader("X-Cookbook-Id"))
            assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
            val body = request.body.readUtf8()
            assertTrue(body.contains("name=\"cover_image\"; filename=\"cover.jpg\""))
            assertTrue(body.contains("Content-Type: image/jpeg"))
            assertTrue(body.contains("jpeg-stream-body"))
        } finally {
            image.delete()
            directory.delete()
        }
    }

    @Test
    fun moveRecipeKeepsSourceCookbookHeaderAndSendsTargetCookbookJson() = runBlocking {
        server.enqueue(jsonResponse(200, recipeDetailJson()))

        val moved = api.moveRecipe("move-token", 77L, 101L, 88L)

        assertEquals(101L, moved.id)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/recipes/101", request.path)
        assertEquals("Bearer move-token", request.getHeader("Authorization"))
        assertEquals("77", request.getHeader("X-Cookbook-Id"))
        assertEquals(
            Json.parseToJsonElement("""{"cookbook_id":88}"""),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun deleteRecipeAccepts204ButPreserves404AsAnApiFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(jsonResponse(404, """{"error":"Recipe not found"}"""))

        api.deleteRecipe("delete-token", 77L, 101L)
        val failure = captureApiFailure { api.deleteRecipe("delete-token", 77L, 102L) }

        assertEquals(404, failure.status)
        assertEquals("Recipe not found", failure.message)
        val successfulRequest = server.takeRequest()
        assertEquals("DELETE", successfulRequest.method)
        assertEquals("/api/v1/recipes/101", successfulRequest.path)
        assertEquals("Bearer delete-token", successfulRequest.getHeader("Authorization"))
        assertEquals("77", successfulRequest.getHeader("X-Cookbook-Id"))
        assertEquals("/api/v1/recipes/102", server.takeRequest().path)
    }

    @Test
    fun addRecipeIngredientsSendsStableIdsAndParsesTheBareRailsArray() = runBlocking {
        server.enqueue(
            jsonResponse(
                201,
                """
                [{
                  "id":901,
                  "client_id":"stable-row-1",
                  "name":"Onion",
                  "details":"2, diced",
                  "checked_at":null,
                  "source_recipe_id":101,
                  "created_at":"2026-09-08T10:00:00.000Z",
                  "updated_at":"2026-09-08T10:00:00.000Z"
                }]
                """.trimIndent(),
            ),
        )
        val payload = ShoppingItemsRequest(
            items = listOf(
                ShoppingItemRequest(
                    clientId = "stable-row-1",
                    name = "Onion",
                    details = "2, diced",
                    checkedAt = null,
                    sourceRecipeId = 101L,
                ),
            ),
        )

        val items = api.addRecipeIngredients("shopping-token", 77L, payload)

        assertEquals(901L, items.single().id)
        assertEquals("stable-row-1", items.single().clientId)
        assertEquals("Onion", items.single().name)
        assertEquals("2, diced", items.single().details)
        assertNull(items.single().checkedAt)
        assertEquals(101L, items.single().sourceRecipeId)
        assertEquals("2026-09-08T10:00:00.000Z", items.single().createdAt)
        assertEquals("2026-09-08T10:00:00.000Z", items.single().updatedAt)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/shopping_list_items", request.path)
        assertEquals("Bearer shopping-token", request.getHeader("Authorization"))
        assertEquals("77", request.getHeader("X-Cookbook-Id"))
        assertEquals(
            Json.parseToJsonElement(
                """
                {"items":[{
                  "client_id":"stable-row-1",
                  "name":"Onion",
                  "details":"2, diced",
                  "checked_at":null,
                  "source_recipe_id":101
                }]}
                """.trimIndent(),
            ),
            Json.parseToJsonElement(request.body.readUtf8()),
        )
    }

    @Test
    fun objectValidationErrorsDoNotHideTopLevelErrorCodeOrLimit() = runBlocking {
        server.enqueue(
            jsonResponse(
                403,
                """
                {
                  "error":"Monthly import limit reached",
                  "error_code":"import_limit_reached",
                  "limit":15,
                  "errors":[{"client_id":"stable-row-1","error":"Recipe not found"}]
                }
                """.trimIndent(),
            ),
        )

        val failure = captureApiFailure { api.recipeBatch("batch-token", 77L) }

        assertEquals(403, failure.status)
        assertEquals("Monthly import limit reached", failure.message)
        assertEquals("import_limit_reached", failure.errorCode)
        assertEquals(15, failure.limit)
    }

    @Test
    fun objectValidationErrorsSupplyAMessageWhenTopLevelErrorIsAbsent() = runBlocking {
        server.enqueue(
            jsonResponse(
                422,
                """{"errors":[{"client_id":"stable-row-1","error":"Recipe not found"}]}""",
            ),
        )

        val failure = captureApiFailure {
            api.addRecipeIngredients(
                "shopping-token",
                77L,
                ShoppingItemsRequest(
                    listOf(ShoppingItemRequest("stable-row-1", "Onion", null, null, 999L)),
                ),
            )
        }

        assertEquals(422, failure.status)
        assertEquals("Recipe not found", failure.message)
        assertNull(failure.errorCode)
        assertNull(failure.limit)
    }

    @Test
    fun recipeMutationIsNotRetriedWhenConnectionDropsAfterRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val failure = captureApiFailure {
            api.updateRecipe(
                "edit-token",
                77L,
                101L,
                RecipeUpdateRequest("Soup", null, null, null, emptyList(), emptyList(), null, null),
            )
        }

        assertNull(failure.status)
        assertEquals("Network request failed", failure.message)
        assertEquals(1, server.requestCount)
        assertEquals("PATCH", server.takeRequest().method)
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
    fun streamingResponseDoesNotBlockTheCallerDispatcher() = runBlocking {
        val bodyReadStarted = CountDownLatch(1)
        val trackedApi = OkHttpMainCourseApi(
            server.url("/"),
            OkHttpClient.Builder()
                .eventListener(
                    object : EventListener() {
                        override fun responseBodyStart(call: Call) {
                            bodyReadStarted.countDown()
                        }
                    },
                )
                .build(),
        )
        server.enqueue(
            jsonResponse(200, "[${" ".repeat(1_000)}]")
                .throttleBody(100, 100, TimeUnit.MILLISECONDS),
        )

        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "test-caller-dispatcher")
        }.asCoroutineDispatcher().use { callerDispatcher ->
            val request = async(callerDispatcher) { trackedApi.cookbooks("test-token") }
            assertTrue(bodyReadStarted.await(5, TimeUnit.SECONDS))

            val marker = async(callerDispatcher) { Thread.currentThread().name }
            val markerThread = withTimeoutOrNull(250) { marker.await() }

            assertFalse(request.isCompleted)
            assertNotNull(markerThread)
            assertTrue(markerThread!!.startsWith("test-caller-dispatcher"))
            assertEquals(emptyList<Any>(), request.await())
        }
    }

    @Test
    fun cancellingStreamingResponseCancelsCallAndPreservesCancellation() = runBlocking {
        val bodyReadStarted = CountDownLatch(1)
        val callCancelled = CountDownLatch(1)
        val trackedApi = OkHttpMainCourseApi(
            server.url("/"),
            OkHttpClient.Builder()
                .eventListener(
                    object : EventListener() {
                        override fun responseBodyStart(call: Call) {
                            bodyReadStarted.countDown()
                        }

                        override fun canceled(call: Call) {
                            callCancelled.countDown()
                        }
                    },
                )
                .build(),
        )
        server.enqueue(
            jsonResponse(200, "[${" ".repeat(1_000)}]")
                .throttleBody(100, 100, TimeUnit.MILLISECONDS),
        )

        val request = async(Dispatchers.Default) { trackedApi.cookbooks("cancel-token") }
        assertTrue(bodyReadStarted.await(5, TimeUnit.SECONDS))
        request.cancel()

        assertTrue("The active OkHttp call was not cancelled", callCancelled.await(1, TimeUnit.SECONDS))
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
    fun failedGooglePostDoesNotRetryOrTryAnotherResolvedRoute() = runBlocking {
        assertFailedPostDoesNotTryAnotherResolvedRoute { api ->
            api.signInWithGoogle(GoogleSignInRequest("token", "nonce", "Pixel 9"))
        }
    }

    @Test
    fun failedEmailPostDoesNotRetryOrTryAnotherResolvedRoute() = runBlocking {
        assertFailedPostDoesNotTryAnotherResolvedRoute { api ->
            api.signIn(SignInRequest("cook@example.com", "secret", "Pixel 9"))
        }
    }

    private suspend fun assertFailedPostDoesNotTryAnotherResolvedRoute(
        request: suspend (MainCourseApi) -> Unit,
    ) {
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
        val retryHost = server.url("/").newBuilder().host("retry.test").build()
        val multiRouteClient = OkHttpClient.Builder()
            .connectTimeout(250, TimeUnit.MILLISECONDS)
            .dns(
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        listOf(
                            InetAddress.getByName("127.0.0.2"),
                            InetAddress.getByName("127.0.0.1"),
                        )
                },
            )
            .build()
        val noRetryApi = OkHttpMainCourseApi(retryHost, multiRouteClient)

        val failure = captureApiFailure {
            request(noRetryApi)
        }

        assertNull(failure.status)
        assertEquals(0, server.requestCount)
    }

    private fun jsonResponse(status: Int, body: String): MockResponse =
        MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    private fun sessionJson() =
        """{"token":"opaque","expires_at":"2026-12-06T10:15:30Z","user":{"id":7,"name":null,"email":"cook@example.com","lifecycle_notifications_enabled":true}}"""

    private fun recipeDetailJson(id: Long = 101L, name: String = "Soup") =
        """
        {
          "id":$id,
          "name":"$name",
          "prep_time":null,
          "cook_time":30,
          "servings":null,
          "favorite":false,
          "ingredients":[],
          "structured_ingredients":[],
          "instructions":[],
          "notes":null,
          "source_url":null,
          "tags":[],
          "cover_image_url":null,
          "cover_images":null,
          "created_at":"2026-09-01T08:00:00.000Z",
          "updated_at":"2026-09-08T08:00:00.000Z"
        }
        """.trimIndent()

    private suspend fun captureApiFailure(block: suspend () -> Unit): ApiFailure =
        try {
            block()
            fail("Expected ApiFailure")
            error("unreachable")
        } catch (failure: ApiFailure) {
            failure
        }
}

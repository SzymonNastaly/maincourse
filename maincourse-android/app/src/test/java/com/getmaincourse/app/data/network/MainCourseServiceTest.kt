package com.getmaincourse.app.data.network
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.UiMessage

import com.getmaincourse.app.data.model.AccountAttributes
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.CreateCookbookRequest
import com.getmaincourse.app.data.model.DeviceTokenRequest
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.NotificationOpenedRequest
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.RecipeContentImportRequest
import com.getmaincourse.app.data.model.RecipePageContent
import com.getmaincourse.app.data.model.RecipeTextImportRequest
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.RecipeUrlImportRequest
import com.getmaincourse.app.data.model.RecipeSaveRequest
import com.getmaincourse.app.data.model.RecipeSaveSource
import com.getmaincourse.app.data.model.ShoppingItemRequest
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.ShoppingItemUpdateRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.StructuredIngredient
import com.getmaincourse.app.data.session.SessionProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MainCourseServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: MainCourseService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .callFactory(ApiCallFactory(SessionProvider(), SessionEvents()))
            .addConverterFactory(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = true
                }.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(MainCourseService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sampleSaveUsesVersionedSourceAndExplicitCookbookDestination() = runTest {
        server.enqueue(jsonResponse(201, """{"recipe_id":91,"cookbook_id":42}"""))
        val saved = service.saveRecipe(42, 42, RecipeSaveRequest(RecipeSaveSource("sample", "tomato-orzo-v1"), "request-uuid"))
        assertEquals(91L, saved.recipeId)
        assertEquals(42L, saved.cookbookId)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/cookbooks/42/recipe_saves", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
        assertEquals(json("""{"source":{"type":"sample","key":"tomato-orzo-v1"},"request_id":"request-uuid"}"""), json(request.body.readUtf8()))
    }

    @Test
    fun signInUsesAnonymousSessionContract() = runTest {
        server.enqueue(jsonResponse(201, sessionJson()))

        val response = service.signIn(SignInRequest("cook@example.com", "secret", "Android", "android-device"))

        assertEquals("token", response.token)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/session", request.path)
        assertNull(request.getHeader("X-MainCourse-Anonymous"))
        assertNull(request.getHeader("Authorization"))
        assertEquals(
            json(
                """{"email":"cook@example.com","password":"secret","device_name":"Android","onboarding_device_id":"android-device"}""",
            ),
            json(request.body.readUtf8()),
        )
    }

    @Test
    fun signUpUsesAnonymousRegistrationContract() = runTest {
        server.enqueue(jsonResponse(201, sessionJson()))

        service.signUp(
            SignUpRequest("Cook", "cook@example.com", "password", "password", "Android", "android-device"),
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/registration", request.path)
        assertNull(request.getHeader("X-MainCourse-Anonymous"))
        assertNull(request.getHeader("Authorization"))
        assertEquals(
            json(
                """{"name":"Cook","email":"cook@example.com","password":"password","password_confirmation":"password","device_name":"Android","onboarding_device_id":"android-device"}""",
            ),
            json(request.body.readUtf8()),
        )
    }

    @Test
    fun onboardingUsesAnonymousResponseContract() = runTest {
        server.enqueue(
            jsonResponse(
                201,
                """{"id":9,"device_id":"android-device","answers":{"household_size":2,"diet":[]}}""",
            ),
        )

        val response = service.submitOnboarding(
            OnboardingRequest(
                deviceId = "android-device",
                answers = buildJsonObject {
                    put("household_size", 2)
                    put("diet", kotlinx.serialization.json.JsonArray(emptyList()))
                },
            ),
        )

        assertEquals(9L, response.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/onboarding_response", request.path)
        assertNull(request.getHeader("X-MainCourse-Anonymous"))
        assertNull(request.getHeader("Authorization"))
        assertEquals(
            json("""{"device_id":"android-device","answers":{"household_size":2,"diet":[]}}"""),
            json(request.body.readUtf8()),
        )
    }

    @Test
    fun cookbookListUsesUnscopedContract() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id":42,"name":"Mine","personal":true,"recipe_count":1,"members":[]}]""",
            ),
        )

        val cookbooks = service.cookbooks()

        assertEquals(42L, cookbooks.single().id)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/cookbooks", request.path)
        assertNull(request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun cookbookCollaborationUsesUnscopedServerContracts() = runTest {
        val cookbookJson = """{"id":42,"name":"Family","personal":false,"recipe_count":0,"members":[]}"""
        server.enqueue(jsonResponse(201, cookbookJson))
        server.enqueue(jsonResponse(201, """{"id":3,"token":"tok","invite_url":"https://app.getmaincourse.com/invite/tok","expires_at":"2099-01-01T00:00:00Z"}"""))
        server.enqueue(jsonResponse(200, """{"cookbook_name":"Family","inviter_email":"owner@example.test","expires_at":"2099-01-01T00:00:00Z","status":"pending"}"""))
        server.enqueue(jsonResponse(200, """{"cookbook_id":42,"cookbook_name":"Family"}"""))
        repeat(3) { server.enqueue(MockResponse().setResponseCode(204)) }

        service.createCookbook(CreateCookbookRequest("Family", true))
        service.createCookbookInvitation(42)
        service.cookbookInvitation("tok")
        service.acceptCookbookInvitation("tok")
        service.leaveCookbook(42)
        service.deleteCookbook(42)
        service.rejectCookbookInvitation("tok")

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/v1/cookbooks", create.path)
        assertEquals(json("""{"name":"Family","move_personal_recipes":true}"""), json(create.body.readUtf8()))
        assertEquals("/api/v1/cookbooks/42/invitations", server.takeRequest().path)
        assertEquals("/api/v1/invitations/tok", server.takeRequest().path)
        assertEquals("/api/v1/invitations/tok/accept", server.takeRequest().path)
        assertEquals("/api/v1/cookbooks/42/leave", server.takeRequest().path)
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals("/api/v1/invitations/tok/reject", server.takeRequest().path)
    }

    @Test
    fun recipeListUsesExplicitCookbookHeader() = runTest {
        server.enqueue(jsonResponse(200, "[]"))

        val recipes = service.recipes(cookbookId = 42)

        assertTrue(recipes.isEmpty())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/recipes", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun recipeDetailBatchUsesCursorLimitAndExplicitCookbookHeader() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"recipes":[${recipeJson()}],"next_cursor":"next-page"}""",
            ),
        )

        val response = service.recipeDetails(cookbookId = 42, cursor = "current-page", limit = 100)

        assertEquals(listOf(7L), response.recipes.map { it.id })
        assertEquals("next-page", response.nextCursor)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/recipes/batch?cursor=current-page&limit=100", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun recipeImportsUseScopedUrlAndTextContracts() = runTest {
        server.enqueue(jsonResponse(202, """{"id":8,"import_status":"pending"}"""))
        server.enqueue(jsonResponse(202, """{"id":9,"import_status":"pending"}"""))

        val urlImport = service.importRecipe(42, RecipeUrlImportRequest("https://example.com/soup"))
        val textImport = service.importRecipeText(42, RecipeTextImportRequest("Soup\n\n1 onion"))

        assertEquals(8L, urlImport.id)
        assertEquals("pending", textImport.importStatus)
        val urlRequest = server.takeRequest()
        assertEquals("POST", urlRequest.method)
        assertEquals("/api/v1/recipes/import", urlRequest.path)
        assertEquals("42", urlRequest.getHeader("X-Cookbook-Id"))
        assertEquals(
            json("""{"url":"https://example.com/soup"}"""),
            json(urlRequest.body.readUtf8()),
        )
        val textRequest = server.takeRequest()
        assertEquals("POST", textRequest.method)
        assertEquals("/api/v1/recipes/extract_from_text", textRequest.path)
        assertEquals("42", textRequest.getHeader("X-Cookbook-Id"))
        assertEquals(
            json("""{"text":"Soup\n\n1 onion"}"""),
            json(textRequest.body.readUtf8()),
        )
    }

    @Test
    fun renderedPageAndImageImportsUseTheIosCompatibleContracts() = runTest {
        server.enqueue(jsonResponse(202, """{"id":10,"import_status":"pending"}"""))
        server.enqueue(jsonResponse(202, """{"id":11,"import_status":"pending"}"""))
        val content = RecipePageContent(
            url = "https://example.com/soup",
            jsonLd = listOf("{\"@type\":\"Recipe\"}"),
            metaTags = mapOf("og:title" to "Soup"),
            coverImageCandidates = listOf("https://example.com/soup.jpg"),
            html = "<body>Soup</body>",
        )

        service.importRecipeContent(42, RecipeContentImportRequest(content))
        service.importRecipeImage(
            42,
            MultipartBody.Part.createFormData(
                "image",
                "shared-recipe.jpg",
                "fake-image".toRequestBody("image/jpeg".toMediaType()),
            ),
        )

        val contentRequest = server.takeRequest()
        assertEquals("/api/v1/recipes/import_with_content", contentRequest.path)
        assertEquals("42", contentRequest.getHeader("X-Cookbook-Id"))
        assertEquals(
            json(
                """{"url":"https://example.com/soup","json_ld":["{\"@type\":\"Recipe\"}"],"meta_tags":{"og:title":"Soup"},"cover_image_candidates":["https://example.com/soup.jpg"],"html":"<body>Soup</body>"}""",
            ),
            json(contentRequest.body.readUtf8()),
        )

        val imageRequest = server.takeRequest()
        assertEquals("/api/v1/recipes/extract_from_image", imageRequest.path)
        assertEquals("42", imageRequest.getHeader("X-Cookbook-Id"))
        assertTrue(imageRequest.getHeader("Content-Type")?.startsWith("multipart/form-data;") == true)
        val multipart = imageRequest.body.readUtf8()
        assertTrue(multipart.contains("name=\"image\"; filename=\"shared-recipe.jpg\""))
        assertTrue(multipart.contains("Content-Type: image/jpeg"))
        assertTrue(multipart.contains("fake-image"))
    }

    @Test
    fun recipeDetailUsesPathAndExplicitCookbookHeader() = runTest {
        server.enqueue(jsonResponse(200, recipeJson()))

        val recipe = service.recipe(cookbookId = 42, recipeId = 7)

        assertEquals(7L, recipe.id)
        val ingredient = recipe.structuredIngredients.single()
        assertEquals("olive oil", ingredient.canonicalName)
        assertEquals("tablespoon", ingredient.canonicalUnit)
        assertEquals("oils_spices_condiments", ingredient.category)
        assertEquals(1, ingredient.enrichmentVersion)
        assertNull(ingredient.shoppingDefaultIncluded)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/recipes/7", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun recipeDetailDecodesFalseAndNullShoppingDefaults() = runTest {
        server.enqueue(jsonResponse(200, recipeJson("\"shopping_default_included\":false,")))
        server.enqueue(jsonResponse(200, recipeJson("\"shopping_default_included\":null,")))

        val excluded = service.recipe(cookbookId = 42, recipeId = 7)
        val legacyNull = service.recipe(cookbookId = 42, recipeId = 7)

        assertFalse(excluded.structuredIngredients.single().shoppingDefaultIncluded!!)
        assertNull(legacyNull.structuredIngredients.single().shoppingDefaultIncluded)
    }

    @Test
    fun structuredIngredientCacheRoundTripPreservesFalseShoppingDefault() {
        val ingredient = StructuredIngredient(
            id = 70,
            position = 0,
            amount = null,
            amountMax = null,
            unit = null,
            name = "Salz",
            note = null,
            raw = "Salz nach Geschmack",
            shoppingDefaultIncluded = false,
        )

        val encoded = Json.encodeToString(ingredient)
        val decoded = Json.decodeFromString<StructuredIngredient>(encoded)

        assertFalse(decoded.shoppingDefaultIncluded!!)
    }

    @Test
    fun moveRecipeUsesSourceHeaderAndTargetJson() = runTest {
        server.enqueue(jsonResponse(200, recipeJson()))

        service.moveRecipe(42, 7, MoveRecipeRequest(84))

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/recipes/7", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
        assertEquals(json("""{"cookbook_id":84}"""), json(request.body.readUtf8()))
    }

    @Test
    fun updateRecipeUsesScopedPatchAndIncludesClearedNullableFields() = runTest {
        server.enqueue(jsonResponse(200, recipeJson()))

        service.updateRecipe(
            42,
            7,
            RecipeUpdateRequest(
                name = "New soup",
                prepTime = null,
                cookTime = 25,
                servings = null,
                ingredients = listOf("onion", "salt"),
                instructions = listOf("Cook"),
                notes = null,
                sourceUrl = null,
            ),
        )

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/recipes/7", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
        assertEquals(
            json(
                """{"name":"New soup","prep_time":null,"cook_time":25,"servings":null,"ingredients":["onion","salt"],"instructions":["Cook"],"notes":null,"source_url":null}""",
            ),
            json(request.body.readUtf8()),
        )
    }

    @Test
    fun coverImageUpdateUsesScopedMultipartPatch() = runTest {
        server.enqueue(jsonResponse(200, recipeJson()))
        val part = MultipartBody.Part.createFormData(
            "cover_image",
            "cover.jpg",
            "image-bytes".toRequestBody("image/jpeg".toMediaType()),
        )

        service.updateRecipeCoverImage(42, 7, part)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/recipes/7", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
        assertTrue(request.getHeader("Content-Type")?.startsWith("multipart/form-data;") == true)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"cover_image\"; filename=\"cover.jpg\""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
        assertTrue(body.contains("image-bytes"))
    }

    @Test
    fun deleteRecipeUsesPathAndExplicitCookbookHeader() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        service.deleteRecipe(42, 7)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/recipes/7", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun shoppingItemCreationUsesExplicitCookbookHeaderAndJson() = runTest {
        server.enqueue(
            jsonResponse(
                201,
                """[{"id":9,"client_id":"row-1","name":"Onion","details":null,"checked_at":null,"source_recipe_id":7,"created_at":"now","updated_at":"now"}]""",
            ),
        )
        val payload = ShoppingItemsRequest(
            listOf(ShoppingItemRequest("row-1", "Onion", null, null, 7)),
        )

        val items = service.createShoppingItems(42, payload)

        assertEquals(9L, items.single().id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/shopping_list_items", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
        assertEquals(
            json(
                """{"items":[{"client_id":"row-1","name":"Onion","details":null,"checked_at":null,"source_recipe_id":7}]}""",
            ),
            json(request.body.readUtf8()),
        )
    }

    @Test
    fun shoppingItemReplacementRequestsClearingTheExistingList() = runTest {
        server.enqueue(
            jsonResponse(
                201,
                """[{"id":9,"client_id":"row-1","name":"Onion","details":null,"checked_at":null,"source_recipe_id":7,"created_at":"now","updated_at":"now"}]""",
            ),
        )
        val payload = ShoppingItemsRequest(
            items = listOf(ShoppingItemRequest("row-1", "Onion", null, null, 7)),
            clearExisting = true,
        )

        service.createShoppingItems(42, payload)

        assertEquals(
            json(
                """{"items":[{"client_id":"row-1","name":"Onion","details":null,"checked_at":null,"source_recipe_id":7}],"clear_existing":true}""",
            ),
            json(server.takeRequest().body.readUtf8()),
        )
    }

    @Test
    fun shoppingListOperationsUseExplicitCookbookScope() = runTest {
        val itemJson =
            """{"id":9,"client_id":"row-1","name":"Onion","details":null,"checked_at":"2026-09-11T10:00:00Z","source_recipe_id":null,"created_at":"2026-09-11T09:00:00Z","updated_at":"2026-09-11T10:00:00Z"}"""
        server.enqueue(jsonResponse(200, "[$itemJson]"))
        server.enqueue(jsonResponse(200, itemJson))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        assertEquals(9L, service.shoppingListItems(42).single().id)
        service.updateShoppingItem(42, 9, ShoppingItemUpdateRequest(checked = true))
        service.deleteShoppingItem(42, 9)
        service.clearShoppingItems(42)

        val list = server.takeRequest()
        assertEquals("GET", list.method)
        assertEquals("/api/v1/shopping_list_items", list.path)
        assertEquals("42", list.getHeader("X-Cookbook-Id"))

        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/api/v1/shopping_list_items/9", update.path)
        assertEquals("42", update.getHeader("X-Cookbook-Id"))
        assertEquals(json("""{"checked":true}"""), json(update.body.readUtf8()))

        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/shopping_list_items/9", delete.path)
        assertEquals("42", delete.getHeader("X-Cookbook-Id"))

        val clear = server.takeRequest()
        assertEquals("DELETE", clear.method)
        assertEquals("/api/v1/shopping_list_items/destroy_all", clear.path)
        assertEquals("42", clear.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun accountUpdateReturnsWrapperAndDeleteIsUnscoped() = runTest {
        server.enqueue(
            jsonResponse(
                200,
                """{"user":{"id":7,"name":"New","email":"cook@example.com","lifecycle_notifications_enabled":false}}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val response = service.updateAccount(
            AccountUpdateRequest(AccountAttributes(name = "New", lifecycleNotificationsEnabled = false)),
        )
        service.deleteAccount()

        assertEquals("New", response.user.name)
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/api/v1/account", update.path)
        assertNull(update.getHeader("X-Cookbook-Id"))
        assertEquals(
            json("""{"user":{"name":"New","lifecycle_notifications_enabled":false}}"""),
            json(update.body.readUtf8()),
        )
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/account", delete.path)
        assertNull(delete.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun pushRegistrationUsesProviderAwareContract() = runTest {
        server.enqueue(
            jsonResponse(
                201,
                """{"id":11,"token":"installation-id","provider":"fcm","environment":"production"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val response = service.registerDeviceToken(
            DeviceTokenRequest(
                token = "installation-id",
                provider = "fcm",
                environment = "production",
                timeZone = "Europe/Berlin",
            ),
        )
        service.deleteDeviceToken("installation-id", "fcm")

        assertEquals("fcm", response.provider)
        val register = server.takeRequest()
        assertEquals("POST", register.method)
        assertEquals("/api/v1/device_tokens", register.path)
        assertEquals(
            json(
                """{"token":"installation-id","provider":"fcm","environment":"production","time_zone":"Europe/Berlin"}""",
            ),
            json(register.body.readUtf8()),
        )

        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/device_tokens/installation-id?provider=fcm", delete.path)
    }

    @Test
    fun notificationOpenedUsesDeliveryContract() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        service.markNotificationOpened(27, NotificationOpenedRequest(actionTaken = "opened"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/notification_deliveries/27/opened", request.path)
        assertEquals(json("""{"action_taken":"opened"}"""), json(request.body.readUtf8()))
    }

    @Test
    fun logoutUsesUnscopedSessionDelete() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        service.signOut()

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/session", request.path)
        assertNull(request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun userMessageDiscardsLegacyProseAndUsesLocalizedStatusFallbacks() = runTest {
        server.enqueue(jsonResponse(422, """{"errors":["Email is invalid","Password is too short"]}"""))
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val validation = captureHttpException {
            service.signIn(SignInRequest("bad", "short", "Android"))
        }
        val unavailable = captureHttpException { service.cookbooks() }

        val strings = ApiStrings { resource, _ -> resource.toString() }
        val fallback = UiMessage.Resource(R.string.error_sign_in)
        assertEquals(R.string.api_error_invalid_request.toString(), validation.userMessage(fallback).resolve(strings))
        assertEquals(R.string.api_error_server_unavailable.toString(), unavailable.userMessage(fallback).resolve(strings))
    }

    @Test
    fun userMessageMapsIoAndRethrowsCancellation() {
        val fallback = UiMessage.Resource(R.string.error_refresh_recipes)
        assertEquals(fallback, IOException("socket closed").userMessage(fallback))

        val cancellation = CancellationException("cancelled")
        try {
            cancellation.userMessage(fallback)
            throw AssertionError("Expected cancellation")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
    }

    private suspend fun captureHttpException(block: suspend () -> Unit): HttpException =
        try {
            block()
            throw AssertionError("Expected HttpException")
        } catch (failure: HttpException) {
            failure
        }

    private fun jsonResponse(status: Int, body: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun json(value: String) = Json.parseToJsonElement(value)

    private fun sessionJson() =
        """{"token":"token","expires_at":"2026-12-06T10:15:30Z","user":{"id":7,"name":"Cook","email":"cook@example.com","lifecycle_notifications_enabled":true}}"""

    private fun recipeJson(shoppingDefaultField: String = "") =
        """
        {
          "id":7,
          "name":"Soup",
          "prep_time":null,
          "cook_time":30,
          "servings":2,
          "favorite":false,
          "ingredients":[],
          "structured_ingredients":[{"id":70,"position":0,$shoppingDefaultField"amount":"2.0","amount_max":null,"unit":"EL","name":"Olivenöl","note":null,"raw":"2 EL Olivenöl","canonical_name":"olive oil","canonical_unit":"tablespoon","category":"oils_spices_condiments","enrichment_version":1}],
          "instructions":[],
          "notes":null,
          "source_url":null,
          "tags":[],
          "cover_image_url":null,
          "cover_images":null,
          "created_at":"2026-09-01T08:00:00Z",
          "updated_at":"2026-09-09T08:00:00Z"
        }
        """.trimIndent()
}

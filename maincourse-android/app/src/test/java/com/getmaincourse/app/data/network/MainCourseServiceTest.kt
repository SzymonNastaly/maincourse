package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.AccountAttributes
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.RecipeContentImportRequest
import com.getmaincourse.app.data.model.RecipePageContent
import com.getmaincourse.app.data.model.RecipeTextImportRequest
import com.getmaincourse.app.data.model.RecipeUrlImportRequest
import com.getmaincourse.app.data.model.ShoppingItemRequest
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.ShoppingItemUpdateRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun signInUsesAnonymousSessionContract() = runTest {
        server.enqueue(jsonResponse(201, sessionJson()))

        val response = service.signIn(SignInRequest("cook@example.com", "secret", "Android"))

        assertEquals("token", response.token)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/session", request.path)
        assertEquals("true", request.getHeader("X-MainCourse-Anonymous"))
        assertEquals(
            json("""{"email":"cook@example.com","password":"secret","device_name":"Android"}"""),
            json(request.body.readUtf8()),
        )
    }

    @Test
    fun signUpUsesAnonymousRegistrationContract() = runTest {
        server.enqueue(jsonResponse(201, sessionJson()))

        service.signUp(SignUpRequest("Cook", "cook@example.com", "password", "password", "Android"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/registration", request.path)
        assertEquals("true", request.getHeader("X-MainCourse-Anonymous"))
        assertEquals(
            json(
                """{"name":"Cook","email":"cook@example.com","password":"password","password_confirmation":"password","device_name":"Android"}""",
            ),
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
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/recipes/7", request.path)
        assertEquals("42", request.getHeader("X-Cookbook-Id"))
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
    fun logoutUsesUnscopedSessionDelete() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        service.signOut()

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/session", request.path)
        assertNull(request.getHeader("X-Cookbook-Id"))
    }

    @Test
    fun userMessageUsesServerErrorThenFallbackForHttpFailures() = runTest {
        server.enqueue(jsonResponse(422, """{"errors":["Email is invalid","Password is too short"]}"""))
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        val validation = captureHttpException {
            service.signIn(SignInRequest("bad", "short", "Android"))
        }
        val unavailable = captureHttpException { service.cookbooks() }

        assertEquals("Email is invalid\nPassword is too short", validation.userMessage("Could not sign in"))
        assertEquals("Could not load cookbooks", unavailable.userMessage("Could not load cookbooks"))
    }

    @Test
    fun userMessageMapsIoAndRethrowsCancellation() {
        assertEquals("You're offline", IOException("socket closed").userMessage("Could not refresh"))

        val cancellation = CancellationException("cancelled")
        try {
            cancellation.userMessage("ignored")
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

    private fun recipeJson() =
        """
        {
          "id":7,
          "name":"Soup",
          "prep_time":null,
          "cook_time":30,
          "servings":2,
          "favorite":false,
          "ingredients":[],
          "structured_ingredients":[],
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

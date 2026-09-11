package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.AccountResponse
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeDetailBatchResponse
import com.getmaincourse.app.data.model.RecipeContentImportRequest
import com.getmaincourse.app.data.model.RecipeImportResponse
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.RecipeTextImportRequest
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.RecipeUrlImportRequest
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.ShoppingItemUpdateRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MultipartBody

interface MainCourseService {
    @Headers("$ANONYMOUS_HEADER: true")
    @POST("api/v1/session")
    suspend fun signIn(@Body request: SignInRequest): SessionResponse

    @Headers("$ANONYMOUS_HEADER: true")
    @POST("api/v1/registration")
    suspend fun signUp(@Body request: SignUpRequest): SessionResponse

    @DELETE("api/v1/session")
    suspend fun signOut()

    @GET("api/v1/cookbooks")
    suspend fun cookbooks(): List<Cookbook>

    @GET("api/v1/recipes")
    suspend fun recipes(
        @Header("X-Cookbook-Id") cookbookId: Long,
    ): List<RecipeSummary>

    @GET("api/v1/recipes/{recipeId}")
    suspend fun recipe(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
    ): RecipeDetail

    @GET("api/v1/recipes/batch")
    suspend fun recipeDetails(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int,
    ): RecipeDetailBatchResponse

    @POST("api/v1/recipes/import")
    suspend fun importRecipe(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Body request: RecipeUrlImportRequest,
    ): RecipeImportResponse

    @POST("api/v1/recipes/import_with_content")
    suspend fun importRecipeContent(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Body request: RecipeContentImportRequest,
    ): RecipeImportResponse

    @POST("api/v1/recipes/extract_from_text")
    suspend fun importRecipeText(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Body request: RecipeTextImportRequest,
    ): RecipeImportResponse

    @Multipart
    @POST("api/v1/recipes/extract_from_image")
    suspend fun importRecipeImage(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Part image: MultipartBody.Part,
    ): RecipeImportResponse

    @PATCH("api/v1/recipes/{recipeId}")
    suspend fun moveRecipe(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
        @Body request: MoveRecipeRequest,
    ): RecipeDetail

    @PATCH("api/v1/recipes/{recipeId}")
    suspend fun updateRecipe(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
        @Body request: RecipeUpdateRequest,
    ): RecipeDetail

    @Multipart
    @PATCH("api/v1/recipes/{recipeId}")
    suspend fun updateRecipeCoverImage(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
        @Part coverImage: MultipartBody.Part,
    ): RecipeDetail

    @DELETE("api/v1/recipes/{recipeId}")
    suspend fun deleteRecipe(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
    )

    @GET("api/v1/shopping_list_items")
    suspend fun shoppingListItems(
        @Header("X-Cookbook-Id") cookbookId: Long,
    ): List<ShoppingItem>

    @POST("api/v1/shopping_list_items")
    suspend fun createShoppingItems(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Body request: ShoppingItemsRequest,
    ): List<ShoppingItem>

    @PATCH("api/v1/shopping_list_items/{itemId}")
    suspend fun updateShoppingItem(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("itemId") itemId: Long,
        @Body request: ShoppingItemUpdateRequest,
    ): ShoppingItem

    @DELETE("api/v1/shopping_list_items/{itemId}")
    suspend fun deleteShoppingItem(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("itemId") itemId: Long,
    )

    @DELETE("api/v1/shopping_list_items/destroy_all")
    suspend fun clearShoppingItems(
        @Header("X-Cookbook-Id") cookbookId: Long,
    )

    @PATCH("api/v1/account")
    suspend fun updateAccount(@Body request: AccountUpdateRequest): AccountResponse

    @DELETE("api/v1/account")
    suspend fun deleteAccount()
}

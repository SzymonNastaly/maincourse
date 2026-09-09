package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.AccountResponse
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

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

    @PATCH("api/v1/recipes/{recipeId}")
    suspend fun moveRecipe(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
        @Body request: MoveRecipeRequest,
    ): RecipeDetail

    @DELETE("api/v1/recipes/{recipeId}")
    suspend fun deleteRecipe(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
    )

    @POST("api/v1/shopping_list_items")
    suspend fun addRecipeIngredients(
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Body request: ShoppingItemsRequest,
    ): List<ShoppingItem>

    @PATCH("api/v1/account")
    suspend fun updateAccount(@Body request: AccountUpdateRequest): AccountResponse

    @DELETE("api/v1/account")
    suspend fun deleteAccount()
}

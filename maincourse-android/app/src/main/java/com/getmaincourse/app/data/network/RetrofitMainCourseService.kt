package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.AccountResponse
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartResponse
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.OnboardingResponse
import com.getmaincourse.app.data.model.RecipeBatchResponse
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

internal interface RetrofitMainCourseService {
    @POST("api/v1/session")
    suspend fun signIn(@Body request: SignInRequest): SessionResponse

    @POST("api/v1/oauth_session")
    suspend fun signInWithGoogle(@Body request: GoogleSignInRequest): SessionResponse

    @POST("api/v1/apple_auth_transaction")
    suspend fun startAppleAuthentication(@Body request: AppleAuthenticationStartRequest): AppleAuthenticationStartResponse

    @POST("api/v1/apple_auth_transaction/exchange")
    suspend fun exchangeAppleAuthentication(@Body request: AppleAuthenticationExchangeRequest): SessionResponse

    @POST("api/v1/registration")
    suspend fun signUp(@Body request: SignUpRequest): SessionResponse

    @DELETE("api/v1/session")
    suspend fun signOut(@Header("Authorization") bearer: String)

    @PATCH("api/v1/account")
    suspend fun updateAccount(
        @Header("Authorization") bearer: String,
        @Body request: AccountUpdateRequest,
    ): AccountResponse

    @DELETE("api/v1/account")
    suspend fun deleteAccount(@Header("Authorization") bearer: String)

    @POST("api/v1/onboarding_response")
    suspend fun submitOnboarding(@Body request: OnboardingRequest): OnboardingResponse

    @GET("api/v1/cookbooks")
    suspend fun cookbooks(@Header("Authorization") bearer: String): List<Cookbook>

    @GET("api/v1/recipes")
    suspend fun recipes(
        @Header("Authorization") bearer: String,
        @Header("X-Cookbook-Id") cookbookId: Long,
    ): List<RecipeSummary>

    @GET("api/v1/recipes/{recipeId}")
    suspend fun recipe(
        @Header("Authorization") bearer: String,
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
    ): RecipeDetail

    @GET("api/v1/recipes/batch")
    suspend fun recipeBatch(
        @Header("Authorization") bearer: String,
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?,
    ): RecipeBatchResponse

    @PATCH("api/v1/recipes/{recipeId}")
    suspend fun updateRecipe(
        @Header("Authorization") bearer: String,
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
        @Body request: RecipeUpdateRequest,
    ): RecipeDetail

    @Multipart
    @PATCH("api/v1/recipes/{recipeId}")
    suspend fun updateRecipeCover(
        @Header("Authorization") bearer: String,
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
        @Part coverImage: MultipartBody.Part,
    ): RecipeDetail

    @PATCH("api/v1/recipes/{recipeId}")
    suspend fun moveRecipe(
        @Header("Authorization") bearer: String,
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
        @Body request: MoveRecipeRequest,
    ): RecipeDetail

    @DELETE("api/v1/recipes/{recipeId}")
    suspend fun deleteRecipe(
        @Header("Authorization") bearer: String,
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Path("recipeId") recipeId: Long,
    )

    @POST("api/v1/shopping_list_items")
    suspend fun addRecipeIngredients(
        @Header("Authorization") bearer: String,
        @Header("X-Cookbook-Id") cookbookId: Long,
        @Body request: ShoppingItemsRequest,
    ): List<ShoppingItem>
}

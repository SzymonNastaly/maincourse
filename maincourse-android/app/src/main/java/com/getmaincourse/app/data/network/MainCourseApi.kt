package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartResponse
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.OnboardingResponse
import com.getmaincourse.app.data.model.RecipeBatchResponse
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.User
import java.io.File

interface MainCourseApi {
    suspend fun signIn(request: SignInRequest): SessionResponse

    suspend fun signInWithGoogle(request: GoogleSignInRequest): SessionResponse

    suspend fun startAppleAuthentication(request: AppleAuthenticationStartRequest): AppleAuthenticationStartResponse

    suspend fun exchangeAppleAuthentication(request: AppleAuthenticationExchangeRequest): SessionResponse

    suspend fun signUp(request: SignUpRequest): SessionResponse

    suspend fun signOut(token: String)

    suspend fun updateAccount(token: String, request: AccountUpdateRequest): User

    suspend fun deleteAccount(token: String)

    suspend fun submitOnboarding(request: OnboardingRequest): OnboardingResponse

    suspend fun cookbooks(token: String): List<Cookbook>

    suspend fun recipes(token: String, cookbookId: Long): List<RecipeSummary>

    suspend fun recipe(token: String, cookbookId: Long, recipeId: Long): RecipeDetail

    suspend fun recipeBatch(token: String, cookbookId: Long, cursor: String? = null): RecipeBatchResponse

    suspend fun updateRecipe(
        token: String,
        cookbookId: Long,
        recipeId: Long,
        request: RecipeUpdateRequest,
    ): RecipeDetail

    suspend fun updateRecipeCover(token: String, cookbookId: Long, recipeId: Long, image: File): RecipeDetail

    suspend fun moveRecipe(
        token: String,
        sourceCookbookId: Long,
        recipeId: Long,
        targetCookbookId: Long,
    ): RecipeDetail

    suspend fun deleteRecipe(token: String, cookbookId: Long, recipeId: Long)

    suspend fun addRecipeIngredients(
        token: String,
        cookbookId: Long,
        request: ShoppingItemsRequest,
    ): List<ShoppingItem>
}

package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest

interface MainCourseApi {
    suspend fun signIn(request: SignInRequest): SessionResponse

    suspend fun signUp(request: SignUpRequest): SessionResponse

    suspend fun signOut(token: String)

    suspend fun cookbooks(token: String): List<Cookbook>

    suspend fun recipes(token: String, cookbookId: Long): List<RecipeSummary>

    suspend fun recipe(token: String, cookbookId: Long, recipeId: Long): RecipeDetail
}

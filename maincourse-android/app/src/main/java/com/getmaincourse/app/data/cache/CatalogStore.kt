package com.getmaincourse.app.data.cache

import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary

data class RecipeScope(val userId: Long, val cookbookId: Long)

data class CachedRecipes(val items: List<RecipeSummary>, val fetched: Boolean)

interface CatalogStore {
    suspend fun cookbooks(userId: Long): List<Cookbook>

    suspend fun replaceCookbooks(userId: Long, items: List<Cookbook>)

    suspend fun selectedCookbookId(userId: Long): Long?

    suspend fun selectCookbook(userId: Long, cookbookId: Long)

    suspend fun recipes(scope: RecipeScope): CachedRecipes

    suspend fun replaceRecipes(scope: RecipeScope, items: List<RecipeSummary>)

    suspend fun detail(scope: RecipeScope, recipeId: Long): RecipeDetail?

    suspend fun saveDetail(scope: RecipeScope, detail: RecipeDetail)

    suspend fun removeRecipe(scope: RecipeScope, recipeId: Long)

    suspend fun removeCookbook(scope: RecipeScope)

    suspend fun clear()
}

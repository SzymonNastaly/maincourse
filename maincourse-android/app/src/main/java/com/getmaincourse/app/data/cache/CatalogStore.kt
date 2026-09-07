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

    /**
     * Persists a selection for an existing [userId]/[cookbookId] membership.
     * Implementations reject a selection when that membership has not first been stored by
     * [replaceCookbooks]. Fakes must enforce the same precondition.
     */
    suspend fun selectCookbook(userId: Long, cookbookId: Long)

    suspend fun recipes(scope: RecipeScope): CachedRecipes

    /**
     * Replaces recipes for a scope whose cookbook membership has already been stored by
     * [replaceCookbooks]. Implementations reject missing memberships, including empty refreshes.
     * Fakes must enforce the same precondition.
     */
    suspend fun replaceRecipes(scope: RecipeScope, items: List<RecipeSummary>)

    suspend fun detail(scope: RecipeScope, recipeId: Long): RecipeDetail?

    suspend fun saveDetail(scope: RecipeScope, detail: RecipeDetail)

    suspend fun removeRecipe(scope: RecipeScope, recipeId: Long)

    suspend fun removeCookbook(scope: RecipeScope)

    suspend fun clear()
}

package com.getmaincourse.app.data.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

data class StoredRecipeList(
    val items: List<RecipeEntity>,
    val fetched: Boolean,
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM cookbooks WHERE userId = :userId ORDER BY listPosition")
    suspend fun cookbooks(userId: Long): List<CookbookEntity>

    @Upsert
    suspend fun upsertCookbooks(items: List<CookbookEntity>)

    @Query("DELETE FROM cookbooks WHERE userId = :userId")
    suspend fun deleteCookbooks(userId: Long)

    @Query("DELETE FROM cookbooks WHERE userId = :userId AND cookbookId NOT IN (:retainedIds)")
    suspend fun deleteCookbooksExcept(userId: Long, retainedIds: List<Long>)

    @Transaction
    suspend fun replaceCookbooks(userId: Long, items: List<CookbookEntity>) {
        if (items.isNotEmpty()) upsertCookbooks(items)
        if (items.isEmpty()) {
            deleteCookbooks(userId)
        } else {
            deleteCookbooksExcept(userId, items.map(CookbookEntity::cookbookId))
        }
    }

    @Query("SELECT cookbookId FROM selected_cookbooks WHERE userId = :userId")
    suspend fun selectedCookbookId(userId: Long): Long?

    @Upsert
    suspend fun selectCookbook(selection: SelectedCookbookEntity)

    @Query(
        "SELECT * FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId ORDER BY listPosition",
    )
    suspend fun recipes(userId: Long, cookbookId: Long): List<RecipeEntity>

    @Query(
        "SELECT * FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId",
    )
    suspend fun recipe(userId: Long, cookbookId: Long, recipeId: Long): RecipeEntity?

    @Upsert
    suspend fun upsertRecipes(items: List<RecipeEntity>)

    @Query("DELETE FROM recipes WHERE userId = :userId AND cookbookId = :cookbookId")
    suspend fun deleteRecipes(userId: Long, cookbookId: Long)

    @Query(
        "DELETE FROM recipes WHERE userId = :userId AND cookbookId = :cookbookId " +
            "AND recipeId NOT IN (:retainedIds)",
    )
    suspend fun deleteRecipesExcept(userId: Long, cookbookId: Long, retainedIds: List<Long>)

    @Upsert
    suspend fun markRecipesFetched(fetch: RecipeFetchEntity)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM recipe_fetches " +
            "WHERE userId = :userId AND cookbookId = :cookbookId)",
    )
    suspend fun recipesFetched(userId: Long, cookbookId: Long): Boolean

    @Transaction
    suspend fun storedRecipes(userId: Long, cookbookId: Long): StoredRecipeList = StoredRecipeList(
        items = recipes(userId, cookbookId),
        fetched = recipesFetched(userId, cookbookId),
    )

    @Transaction
    suspend fun replaceRecipes(
        userId: Long,
        cookbookId: Long,
        items: List<RecipeEntity>,
    ) {
        val existing = recipes(userId, cookbookId).associateBy(RecipeEntity::recipeId)
        val replacements = items.map { item ->
            item.copy(
                detailJson = existing[item.recipeId]?.detailJson,
                detailUpdatedAt = existing[item.recipeId]?.detailUpdatedAt,
            )
        }
        if (replacements.isNotEmpty()) upsertRecipes(replacements)
        if (items.isEmpty()) {
            deleteRecipes(userId, cookbookId)
        } else {
            deleteRecipesExcept(userId, cookbookId, items.map(RecipeEntity::recipeId))
        }
        markRecipesFetched(RecipeFetchEntity(userId, cookbookId))
    }

    @Query(
        "UPDATE recipes SET detailJson = :detailJson, detailUpdatedAt = :detailUpdatedAt " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId",
    )
    suspend fun updateDetail(
        userId: Long,
        cookbookId: Long,
        recipeId: Long,
        detailJson: String,
        detailUpdatedAt: String,
    ): Int

    @Query(
        "DELETE FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId",
    )
    suspend fun removeRecipe(userId: Long, cookbookId: Long, recipeId: Long)

    @Query("DELETE FROM cookbooks WHERE userId = :userId AND cookbookId = :cookbookId")
    suspend fun removeCookbook(userId: Long, cookbookId: Long)

    @Query("DELETE FROM cookbooks")
    suspend fun clear()
}

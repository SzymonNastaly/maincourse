package com.getmaincourse.app.data.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class StoredRecipeList(
    val items: List<RecipeEntity>,
    val fetched: Boolean,
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM cookbooks WHERE userId = :userId ORDER BY listPosition")
    fun observeCookbooks(userId: Long): Flow<List<CookbookEntity>>

    @Query("SELECT * FROM cookbooks WHERE userId = :userId ORDER BY listPosition")
    suspend fun cookbooks(userId: Long): List<CookbookEntity>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM cookbooks " +
            "WHERE userId = :userId AND cookbookId = :cookbookId)",
    )
    suspend fun hasCookbook(userId: Long, cookbookId: Long): Boolean

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
    fun observeSelectedCookbookId(userId: Long): Flow<Long?>

    @Query("SELECT cookbookId FROM selected_cookbooks WHERE userId = :userId")
    suspend fun selectedCookbookId(userId: Long): Long?

    @Upsert
    suspend fun selectCookbook(selection: SelectedCookbookEntity)

    @Query(
        "SELECT * FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId ORDER BY listPosition",
    )
    fun observeRecipes(userId: Long, cookbookId: Long): Flow<List<RecipeEntity>>

    @Query(
        "SELECT * FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId ORDER BY listPosition",
    )
    suspend fun recipes(userId: Long, cookbookId: Long): List<RecipeEntity>

    @Query(
        "SELECT * FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId",
    )
    fun observeRecipe(userId: Long, cookbookId: Long, recipeId: Long): Flow<RecipeEntity?>

    @Query(
        "SELECT * FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId",
    )
    suspend fun recipe(userId: Long, cookbookId: Long, recipeId: Long): RecipeEntity?

    @Upsert
    suspend fun upsertRecipes(items: List<RecipeEntity>)

    @Query(
        "SELECT COALESCE(MAX(listPosition), -1) + 1 FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId",
    )
    suspend fun nextRecipePosition(userId: Long, cookbookId: Long): Int

    @Query("DELETE FROM recipes WHERE userId = :userId AND cookbookId = :cookbookId")
    suspend fun deleteRecipes(userId: Long, cookbookId: Long)

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
            )
        }
        deleteRecipes(userId, cookbookId)
        if (replacements.isNotEmpty()) upsertRecipes(replacements)
        markRecipesFetched(RecipeFetchEntity(userId, cookbookId))
    }

    @Query(
        "UPDATE recipes SET detailJson = :detailJson " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId",
    )
    suspend fun updateDetail(
        userId: Long,
        cookbookId: Long,
        recipeId: Long,
        detailJson: String,
    ): Int

    @Query(
        "UPDATE recipes SET detailJson = NULL " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId " +
            "AND detailJson = :invalidDetailJson",
    )
    suspend fun clearDetailIfInvalid(
        userId: Long,
        cookbookId: Long,
        recipeId: Long,
        invalidDetailJson: String,
    ): Int

    @Query("DELETE FROM recipe_fetches WHERE userId = :userId AND cookbookId = :cookbookId")
    suspend fun clearRecipesFetched(userId: Long, cookbookId: Long)

    @Transaction
    suspend fun invalidateRecipes(userId: Long, cookbookId: Long) {
        deleteRecipes(userId, cookbookId)
        clearRecipesFetched(userId, cookbookId)
    }

    @Query(
        "DELETE FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId",
    )
    suspend fun removeRecipe(userId: Long, cookbookId: Long, recipeId: Long)

    @Query("DELETE FROM cookbooks WHERE userId = :userId AND cookbookId = :cookbookId")
    suspend fun removeCookbook(userId: Long, cookbookId: Long)

    @Query(
        "DELETE FROM cookbooks WHERE userId = :userId AND cookbookId = :cookbookId " +
            "AND cookbookJson = :invalidCookbookJson",
    )
    suspend fun removeCookbookIfInvalid(
        userId: Long,
        cookbookId: Long,
        invalidCookbookJson: String,
    ): Int

    @Query("DELETE FROM cookbooks")
    suspend fun clear()
}

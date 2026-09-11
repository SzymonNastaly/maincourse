package com.getmaincourse.app.data.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM cookbooks WHERE userId = :userId ORDER BY listPosition")
    fun observeCookbooks(userId: Long): Flow<List<CookbookEntity>>

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
        "DELETE FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId",
    )
    suspend fun removeRecipe(userId: Long, cookbookId: Long, recipeId: Long)

    @Query(
        "SELECT * FROM shopping_list_items " +
            "WHERE userId = :userId AND cookbookId = :cookbookId " +
            "ORDER BY CASE WHEN checkedAt IS NULL THEN 0 ELSE 1 END, " +
            "CASE WHEN checkedAt IS NULL THEN createdAt ELSE checkedAt END DESC",
    )
    fun observeShoppingItems(userId: Long, cookbookId: Long): Flow<List<ShoppingItemEntity>>

    @Upsert
    suspend fun upsertShoppingItems(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM shopping_list_items WHERE userId = :userId AND cookbookId = :cookbookId")
    suspend fun deleteShoppingItems(userId: Long, cookbookId: Long)

    @Query(
        "DELETE FROM shopping_list_items " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND itemId = :itemId",
    )
    suspend fun deleteShoppingItem(userId: Long, cookbookId: Long, itemId: Long)

    @Query(
        "DELETE FROM shopping_list_items " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND itemId NOT IN (:retainedIds)",
    )
    suspend fun deleteShoppingItemsExcept(userId: Long, cookbookId: Long, retainedIds: List<Long>)

    @Transaction
    suspend fun replaceShoppingItems(
        userId: Long,
        cookbookId: Long,
        items: List<ShoppingItemEntity>,
    ) {
        if (items.isNotEmpty()) upsertShoppingItems(items)
        if (items.isEmpty()) {
            deleteShoppingItems(userId, cookbookId)
        } else {
            deleteShoppingItemsExcept(userId, cookbookId, items.map(ShoppingItemEntity::itemId))
        }
    }

}

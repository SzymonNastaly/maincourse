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
        "SELECT cursor FROM recipe_detail_syncs " +
            "WHERE userId = :userId AND cookbookId = :cookbookId",
    )
    suspend fun recipeDetailSyncCursor(userId: Long, cookbookId: Long): String?

    @Upsert
    suspend fun upsertRecipeDetailSync(sync: RecipeDetailSyncEntity)

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
        "DELETE FROM recipes " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId",
    )
    suspend fun removeRecipe(userId: Long, cookbookId: Long, recipeId: Long)

    @Query(
        "SELECT * FROM recipe_search_documents " +
            "WHERE userId = :userId AND cookbookId = :cookbookId",
    )
    suspend fun recipeSearchDocuments(userId: Long, cookbookId: Long): List<RecipeSearchDocumentEntity>

    @Upsert
    suspend fun upsertRecipeSearchDocuments(items: List<RecipeSearchDocumentEntity>)

    @Query(
        "DELETE FROM recipe_search_documents " +
            "WHERE userId = :userId AND cookbookId = :cookbookId",
    )
    suspend fun deleteRecipeSearchDocuments(userId: Long, cookbookId: Long)

    @Query(
        "DELETE FROM recipe_search_documents " +
            "WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId IN (:recipeIds)",
    )
    suspend fun deleteRecipeSearchDocuments(userId: Long, cookbookId: Long, recipeIds: List<Long>)

    @Query(
        """
        SELECT recipes.*
        FROM recipe_search_documents_fts
        JOIN recipe_search_documents
            ON recipe_search_documents_fts.rowid = recipe_search_documents.rowid
        JOIN recipes
            ON recipes.userId = recipe_search_documents.userId
            AND recipes.cookbookId = recipe_search_documents.cookbookId
            AND recipes.recipeId = recipe_search_documents.recipeId
        WHERE recipe_search_documents.userId = :userId
            AND recipe_search_documents.cookbookId = :cookbookId
            AND recipe_search_documents_fts MATCH :query
        ORDER BY CASE
            WHEN recipe_search_documents_fts.rowid IN (
                SELECT rowid FROM recipe_search_documents_fts
                WHERE recipe_search_documents_fts MATCH :nameQuery
            ) THEN 0
            WHEN recipe_search_documents_fts.rowid IN (
                SELECT rowid FROM recipe_search_documents_fts
                WHERE recipe_search_documents_fts MATCH :ingredientQuery
            ) THEN 1
            ELSE 2
        END,
        recipe_search_documents.updatedAt DESC,
        recipes.listPosition
        LIMIT :limit
        """,
    )
    fun observeRecipeSearchResults(
        userId: Long,
        cookbookId: Long,
        query: String,
        nameQuery: String,
        ingredientQuery: String,
        limit: Int,
    ): Flow<List<RecipeEntity>>

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

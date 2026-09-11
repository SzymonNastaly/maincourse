package com.getmaincourse.app.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cookbooks",
    primaryKeys = ["userId", "cookbookId"],
)
data class CookbookEntity(
    val userId: Long,
    val cookbookId: Long,
    val listPosition: Int,
    val cookbookJson: String,
)

@Entity(
    tableName = "selected_cookbooks",
    primaryKeys = ["userId"],
    foreignKeys = [
        ForeignKey(
            entity = CookbookEntity::class,
            parentColumns = ["userId", "cookbookId"],
            childColumns = ["userId", "cookbookId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["userId", "cookbookId"], unique = true)],
)
data class SelectedCookbookEntity(
    val userId: Long,
    val cookbookId: Long,
)

@Entity(
    tableName = "recipe_detail_syncs",
    primaryKeys = ["userId", "cookbookId"],
    foreignKeys = [
        ForeignKey(
            entity = CookbookEntity::class,
            parentColumns = ["userId", "cookbookId"],
            childColumns = ["userId", "cookbookId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class RecipeDetailSyncEntity(
    val userId: Long,
    val cookbookId: Long,
    val cursor: String,
)

@Entity(
    tableName = "recipes",
    primaryKeys = ["userId", "cookbookId", "recipeId"],
    foreignKeys = [
        ForeignKey(
            entity = CookbookEntity::class,
            parentColumns = ["userId", "cookbookId"],
            childColumns = ["userId", "cookbookId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["userId", "cookbookId"])],
)
data class RecipeEntity(
    val userId: Long,
    val cookbookId: Long,
    val recipeId: Long,
    val listPosition: Int,
    val summaryJson: String,
    val detailJson: String? = null,
)

@Entity(
    tableName = "recipe_search_documents",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["userId", "cookbookId", "recipeId"],
            childColumns = ["userId", "cookbookId", "recipeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["userId", "cookbookId", "recipeId"], unique = true)],
)
data class RecipeSearchDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0,
    val userId: Long,
    val cookbookId: Long,
    val recipeId: Long,
    val name: String,
    val ingredients: String,
    val instructions: String,
    val updatedAt: String,
)

@Fts4(
    contentEntity = RecipeSearchDocumentEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=2"],
    prefix = [2, 3, 4],
)
@Entity(tableName = "recipe_search_documents_fts")
data class RecipeSearchFtsEntity(
    val name: String,
    val ingredients: String,
    val instructions: String,
)

@Entity(
    tableName = "shopping_list_items",
    primaryKeys = ["userId", "cookbookId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = CookbookEntity::class,
            parentColumns = ["userId", "cookbookId"],
            childColumns = ["userId", "cookbookId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["userId", "cookbookId"])],
)
data class ShoppingItemEntity(
    val userId: Long,
    val cookbookId: Long,
    val itemId: Long,
    val checkedAt: String?,
    val createdAt: String,
    val itemJson: String,
)

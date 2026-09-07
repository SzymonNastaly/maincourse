package com.getmaincourse.app.data.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

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
    tableName = "recipe_fetches",
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
data class RecipeFetchEntity(
    val userId: Long,
    val cookbookId: Long,
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

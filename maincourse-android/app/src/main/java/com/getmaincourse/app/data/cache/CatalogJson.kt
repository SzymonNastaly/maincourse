package com.getmaincourse.app.data.cache

import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.ShoppingItem
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal fun Cookbook.toEntity(userId: Long, position: Int, json: Json) = CookbookEntity(
    userId = userId,
    cookbookId = id,
    listPosition = position,
    cookbookJson = json.encodeToString(this),
)

internal fun CookbookEntity.toCookbook(json: Json): Cookbook? = json.decodeOrNull(cookbookJson)

internal fun RecipeSummary.toEntity(
    userId: Long,
    cookbookId: Long,
    position: Int,
    json: Json,
) = RecipeEntity(
    userId = userId,
    cookbookId = cookbookId,
    recipeId = id,
    listPosition = position,
    summaryJson = json.encodeToString(this),
)

internal fun RecipeEntity.toSummary(json: Json): RecipeSummary? = json.decodeOrNull(summaryJson)

internal fun RecipeEntity.toDetail(json: Json): RecipeDetail? = detailJson?.let(json::decodeOrNull)

internal fun RecipeDetail.toJson(json: Json): String = json.encodeToString(this)

internal fun RecipeDetail.toSummary(previous: RecipeSummary?): RecipeSummary = RecipeSummary(
    id = id,
    name = name,
    prepTime = prepTime,
    cookTime = cookTime,
    favorite = favorite,
    coverImageUrl = coverImageUrl,
    coverImages = coverImages,
    importStatus = previous?.importStatus ?: "completed",
    errorMessage = previous?.errorMessage,
    updatedAt = updatedAt,
)

internal fun ShoppingItem.toEntity(userId: Long, cookbookId: Long, json: Json) = ShoppingItemEntity(
    userId = userId,
    cookbookId = cookbookId,
    itemId = id,
    checkedAt = checkedAt,
    createdAt = createdAt,
    itemJson = json.encodeToString(this),
)

internal fun ShoppingItemEntity.toShoppingItem(json: Json): ShoppingItem? = json.decodeOrNull(itemJson)

private inline fun <reified T> Json.decodeOrNull(value: String): T? = try {
    decodeFromString<T>(value)
} catch (_: SerializationException) {
    null
}

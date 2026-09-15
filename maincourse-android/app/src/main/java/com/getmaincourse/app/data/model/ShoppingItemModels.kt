package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShoppingItemsRequest(
    val items: List<ShoppingItemRequest>,
    @SerialName("clear_existing")
    val clearExisting: Boolean = false,
)

@Serializable
data class ShoppingItemRequest(
    @SerialName("client_id")
    val clientId: String,
    val name: String,
    val details: String?,
    @SerialName("checked_at")
    val checkedAt: String?,
    @SerialName("source_recipe_id")
    val sourceRecipeId: Long?,
)

@Serializable
data class ShoppingItemUpdateRequest(
    val checked: Boolean,
)

@Serializable
data class ShoppingItem(
    val id: Long,
    @SerialName("client_id")
    val clientId: String,
    val name: String,
    val details: String?,
    @SerialName("checked_at")
    val checkedAt: String?,
    @SerialName("source_recipe_id")
    val sourceRecipeId: Long?,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

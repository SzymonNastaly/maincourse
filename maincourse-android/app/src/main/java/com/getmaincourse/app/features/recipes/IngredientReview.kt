package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.model.ShoppingItemRequest
import com.getmaincourse.app.data.model.StructuredIngredient
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class ShoppingItemInput(
    val clientId: String,
    val name: String,
    val details: String?,
    val checkedAt: String? = null,
    val sourceRecipeId: Long?,
) {
    internal fun toRequest() = ShoppingItemRequest(clientId, name, details, checkedAt, sourceRecipeId)
}

@Serializable
data class IngredientReviewItem(
    val clientId: String,
    val name: String,
    val details: String?,
    val sourceRecipeId: Long,
    val included: Boolean = true,
)

@Serializable
data class IngredientReview(
    val recipeId: Long,
    val items: List<IngredientReviewItem>,
) {
    fun withIncluded(clientId: String, included: Boolean): IngredientReview = copy(
        items = items.map { if (it.clientId == clientId) it.copy(included = included) else it },
    )

    fun includedPayload(): List<ShoppingItemInput> = items.filter(IngredientReviewItem::included).map {
        ShoppingItemInput(it.clientId, it.name, it.details, null, it.sourceRecipeId)
    }

    companion object {
        fun create(
            recipeId: Long,
            ingredients: List<StructuredIngredient>,
            portions: Int,
            baseServings: Int?,
            newId: () -> String = { UUID.randomUUID().toString() },
        ): IngredientReview = IngredientReview(
            recipeId,
            ingredients.sortedBy(StructuredIngredient::position).map { ingredient ->
                val (name, details) = if (baseServings != null && baseServings > 0) {
                    IngredientFormatter.reviewParts(ingredient, portions, baseServings)
                } else {
                    ingredient.raw to null
                }
                IngredientReviewItem(newId(), name, details, recipeId)
            },
        )
    }
}

package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.model.ShoppingItemRequest
import com.getmaincourse.app.data.model.StructuredIngredient
import java.util.UUID

data class ShoppingItemInput(
    val clientId: String,
    val name: String,
    val details: String?,
    val checkedAt: String? = null,
    val sourceRecipeId: Long?,
) {
    internal fun toRequest() = ShoppingItemRequest(clientId, name, details, checkedAt, sourceRecipeId)
}

data class IngredientReviewItem(
    val clientId: String,
    val name: String,
    val details: String?,
    val sourceRecipeId: Long,
    val included: Boolean = true,
)

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
            baseServings: Int,
            newId: () -> String = { UUID.randomUUID().toString() },
        ): IngredientReview = IngredientReview(
            recipeId,
            ingredients.sortedBy(StructuredIngredient::position).map { ingredient ->
                val (name, details) = IngredientFormatter.reviewParts(ingredient, portions, baseServings)
                IngredientReviewItem(newId(), name, details, recipeId)
            },
        )
    }
}

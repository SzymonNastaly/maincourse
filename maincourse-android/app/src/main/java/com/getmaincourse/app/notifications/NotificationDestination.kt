package com.getmaincourse.app.notifications

import android.content.Intent

sealed interface NotificationDestination {
    val deliveryId: Long?

    data class Recipe(
        val recipeId: Long,
        val cookbookId: Long?,
        override val deliveryId: Long,
    ) : NotificationDestination

    data class ShoppingList(
        val cookbookId: Long?,
        override val deliveryId: Long?,
    ) : NotificationDestination

    data object Home : NotificationDestination {
        override val deliveryId: Long? = null
    }

    companion object {
        fun from(intent: Intent): NotificationDestination? = from(
            buildMap {
                intent.extras?.keySet()?.forEach { key ->
                    intent.getStringExtra(key)?.let { put(key, it) }
                }
            },
        )

        fun from(data: Map<String, String>): NotificationDestination? {
            val campaign = data["campaign"]
            val deliveryId = data["delivery_id"]?.toLongOrNull()
            if (!campaign.isNullOrBlank() && deliveryId != null) {
                val recipeId = data["recipe_id"]?.toLongOrNull()
                val cookbookId = data["cookbook_id"]?.toLongOrNull()
                return if (recipeId != null) {
                    Recipe(recipeId, cookbookId, deliveryId)
                } else {
                    ShoppingList(cookbookId, deliveryId)
                }
            }

            return when (data["category"]) {
                "shopping_list" -> ShoppingList(data["cookbook_id"]?.toLongOrNull(), null)
                "meal_plan_activity" -> Home
                else -> null
            }
        }
    }
}

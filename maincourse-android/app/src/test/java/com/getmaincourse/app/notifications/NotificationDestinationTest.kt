package com.getmaincourse.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationDestinationTest {
    @Test
    fun `recipe lifecycle payload opens its cookbook and recipe`() {
        assertEquals(
            NotificationDestination.Recipe(recipeId = 7, cookbookId = 3, deliveryId = 42),
            NotificationDestination.from(
                mapOf(
                    "campaign" to "import_follow_up",
                    "delivery_id" to "42",
                    "recipe_id" to "7",
                    "cookbook_id" to "3",
                ),
            ),
        )
    }

    @Test
    fun `stale list lifecycle payload opens its cookbook shopping list`() {
        assertEquals(
            NotificationDestination.ShoppingList(cookbookId = 3, deliveryId = 43),
            NotificationDestination.from(
                mapOf(
                    "campaign" to "stale_shopping_list",
                    "delivery_id" to "43",
                    "cookbook_id" to "3",
                ),
            ),
        )
    }

    @Test
    fun `collaborator shopping payload opens the shared list`() {
        assertEquals(
            NotificationDestination.ShoppingList(cookbookId = 8, deliveryId = null),
            NotificationDestination.from(mapOf("category" to "shopping_list", "cookbook_id" to "8")),
        )
    }

    @Test
    fun `unknown payload is ignored`() {
        assertNull(NotificationDestination.from(mapOf("unrelated" to "value")))
    }
}

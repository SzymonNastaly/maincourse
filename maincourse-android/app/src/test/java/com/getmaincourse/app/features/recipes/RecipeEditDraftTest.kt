package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEditDraftTest {
    @Test
    fun completeSnapshotTrimsRowsAndExplicitlyClearsNullableValuesAndArrays() {
        val original = values(name = "Soup", ingredients = listOf(row("a", "onion")), instructions = listOf(row("b", "cook")))
        val draft = RecipeEditDraft(
            scope = RecipeScope(7, 1),
            recipeId = 10,
            original = original,
            values = values(
                name = "  New soup  ",
                prepMinutes = "",
                cookMinutes = "  ",
                servings = "",
                ingredients = listOf(row("a", " ")),
                instructions = emptyList(),
                notes = " ",
                sourceUrl = "",
            ),
        )

        assertEquals(
            RecipeDraftValidation.Valid(
                RecipeUpdateRequest("New soup", null, null, null, emptyList(), emptyList(), null, null),
            ),
            draft.validate(),
        )
        assertTrue(draft.hasChanges())
    }

    @Test
    fun validationRejectsBlankNameInvalidNumbersAndUnsafeUrls() {
        assertEquals(RecipeDraftError.NAME_REQUIRED, draft(values(name = " ")).validate().error)
        assertEquals(RecipeDraftError.PREP_MINUTES_INVALID, draft(values(prepMinutes = "-1")).validate().error)
        assertEquals(RecipeDraftError.COOK_MINUTES_INVALID, draft(values(cookMinutes = "1.5")).validate().error)
        assertEquals(RecipeDraftError.SERVINGS_INVALID, draft(values(servings = "0")).validate().error)
        assertEquals(RecipeDraftError.SOURCE_URL_INVALID, draft(values(sourceUrl = "https://user:pass@example.com/a")).validate().error)
        assertEquals(RecipeDraftError.SOURCE_URL_INVALID, draft(values(sourceUrl = "file:///tmp/recipe")).validate().error)
    }

    @Test
    fun unchangedDraftIsNotSaveableButRowIdentityAndOrderDoNotAffectWireValues() {
        val original = values(
            ingredients = listOf(row("first", " onion "), row("second", "")),
            instructions = listOf(row("third", " simmer ")),
        )
        val draft = RecipeEditDraft(RecipeScope(7, 1), 10, original, original)

        assertFalse(draft.hasChanges())
        assertEquals(listOf("onion"), (draft.validate() as RecipeDraftValidation.Valid).request.ingredients)
    }

    private fun draft(values: RecipeEditValues) = RecipeEditDraft(RecipeScope(7, 1), 10, values(), values)

    private fun values(
        name: String = "Soup",
        prepMinutes: String = "10",
        cookMinutes: String = "20",
        servings: String = "4",
        ingredients: List<RecipeEditRow> = listOf(row("ingredient", "1 onion")),
        instructions: List<RecipeEditRow> = listOf(row("instruction", "Simmer")),
        notes: String = "Note",
        sourceUrl: String = "https://example.com/recipe",
    ) = RecipeEditValues(name, prepMinutes, cookMinutes, servings, ingredients, instructions, notes, sourceUrl)

    private fun row(id: String, text: String) = RecipeEditRow(id, text)

    private val RecipeDraftValidation.error: RecipeDraftError
        get() = (this as RecipeDraftValidation.Invalid).error
}

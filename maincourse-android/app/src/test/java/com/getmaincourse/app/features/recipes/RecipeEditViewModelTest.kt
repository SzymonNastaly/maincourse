package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.StructuredIngredient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun populatesRawIngredientRowsAndSendsTrimmedCompleteUpdate() = runTest(dispatcher) {
        var request: RecipeUpdateRequest? = null
        val viewModel = viewModel { update, _ -> request = update }
        runCurrent()

        assertEquals(listOf("2 onions", "salt"), viewModel.state.value.ingredients.map { it.text })
        viewModel.updateName("  Onion broth  ")
        viewModel.updatePrepTime("1a5")
        viewModel.updateNotes("   ")
        viewModel.updateSourceUrl("  https://example.test/soup  ")
        viewModel.updateIngredient(viewModel.state.value.ingredients.first().id, " 3 onions ")

        viewModel.save().join()

        assertEquals("Onion broth", request?.name)
        assertEquals(15, request?.prepTime)
        assertEquals(listOf("3 onions", "salt"), request?.ingredients)
        assertEquals(null, request?.notes)
        assertEquals("https://example.test/soup", request?.sourceUrl)
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun rowsKeepStableIdsWhileAddingRemovingAndReordering() = runTest(dispatcher) {
        val viewModel = viewModel()
        runCurrent()
        val firstId = viewModel.state.value.ingredients.first().id
        val secondId = viewModel.state.value.ingredients.last().id

        viewModel.moveIngredient(secondId, -1)
        viewModel.removeIngredient(firstId)
        viewModel.addIngredient()

        assertEquals(secondId, viewModel.state.value.ingredients.first().id)
        assertEquals(listOf("salt", ""), viewModel.state.value.ingredients.map { it.text })
        assertTrue(viewModel.state.value.dirty)
    }

    @Test
    fun selectedPhotoIsPassedToTheMutationAndSaveFailureCanBeRetried() = runTest(dispatcher) {
        var attempts = 0
        var receivedImage: SharedImage? = null
        val viewModel = viewModel { _, image ->
            attempts++
            if (attempts == 1) throw IOException("offline")
            receivedImage = image
        }
        runCurrent()
        val image = SharedImage(byteArrayOf(1, 2, 3), "image/jpeg")
        viewModel.selectImage(image)

        viewModel.save().join()
        assertEquals("You're offline", viewModel.state.value.error)
        assertFalse(viewModel.state.value.saved)

        viewModel.save().join()
        assertSame(image, receivedImage)
        assertTrue(viewModel.state.value.saved)
        assertEquals(2, attempts)
    }

    private fun viewModel(
        update: suspend (RecipeUpdateRequest, SharedImage?) -> Unit = { _, _ -> },
    ) = RecipeEditViewModel(
        observeRecipe = { MutableStateFlow(DETAIL) },
        updateRecipe = update,
    )

    private companion object {
        val DETAIL = RecipeDetail(
            id = 7,
            name = "Onion soup",
            prepTime = 10,
            cookTime = 20,
            servings = 4,
            favorite = false,
            ingredients = listOf("legacy fallback"),
            structuredIngredients = listOf(
                StructuredIngredient(1, 0, "2", null, null, "onions", null, "2 onions"),
                StructuredIngredient(2, 1, null, null, null, null, null, "salt"),
            ),
            instructions = listOf("Chop", "Cook"),
            notes = "Serve warm",
            sourceUrl = null,
            tags = emptyList(),
            coverImageUrl = null,
            coverImages = null,
            createdAt = "2026-09-01T00:00:00Z",
            updatedAt = "2026-09-10T00:00:00Z",
        )
    }
}

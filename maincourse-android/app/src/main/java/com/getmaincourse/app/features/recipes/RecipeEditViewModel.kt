package com.getmaincourse.app.features.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecipeEditRow(
    val id: Long,
    val text: String,
)

data class RecipeEditUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val servings: String = "",
    val ingredients: List<RecipeEditRow> = emptyList(),
    val instructions: List<RecipeEditRow> = emptyList(),
    val notes: String = "",
    val sourceUrl: String = "",
    val coverImagePath: String? = null,
    val selectedImage: SharedImage? = null,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val nameInvalid: Boolean
        get() = loaded && name.isBlank()

    val canSave: Boolean
        get() = loaded && !nameInvalid && !saving
}

class RecipeEditViewModel internal constructor(
    observeRecipe: () -> Flow<RecipeDetail?>,
    private val updateRecipe: suspend (RecipeUpdateRequest, SharedImage?) -> Unit,
) : ViewModel() {
    constructor(
        userId: Long,
        cookbookId: Long,
        recipeId: Long,
        repository: RecipeRepository,
    ) : this(
        observeRecipe = { repository.observeDetail(userId, cookbookId, recipeId) },
        updateRecipe = { request, image ->
            repository.update(
                userId = userId,
                cookbookId = cookbookId,
                recipeId = recipeId,
                request = request,
                coverImage = image?.bytes,
                coverImageMimeType = image?.mimeType,
            )
        },
    )

    private val mutableState = MutableStateFlow(RecipeEditUiState())
    private var original: DraftContent? = null
    private var nextRowId = 1L
    private var saveJob: Job? = null

    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            observeRecipe().collect { recipe ->
                if (recipe != null && original == null) populate(recipe)
            }
        }
    }

    fun updateName(value: String) = edit { copy(name = value) }

    fun updatePrepTime(value: String) = edit { copy(prepTime = value.onlyDigits()) }

    fun updateCookTime(value: String) = edit { copy(cookTime = value.onlyDigits()) }

    fun updateServings(value: String) = edit { copy(servings = value.onlyDigits()) }

    fun updateNotes(value: String) = edit { copy(notes = value) }

    fun updateSourceUrl(value: String) = edit { copy(sourceUrl = value) }

    fun updateIngredient(id: Long, value: String) = edit {
        copy(ingredients = ingredients.map { row -> if (row.id == id) row.copy(text = value) else row })
    }

    fun addIngredient() = edit {
        copy(ingredients = ingredients + RecipeEditRow(newRowId(), ""))
    }

    fun removeIngredient(id: Long) = edit {
        copy(ingredients = ingredients.removeKeepingOne(id))
    }

    fun moveIngredient(id: Long, offset: Int) = edit {
        copy(ingredients = ingredients.move(id, offset))
    }

    fun updateInstruction(id: Long, value: String) = edit {
        copy(instructions = instructions.map { row -> if (row.id == id) row.copy(text = value) else row })
    }

    fun addInstruction() = edit {
        copy(instructions = instructions + RecipeEditRow(newRowId(), ""))
    }

    fun removeInstruction(id: Long) = edit {
        copy(instructions = instructions.removeKeepingOne(id))
    }

    fun moveInstruction(id: Long, offset: Int) = edit {
        copy(instructions = instructions.move(id, offset))
    }

    fun selectImage(image: SharedImage) = edit { copy(selectedImage = image, error = null) }

    fun reportImageError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    fun save(): Job {
        saveJob?.takeIf(Job::isActive)?.let { return it }
        val current = mutableState.value
        if (!current.canSave) return completedJob()

        mutableState.value = current.copy(saving = true, error = null)
        return viewModelScope.launch {
            try {
                val saving = mutableState.value
                updateRecipe(saving.toRequest(), saving.selectedImage)
                mutableState.value = mutableState.value.copy(saving = false, saved = true, dirty = false)
            } catch (failure: CancellationException) {
                mutableState.value = mutableState.value.copy(saving = false)
                throw failure
            } catch (failure: Throwable) {
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    error = failure.userMessage("Could not save recipe"),
                )
            }
        }.also { saveJob = it }
    }

    private fun populate(recipe: RecipeDetail) {
        val ingredients = (recipe.structuredIngredients
            .sortedBy { it.position }
            .map { it.raw }
            .takeIf { it.isNotEmpty() }
            ?: recipe.ingredients).ifEmpty { listOf("") }
        val instructions = recipe.instructions.ifEmpty { listOf("") }
        val content = DraftContent(
            name = recipe.name,
            prepTime = recipe.prepTime?.toString().orEmpty(),
            cookTime = recipe.cookTime?.toString().orEmpty(),
            servings = recipe.servings?.toString().orEmpty(),
            ingredients = ingredients,
            instructions = instructions,
            notes = recipe.notes.orEmpty(),
            sourceUrl = recipe.sourceUrl.orEmpty(),
        )
        original = content
        mutableState.value = RecipeEditUiState(
            loaded = true,
            name = content.name,
            prepTime = content.prepTime,
            cookTime = content.cookTime,
            servings = content.servings,
            ingredients = content.ingredients.map { RecipeEditRow(newRowId(), it) },
            instructions = content.instructions.map { RecipeEditRow(newRowId(), it) },
            notes = content.notes,
            sourceUrl = content.sourceUrl,
            coverImagePath = recipe.coverImages?.hero ?: recipe.coverImageUrl,
        )
    }

    private fun edit(change: RecipeEditUiState.() -> RecipeEditUiState) {
        val changed = mutableState.value.change()
        mutableState.value = changed.copy(dirty = changed.content() != original || changed.selectedImage != null)
    }

    private fun RecipeEditUiState.toRequest() = RecipeUpdateRequest(
        name = name.trim(),
        prepTime = prepTime.toIntOrNull(),
        cookTime = cookTime.toIntOrNull(),
        servings = servings.toIntOrNull(),
        ingredients = ingredients.map { it.text.trim() }.filter(String::isNotEmpty),
        instructions = instructions.map { it.text.trim() }.filter(String::isNotEmpty),
        notes = notes.trim().ifEmpty { null },
        sourceUrl = sourceUrl.trim().ifEmpty { null },
    )

    private fun RecipeEditUiState.content() = DraftContent(
        name = name,
        prepTime = prepTime,
        cookTime = cookTime,
        servings = servings,
        ingredients = ingredients.map(RecipeEditRow::text),
        instructions = instructions.map(RecipeEditRow::text),
        notes = notes,
        sourceUrl = sourceUrl,
    )

    private fun List<RecipeEditRow>.removeKeepingOne(id: Long): List<RecipeEditRow> {
        val remaining = filterNot { it.id == id }
        return remaining.ifEmpty { listOf(RecipeEditRow(newRowId(), "")) }
    }

    private fun List<RecipeEditRow>.move(id: Long, offset: Int): List<RecipeEditRow> {
        val from = indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in indices) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun newRowId(): Long = nextRowId++

    private fun String.onlyDigits(): String = filter(Char::isDigit)

    private fun completedJob() = Job().apply { complete() }

    private data class DraftContent(
        val name: String,
        val prepTime: String,
        val cookTime: String,
        val servings: String,
        val ingredients: List<String>,
        val instructions: List<String>,
        val notes: String,
        val sourceUrl: String,
    )
}

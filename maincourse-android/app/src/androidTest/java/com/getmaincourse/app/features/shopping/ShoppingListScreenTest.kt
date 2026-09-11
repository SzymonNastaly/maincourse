package com.getmaincourse.app.features.shopping

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.ui.theme.MainCourseTheme
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingListScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainCourseTestActivity>()

    @After
    fun tearDown() {
        compose.runOnIdle { MainCourseTestContent.content = {} }
    }

    @Test
    fun listShowsSectionsDetailsAndItemActions() {
        val toggled = AtomicLong()
        val deleted = AtomicLong()
        val unchecked = item(1, "Tomatoes", "2 cans")
        val checked = item(2, "Bread", checkedAt = "2026-09-11T10:00:00Z")
        show(
            ShoppingListUiState(
                cookbooks = listOf(COOKBOOK),
                selectedCookbookId = COOKBOOK.id,
                items = listOf(unchecked, checked),
                initialLoading = false,
            ),
            onToggle = { toggled.set(it.id) },
            onDelete = { deleted.set(it.id) },
        )

        compose.onNodeWithText("TO BUY").assertIsDisplayed()
        compose.onNodeWithText("Tomatoes").assertIsDisplayed()
        compose.onNodeWithText("2 cans").assertIsDisplayed()
        compose.onNodeWithText("ALREADY GOT").assertIsDisplayed()
        compose.onNodeWithText("Bread").assertIsDisplayed()

        compose.onNodeWithTag("shopping_toggle_1").performClick()
        compose.onNodeWithTag("shopping_delete_2").performClick()

        compose.runOnIdle {
            assertEquals(1L, toggled.get())
            assertEquals(2L, deleted.get())
        }
    }

    @Test
    fun addBarKeepsDraftInStateAndSubmitsIt() {
        val state: MutableState<ShoppingListUiState> = mutableStateOf(
            ShoppingListUiState(
                cookbooks = listOf(COOKBOOK),
                selectedCookbookId = COOKBOOK.id,
                initialLoading = false,
            ),
        )
        var submitted = false
        show(
            state = state,
            onDraftChange = { state.value = state.value.copy(draft = it) },
            onAdd = { submitted = true },
        )

        compose.onNodeWithTag("shopping_add_input").performTextInput("Milk")
        compose.onNodeWithTag("shopping_add_input").assertTextContains("Milk")
        compose.onNodeWithTag("shopping_add").performClick()

        compose.runOnIdle { assertTrue(submitted) }
    }

    @Test
    fun removeAllRequiresConfirmation() {
        var cleared = false
        show(
            ShoppingListUiState(
                cookbooks = listOf(COOKBOOK),
                selectedCookbookId = COOKBOOK.id,
                items = listOf(item(1, "Milk")),
                initialLoading = false,
            ),
            onClear = { cleared = true },
        )

        compose.onNodeWithTag("shopping_clear").performClick()
        compose.onNodeWithText("Remove everything?").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!cleared) }

        compose.onNodeWithTag("shopping_clear_confirm").performClick()
        compose.runOnIdle { assertTrue(cleared) }
    }

    private fun show(
        state: ShoppingListUiState,
        onToggle: (ShoppingItem) -> Unit = {},
        onDelete: (ShoppingItem) -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        val holder = mutableStateOf(state)
        show(holder, onToggle = onToggle, onDelete = onDelete, onClear = onClear)
    }

    private fun show(
        state: MutableState<ShoppingListUiState>,
        onDraftChange: (String) -> Unit = {},
        onAdd: () -> Unit = {},
        onToggle: (ShoppingItem) -> Unit = {},
        onDelete: (ShoppingItem) -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    ShoppingListScreen(
                        state = state.value,
                        onSelectCookbook = {},
                        onRefresh = {},
                        onDraftChange = onDraftChange,
                        onAdd = onAdd,
                        onToggle = onToggle,
                        onDelete = onDelete,
                        onClear = onClear,
                        onClearError = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun item(
        id: Long,
        name: String,
        details: String? = null,
        checkedAt: String? = null,
    ) = ShoppingItem(
        id = id,
        clientId = "client-$id",
        name = name,
        details = details,
        checkedAt = checkedAt,
        sourceRecipeId = null,
        createdAt = "2026-09-11T09:00:00Z",
        updatedAt = "2026-09-11T10:00:00Z",
    )

    private companion object {
        val COOKBOOK = Cookbook(10, "Home", true, 0, emptyList())
    }
}

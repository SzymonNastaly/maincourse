package com.getmaincourse.app.data.cache

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainCourseDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MainCourseDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun migrationFromOneCreatesScopedShoppingCache() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                "INSERT INTO cookbooks (userId, cookbookId, listPosition, cookbookJson) " +
                    "VALUES (1, 10, 0, '{}')",
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 2, true, MainCourseDatabase.MIGRATION_1_2).use { db ->
            db.query("PRAGMA table_info(shopping_list_items)").use { cursor ->
                val names = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertEquals(
                    listOf("userId", "cookbookId", "itemId", "checkedAt", "createdAt", "itemJson"),
                    names,
                )
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrationFromTwoReplacesLegacyFetchMarkerAndPreservesCachedData() {
        helper.createDatabase(DETAIL_DATABASE_NAME, 2).apply {
            execSQL(
                "INSERT INTO cookbooks (userId, cookbookId, listPosition, cookbookJson) " +
                    "VALUES (1, 10, 0, '{}')",
            )
            execSQL("INSERT INTO selected_cookbooks (userId, cookbookId) VALUES (1, 10)")
            execSQL(
                "INSERT INTO recipes " +
                    "(userId, cookbookId, recipeId, listPosition, summaryJson, detailJson) " +
                    "VALUES (1, 10, 7, 0, '{}', '{\"id\":7}')",
            )
            execSQL("INSERT INTO recipe_fetches (userId, cookbookId) VALUES (1, 10)")
            execSQL(
                "INSERT INTO shopping_list_items " +
                    "(userId, cookbookId, itemId, checkedAt, createdAt, itemJson) " +
                    "VALUES (1, 10, 3, NULL, '2026-09-11T00:00:00Z', '{}')",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DETAIL_DATABASE_NAME,
            3,
            true,
            MainCourseDatabase.MIGRATION_2_3,
        ).use { db ->
            db.query("SELECT detailJson FROM recipes WHERE recipeId = 7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("{\"id\":7}", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM shopping_list_items").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM selected_cookbooks").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'recipe_fetches'",
            ).use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
            db.query("PRAGMA table_info(recipe_detail_syncs)").use { cursor ->
                val names = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertEquals(listOf("userId", "cookbookId", "cursor"), names)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrationFromThreeAddsMinimumSdkCompatibleSearchIndexAndPreservesRecipes() {
        helper.createDatabase(SEARCH_DATABASE_NAME, 3).apply {
            execSQL(
                "INSERT INTO cookbooks (userId, cookbookId, listPosition, cookbookJson) " +
                    "VALUES (1, 10, 0, '{}')",
            )
            execSQL(
                "INSERT INTO recipes " +
                    "(userId, cookbookId, recipeId, listPosition, summaryJson, detailJson) " +
                    "VALUES (1, 10, 7, 0, '{\"id\":7}', '{\"ingredients\":[\"salt\"]}')",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            SEARCH_DATABASE_NAME,
            5,
            true,
            MainCourseDatabase.MIGRATION_3_5,
        ).use { db ->
            db.query("SELECT detailJson FROM recipes WHERE recipeId = 7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("{\"ingredients\":[\"salt\"]}", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM recipe_search_documents").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' AND name = 'recipe_search_documents_fts'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    @Throws(IOException::class)
    fun migrationFromFourDropsTheIncompatibleDerivedSearchProjection() {
        helper.createDatabase(SEARCH_VERSION_FOUR_DATABASE_NAME, 4).apply {
            execSQL(
                "INSERT INTO cookbooks (userId, cookbookId, listPosition, cookbookJson) " +
                    "VALUES (1, 10, 0, '{}')",
            )
            execSQL(
                "INSERT INTO recipes " +
                    "(userId, cookbookId, recipeId, listPosition, summaryJson, detailJson) " +
                    "VALUES (1, 10, 7, 0, '{\"id\":7}', '{\"ingredients\":[\"salt\"]}')",
            )
            execSQL(
                "INSERT INTO recipe_search_documents " +
                    "(userId, cookbookId, recipeId, name, ingredients, instructions, updatedAt) " +
                    "VALUES (1, 10, 7, 'Café', 'salt', '', '2026-09-21T00:00:00Z')",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            SEARCH_VERSION_FOUR_DATABASE_NAME,
            5,
            true,
            MainCourseDatabase.MIGRATION_4_5,
        ).use { db ->
            db.query("SELECT COUNT(*) FROM recipes").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM recipe_search_documents").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "shopping-migration-test"
        const val DETAIL_DATABASE_NAME = "detail-sync-migration-test"
        const val SEARCH_DATABASE_NAME = "search-migration-test"
        const val SEARCH_VERSION_FOUR_DATABASE_NAME = "search-v4-migration-test"
    }
}

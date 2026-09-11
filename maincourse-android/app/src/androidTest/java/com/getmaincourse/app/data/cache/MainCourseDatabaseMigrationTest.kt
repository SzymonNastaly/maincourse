package com.getmaincourse.app.data.cache

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
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

    private companion object {
        const val DATABASE_NAME = "shopping-migration-test"
    }
}

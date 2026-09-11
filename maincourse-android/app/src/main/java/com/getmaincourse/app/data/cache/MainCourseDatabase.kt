package com.getmaincourse.app.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CookbookEntity::class,
        SelectedCookbookEntity::class,
        RecipeDetailSyncEntity::class,
        RecipeEntity::class,
        RecipeSearchDocumentEntity::class,
        RecipeSearchFtsEntity::class,
        ShoppingItemEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MainCourseDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        fun open(context: Context, name: String = "maincourse.db"): MainCourseDatabase =
            Room.databaseBuilder(context.applicationContext, MainCourseDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shopping_list_items` (
                        `userId` INTEGER NOT NULL,
                        `cookbookId` INTEGER NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `checkedAt` TEXT,
                        `createdAt` TEXT NOT NULL,
                        `itemJson` TEXT NOT NULL,
                        PRIMARY KEY(`userId`, `cookbookId`, `itemId`),
                        FOREIGN KEY(`userId`, `cookbookId`)
                            REFERENCES `cookbooks`(`userId`, `cookbookId`)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shopping_list_items_userId_cookbookId` " +
                        "ON `shopping_list_items` (`userId`, `cookbookId`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `recipe_fetches`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipe_detail_syncs` (
                        `userId` INTEGER NOT NULL,
                        `cookbookId` INTEGER NOT NULL,
                        `cursor` TEXT NOT NULL,
                        PRIMARY KEY(`userId`, `cookbookId`),
                        FOREIGN KEY(`userId`, `cookbookId`)
                            REFERENCES `cookbooks`(`userId`, `cookbookId`)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipe_search_documents` (
                        `rowid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `cookbookId` INTEGER NOT NULL,
                        `recipeId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `ingredients` TEXT NOT NULL,
                        `instructions` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        FOREIGN KEY(`userId`, `cookbookId`, `recipeId`)
                            REFERENCES `recipes`(`userId`, `cookbookId`, `recipeId`)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_recipe_search_documents_userId_cookbookId_recipeId` " +
                        "ON `recipe_search_documents` (`userId`, `cookbookId`, `recipeId`)",
                )
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `recipe_search_documents_fts`
                    USING FTS4(
                        `name` TEXT NOT NULL,
                        `ingredients` TEXT NOT NULL,
                        `instructions` TEXT NOT NULL,
                        content=`recipe_search_documents`,
                        tokenize=unicode61 `remove_diacritics=2`,
                        prefix=`2,3,4`
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}

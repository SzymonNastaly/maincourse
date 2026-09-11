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
        RecipeFetchEntity::class,
        RecipeEntity::class,
        ShoppingItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MainCourseDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        fun open(context: Context, name: String = "maincourse.db"): MainCourseDatabase =
            Room.databaseBuilder(context.applicationContext, MainCourseDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
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
    }
}

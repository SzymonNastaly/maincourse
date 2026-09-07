package com.getmaincourse.app.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CookbookEntity::class,
        SelectedCookbookEntity::class,
        RecipeFetchEntity::class,
        RecipeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MainCourseDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        fun open(context: Context, name: String = "maincourse.db"): MainCourseDatabase =
            Room.databaseBuilder(context.applicationContext, MainCourseDatabase::class.java, name)
                .build()
    }
}

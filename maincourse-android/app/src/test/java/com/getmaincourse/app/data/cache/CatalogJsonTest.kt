package com.getmaincourse.app.data.cache

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogJsonTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun cookbooksCachedBeforeTheDefaultNameMarkerStillDecode() {
        val entity = CookbookEntity(1, 42, 0, """{"id":42,"name":"Family","personal":false,"recipe_count":0,"members":[]}""")

        val cookbook = entity.toCookbook(json)!!

        assertEquals("Family", cookbook.name)
        assertFalse(cookbook.defaultName)
    }

    @Test
    fun defaultNameMarkerSurvivesTheCache() {
        val entity = CookbookEntity(1, 42, 0, """{"id":42,"name":"My Recipes","personal":true,"recipe_count":0,"members":[],"default_name":true}""")

        val cached = entity.toCookbook(json)!!.toEntity(1, 0, json).toCookbook(json)!!

        assertTrue(cached.defaultName)
    }
}

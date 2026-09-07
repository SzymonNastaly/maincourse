package com.getmaincourse.app.data.images

import com.getmaincourse.app.data.model.CoverImages
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlResolverTest {
    @Test
    fun relativeImagesResolveFromTheApiOriginRatherThanApiV1() {
        assertEquals(
            "https://app.example.test/rails/active_storage/card.jpg",
            resolveImageUrl(
                "https://app.example.test/api/v1/",
                "/rails/active_storage/card.jpg",
            ),
        )
    }

    @Test
    fun absoluteImagesRemainAbsoluteAndMissingImagesRemainMissing() {
        assertEquals(
            "https://cdn.example.test/card.jpg",
            resolveImageUrl("https://app.example.test/", "https://cdn.example.test/card.jpg"),
        )
        assertNull(resolveImageUrl("https://app.example.test/", "  "))
        assertNull(resolveImageUrl("https://app.example.test/", null))
    }

    @Test
    fun cardAndHeroFallbacksUseTheApprovedOrder() {
        val covers = CoverImages(thumb = "/thumb", card = null, hero = "/hero")
        assertEquals("/legacy", summary(covers, "/legacy").cardImagePath())
        assertEquals("/hero", summary(covers, null).cardImagePath())
        assertEquals("/hero", detail(covers, "/legacy").heroImagePath())
        assertEquals("/legacy", detail(CoverImages("/thumb", "/card", null), "/legacy").heroImagePath())
    }

    private fun summary(covers: CoverImages, legacy: String?) = RecipeSummary(
        1L, "Soup", null, null, false, legacy, covers, "completed", null, "now",
    )

    private fun detail(covers: CoverImages, legacy: String?) = RecipeDetail(
        1L, "Soup", null, null, null, false, emptyList(), emptyList(), emptyList(), null,
        null, emptyList(), legacy, covers, "then", "now",
    )
}

package com.getmaincourse.app.features.search

import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary

data class RecipeSearchDocument(
    val summary: RecipeSummary,
    val detail: RecipeDetail?,
)

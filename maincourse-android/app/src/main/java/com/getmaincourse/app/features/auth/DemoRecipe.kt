package com.getmaincourse.app.features.auth

import android.content.res.AssetManager
import com.getmaincourse.app.data.model.StructuredIngredient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DemoRecipe(
    val key: String,
    val name: String,
    val creator: String,
    val caption: String,
    val servings: Int,
    @SerialName("prep_time") val prepTime: Int,
    @SerialName("cook_time") val cookTime: Int,
    @SerialName("image_name") val imageName: String,
    val ingredients: List<DemoIngredient>,
    val instructions: List<String>,
) {
    companion object {
        fun load(assets: AssetManager): DemoRecipe = assets.open("tomato-orzo-v1.json").bufferedReader().use {
            Json.decodeFromString(it.readText())
        }
    }
}

@Serializable
data class DemoIngredient(val raw: String, val amount: Double?, val unit: String?, val name: String, val note: String?) {
    fun structured() = StructuredIngredient(
        id = 0, position = 0, amount = amount?.toString(), amountMax = null,
        unit = unit, name = name, note = note, raw = raw,
    )
}

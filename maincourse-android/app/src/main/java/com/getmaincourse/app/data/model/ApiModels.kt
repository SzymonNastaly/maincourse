package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val name: String?,
    val email: String,
    @SerialName("lifecycle_notifications_enabled")
    val lifecycleNotificationsEnabled: Boolean,
)

@Serializable
data class SessionResponse(
    val token: String,
    @SerialName("expires_at")
    val expiresAt: String,
    val user: User,
)

@Serializable
data class Cookbook(
    val id: Long,
    val name: String,
    val personal: Boolean,
    @SerialName("recipe_count")
    val recipeCount: Int,
    val members: List<CookbookMember>,
)

@Serializable
data class CookbookMember(
    val id: Long,
    val email: String,
    val role: String,
)

@Serializable
data class RecipeSummary(
    val id: Long,
    val name: String,
    @SerialName("prep_time")
    val prepTime: Int?,
    @SerialName("cook_time")
    val cookTime: Int?,
    val favorite: Boolean,
    @SerialName("cover_image_url")
    val coverImageUrl: String?,
    @SerialName("cover_images")
    val coverImages: CoverImages?,
    @SerialName("import_status")
    val importStatus: String,
    @SerialName("error_message")
    val errorMessage: String?,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class RecipeDetail(
    val id: Long,
    val name: String,
    @SerialName("prep_time")
    val prepTime: Int?,
    @SerialName("cook_time")
    val cookTime: Int?,
    val servings: Int?,
    val favorite: Boolean,
    val ingredients: List<String>,
    @SerialName("structured_ingredients")
    val structuredIngredients: List<StructuredIngredient>,
    val instructions: List<String>,
    val notes: String?,
    @SerialName("source_url")
    val sourceUrl: String?,
    val tags: List<RecipeTag>,
    @SerialName("cover_image_url")
    val coverImageUrl: String?,
    @SerialName("cover_images")
    val coverImages: CoverImages?,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class RecipeDetailBatchResponse(
    val recipes: List<RecipeDetail>,
    @SerialName("next_cursor")
    val nextCursor: String?,
)

@Serializable
data class StructuredIngredient(
    val id: Long,
    val position: Int,
    val amount: String?,
    @SerialName("amount_max")
    val amountMax: String?,
    val unit: String?,
    val name: String?,
    val note: String?,
    val raw: String,
)

@Serializable
data class RecipeTag(
    val id: Long,
    val name: String,
)

@Serializable
data class CoverImages(
    val thumb: String?,
    val card: String?,
    val hero: String?,
)

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
    @SerialName("device_name")
    val deviceName: String,
)

@Serializable
data class SignUpRequest(
    val name: String?,
    val email: String,
    val password: String,
    @SerialName("password_confirmation")
    val passwordConfirmation: String,
    @SerialName("device_name")
    val deviceName: String,
)

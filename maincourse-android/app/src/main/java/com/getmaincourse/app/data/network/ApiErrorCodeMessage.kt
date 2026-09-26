package com.getmaincourse.app.data.network

import com.getmaincourse.app.R

internal fun ApiErrorCode.message(strings: ApiStrings, count: Long?): String = when (this) {
    ApiErrorCode.ACCOUNT_LINK_REQUIRED -> strings.text(R.string.api_error_account_link_required)
    ApiErrorCode.ALREADY_COOKBOOK_MEMBER -> strings.text(R.string.api_error_already_cookbook_member)
    ApiErrorCode.APP_UPDATE_REQUIRED -> strings.text(R.string.api_error_app_update_required)
    ApiErrorCode.APPLE_ACCOUNT_CREATION_CONFIRMATION_REQUIRED -> strings.text(R.string.api_error_apple_account_creation_confirmation_required)
    ApiErrorCode.CONTENT_TOO_LARGE -> strings.text(R.string.api_error_content_too_large)
    ApiErrorCode.COOKBOOK_UNAVAILABLE -> strings.text(R.string.api_error_cookbook_unavailable)
    ApiErrorCode.DELETE_FAILED -> strings.text(R.string.api_error_delete_failed)
    ApiErrorCode.FORBIDDEN -> strings.text(R.string.api_error_forbidden)
    ApiErrorCode.IMAGE_REQUIRED -> strings.text(R.string.api_error_image_required)
    ApiErrorCode.IMAGE_TOO_LARGE -> strings.text(R.string.api_error_image_too_large)
    ApiErrorCode.IMPORT_LIMIT_REACHED -> if (count != null && count > 0) {
        strings.text(R.string.api_error_import_limit_reached_count, count)
    } else {
        strings.text(R.string.api_error_import_limit_reached)
    }
    ApiErrorCode.INVALID_CREDENTIALS -> strings.text(R.string.api_error_invalid_credentials)
    ApiErrorCode.INVALID_IMAGE -> strings.text(R.string.api_error_invalid_image)
    ApiErrorCode.INVALID_IMPORT_URL -> strings.text(R.string.api_error_invalid_import_url)
    ApiErrorCode.INVALID_RECIPE_SAVE -> strings.text(R.string.api_error_invalid_recipe_save)
    ApiErrorCode.INVALID_REQUEST -> strings.text(R.string.api_error_invalid_request)
    ApiErrorCode.INVALID_SHOPPING_ITEMS -> strings.text(R.string.api_error_invalid_shopping_items)
    ApiErrorCode.INVITATION_UNAVAILABLE -> strings.text(R.string.api_error_invitation_unavailable)
    ApiErrorCode.MEAL_PLAN_FINALIZED -> strings.text(R.string.api_error_meal_plan_finalized)
    ApiErrorCode.MEAL_PLAN_SELECTION_CONFLICT -> strings.text(R.string.api_error_meal_plan_selection_conflict)
    ApiErrorCode.NAME_REQUIRED -> strings.text(R.string.api_error_name_required)
    ApiErrorCode.NOT_FOUND -> strings.text(R.string.api_error_not_found)
    ApiErrorCode.OAUTH_FAILED -> strings.text(R.string.api_error_oauth_failed)
    ApiErrorCode.OAUTH_UNAVAILABLE -> strings.text(R.string.api_error_oauth_unavailable)
    ApiErrorCode.OWNER_CANNOT_LEAVE -> strings.text(R.string.api_error_owner_cannot_leave)
    ApiErrorCode.OWNER_REQUIRED -> strings.text(R.string.api_error_owner_required)
    ApiErrorCode.PERSONAL_COOKBOOK_INVITATION -> strings.text(R.string.api_error_personal_cookbook_invitation)
    ApiErrorCode.PERSONAL_COOKBOOK_REQUIRED -> strings.text(R.string.api_error_personal_cookbook_required)
    ApiErrorCode.RATE_LIMITED -> strings.text(R.string.api_error_rate_limited)
    ApiErrorCode.RECIPE_SAVE_CONFLICT -> strings.text(R.string.api_error_recipe_save_conflict)
    ApiErrorCode.RECIPE_SAVE_GONE -> strings.text(R.string.api_error_recipe_save_gone)
    ApiErrorCode.SHARED_COOKBOOK_EXISTS -> strings.text(R.string.api_error_shared_cookbook_exists)
    ApiErrorCode.TEXT_REQUIRED -> strings.text(R.string.api_error_text_required)
    ApiErrorCode.TEXT_TOO_LONG -> if (count != null && count > 0) {
        strings.text(R.string.api_error_text_too_long_count, count)
    } else {
        strings.text(R.string.api_error_text_too_long)
    }
    ApiErrorCode.UNAUTHORIZED -> strings.text(R.string.api_error_unauthorized)
    ApiErrorCode.UPDATE_FAILED -> strings.text(R.string.api_error_update_failed)
    ApiErrorCode.URL_REQUIRED -> strings.text(R.string.api_error_url_required)
    ApiErrorCode.VALIDATION_FAILED -> strings.text(R.string.api_error_invalid_request)
}

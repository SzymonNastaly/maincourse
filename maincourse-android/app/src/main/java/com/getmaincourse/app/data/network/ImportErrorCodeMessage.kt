package com.getmaincourse.app.data.network

import com.getmaincourse.app.R

internal fun ImportErrorCode.message(strings: ApiStrings): String = when (this) {
    ImportErrorCode.IMPORT_FAILED -> strings.text(R.string.api_import_failed)
    ImportErrorCode.NO_RECIPE_IN_PHOTO -> strings.text(R.string.api_import_no_recipe_in_photo)
}

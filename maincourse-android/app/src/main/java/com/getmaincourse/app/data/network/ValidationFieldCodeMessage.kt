package com.getmaincourse.app.data.network

import com.getmaincourse.app.R

internal fun ValidationFieldCode.message(strings: ApiStrings): String = when (this) {
    ValidationFieldCode.BASE -> strings.text(R.string.api_field_base)
    ValidationFieldCode.EMAIL_ADDRESS -> strings.text(R.string.api_field_email_address)
    ValidationFieldCode.PASSWORD -> strings.text(R.string.api_field_password)
    ValidationFieldCode.PASSWORD_CHALLENGE -> strings.text(R.string.api_field_password)
    ValidationFieldCode.PASSWORD_CONFIRMATION -> strings.text(R.string.api_field_password_confirmation)
    ValidationFieldCode.NAME -> strings.text(R.string.api_field_name)
    ValidationFieldCode.SERVINGS -> strings.text(R.string.api_field_servings)
    ValidationFieldCode.PREP_TIME -> strings.text(R.string.api_field_prep_time)
    ValidationFieldCode.COOK_TIME -> strings.text(R.string.api_field_cook_time)
    ValidationFieldCode.SOURCE_URL -> strings.text(R.string.api_field_source_url)
    ValidationFieldCode.COVER_IMAGE -> strings.text(R.string.api_field_image)
    ValidationFieldCode.IMPORT_IMAGE -> strings.text(R.string.api_field_image)
}

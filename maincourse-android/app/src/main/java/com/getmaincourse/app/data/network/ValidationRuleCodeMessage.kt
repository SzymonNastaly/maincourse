package com.getmaincourse.app.data.network

import com.getmaincourse.app.R

internal fun ValidationRuleCode.message(strings: ApiStrings, field: String, count: Long?): String = when (this) {
    ValidationRuleCode.BLANK -> strings.text(R.string.api_rule_blank, field)
    ValidationRuleCode.TAKEN -> strings.text(R.string.api_rule_taken, field)
    ValidationRuleCode.INVALID -> strings.text(R.string.api_rule_invalid, field)
    ValidationRuleCode.TOO_LONG -> if (count != null && count >= 0) {
        strings.text(R.string.api_rule_too_long_count, field, count)
    } else {
        strings.text(R.string.api_rule_too_long, field)
    }
    ValidationRuleCode.TOO_SHORT -> if (count != null && count >= 0) {
        strings.text(R.string.api_rule_too_short_count, field, count)
    } else {
        strings.text(R.string.api_rule_too_short, field)
    }
    ValidationRuleCode.PASSWORD_TOO_LONG -> strings.text(R.string.api_rule_password_too_long)
    ValidationRuleCode.CONFIRMATION -> strings.text(R.string.api_rule_confirmation)
}

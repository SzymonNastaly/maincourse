import Foundation

extension ValidationRuleCode {
    func message(field: String, count: Int?, bundle: Bundle, locale: Locale) -> String {
        switch self {
        case .blank:
            return String(
                format: String(localized: "%@ is required.", bundle: bundle, locale: locale),
                locale: locale,
                field
            )
        case .taken:
            return String(
                format: String(localized: "%@ is already in use.", bundle: bundle, locale: locale),
                locale: locale,
                field
            )
        case .too_long:
            if let count, count >= 0 {
                return String(
                    format: String(
                        localized: "The maximum character count for %@ is %lld.",
                        bundle: bundle,
                        locale: locale
                    ),
                    locale: locale,
                    field,
                    count
                )
            }
            return String(
                format: String(localized: "%@ is too long.", bundle: bundle, locale: locale),
                locale: locale,
                field
            )
        case .too_short:
            if let count, count >= 0 {
                return String(
                    format: String(
                        localized: "The minimum character count for %@ is %lld.",
                        bundle: bundle,
                        locale: locale
                    ),
                    locale: locale,
                    field,
                    count
                )
            }
            return String(
                format: String(localized: "%@ is too short.", bundle: bundle, locale: locale),
                locale: locale,
                field
            )
        case .password_too_long:
            return String(
                localized: "This password is too long. Use a shorter password.",
                bundle: bundle,
                locale: locale
            )
        case .confirmation:
            return String(localized: "Password confirmation does not match.", bundle: bundle, locale: locale)
        case .invalid:
            return String(
                format: String(
                    localized: "%@ is invalid. Please check it and try again.",
                    bundle: bundle,
                    locale: locale
                ),
                locale: locale,
                field
            )
        }
    }
}

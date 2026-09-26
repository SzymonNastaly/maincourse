import Foundation

extension ValidationFieldCode {
    func message(bundle: Bundle, locale: Locale) -> String {
        switch self {
        case .base: String(localized: "This value", bundle: bundle, locale: locale)
        case .email_address: String(localized: "Email", bundle: bundle, locale: locale)
        case .password, .password_challenge: String(localized: "Password", bundle: bundle, locale: locale)
        case .password_confirmation: String(localized: "Password confirmation", bundle: bundle, locale: locale)
        case .name: String(localized: "Name", bundle: bundle, locale: locale)
        case .servings: String(localized: "Servings", bundle: bundle, locale: locale)
        case .prep_time: String(localized: "Preparation time", bundle: bundle, locale: locale)
        case .cook_time: String(localized: "Cooking time", bundle: bundle, locale: locale)
        case .source_url: String(localized: "Recipe URL", bundle: bundle, locale: locale)
        case .cover_image, .import_image: String(localized: "Recipe photo", bundle: bundle, locale: locale)
        }
    }
}

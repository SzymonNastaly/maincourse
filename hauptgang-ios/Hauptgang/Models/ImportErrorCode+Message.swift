import Foundation

extension ImportErrorCode {
    func message(bundle: Bundle = .main, locale: Locale = .autoupdatingCurrent) -> String {
        switch self {
        case .import_failed:
            String(
                localized: "Could not import this recipe. Try another source or photo.",
                bundle: bundle,
                locale: locale
            )
        case .no_recipe_in_photo:
            String(localized: "No recipe found in that photo.", bundle: bundle, locale: locale)
        }
    }
}

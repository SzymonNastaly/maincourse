import Foundation

enum RecipeDisplayFormatter {
    static func recipeCount(_ count: Int, locale: Locale = .autoupdatingCurrent) -> String {
        String(
            localized: "\(count) recipes",
            locale: locale,
            comment: "Number of recipes in a cookbook. Uses plural variations, including one recipe."
        )
    }

    static func minutes(_ minutes: Int, locale: Locale = .autoupdatingCurrent) -> String {
        Duration.seconds(Double(minutes) * 60).formatted(
            .units(allowed: [.minutes], width: .narrow).locale(locale)
        )
    }
}

import Foundation

/// Pure formatter helpers for `StructuredIngredient` quantities.
///
/// Mirrors the web-side `format_quantity` helper so both clients render
/// identical strings. Uses Decimal arithmetic to avoid Double precision drift
/// when scaling quantities (e.g. `1/3 tsp × 2`).
enum IngredientFormatter {
    /// "200 g", "200–250 g", "½ tsp", "pinch", "" when both nil.
    static func formatQuantity(
        amount: Decimal?,
        amountMax: Decimal?,
        unit: String?,
        scale: Decimal = 1
    ) -> String {
        let unit = (unit?.isEmpty == false) ? unit : nil

        let quantity: String? = switch (amount, amountMax) {
        case let (min?, max?):
            "\(self.formatAmount(min * scale))\u{2013}\(self.formatAmount(max * scale))"
        case let (min?, nil):
            self.formatAmount(min * scale)
        default:
            nil
        }

        return [quantity, unit].compactMap { $0 }.joined(separator: " ")
    }

    /// Format a single amount: prefer common unicode fractions, otherwise
    /// round to 2 decimals and strip trailing zeros.
    static func formatAmount(_ value: Decimal) -> String {
        if let glyph = unicodeFraction(value) {
            return glyph
        }

        var rounded = Decimal()
        var input = value
        NSDecimalRound(&rounded, &input, 2, .plain)

        let formatter = Self.decimalFormatter
        return formatter.string(from: rounded as NSNumber) ?? "\(rounded)"
    }

    /// Returns a unicode fraction glyph if `value` is approximately one of
    /// the common cooking fractions; nil otherwise.
    static func unicodeFraction(_ value: Decimal) -> String? {
        for entry in self.fractionTable {
            var diff = value - entry.value
            if diff < 0 {
                diff = -diff
            }
            if diff < Self.fractionTolerance {
                return entry.glyph
            }
        }
        return nil
    }

    private static let fractionTolerance = Decimal(string: "0.005")!

    private static let fractionTable: [(value: Decimal, glyph: String)] = [
        (Decimal(string: "0.25")!, "\u{00BC}"), // ¼
        (Decimal(string: "0.5")!, "\u{00BD}"), // ½
        (Decimal(string: "0.75")!, "\u{00BE}"), // ¾
        (Decimal(string: "0.3333")!, "\u{2153}"), // ⅓
        (Decimal(string: "0.6667")!, "\u{2154}"), // ⅔
        (Decimal(string: "0.125")!, "\u{215B}"), // ⅛
        (Decimal(string: "0.375")!, "\u{215C}"), // ⅜
        (Decimal(string: "0.625")!, "\u{215D}"), // ⅝
        (Decimal(string: "0.875")!, "\u{215E}") // ⅞
    ]

    private static let decimalFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
        formatter.decimalSeparator = "."
        return formatter
    }()
}

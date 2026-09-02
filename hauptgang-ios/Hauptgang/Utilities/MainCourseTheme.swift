import SwiftUI

// MARK: - Colour tokens

/// MainCourse design tokens. Hex literals mirror the `@theme` block of
/// `app/assets/tailwind/application.css` in the web app; this file is the iOS
/// source of truth and is compiled into both the app and the share extension.
extension Color {
    // Surfaces
    static let mcCanvas = Color(mcHex: 0xEEF0F2)
    static let mcSurface = Color(mcHex: 0xFFFFFF)
    static let mcSunken = Color(mcHex: 0xF5F7F8)

    // Text
    static let mcInk = Color(mcHex: 0x14171C)
    static let mcBody = Color(mcHex: 0x5B6570)
    static let mcMuted = Color(mcHex: 0x9AA3AE)

    // Lines
    static let mcLine = Color(mcHex: 0xE3E6EA)
    static let mcHairline = Color(mcHex: 0xDCE0E6)

    // Accent — the only action colour
    static let mcAccent = Color(mcHex: 0x16624B)
    static let mcAccentDark = Color(mcHex: 0x0F4736)
    static let mcAccentTint = Color(mcHex: 0xF1F7F4)
    static let mcAccentLine = Color(mcHex: 0xD6E7DF)

    // Signal
    static let mcLime = Color(mcHex: 0xCDEB7A)
    static let mcAmber = Color(mcHex: 0xB07D12)
    static let mcAmberTint = Color(mcHex: 0xFBF3E0)
    static let mcDanger = Color(mcHex: 0xB42318)
    static let mcDangerTint = Color(mcHex: 0xFDF3F2)
    static let mcDangerLine = Color(mcHex: 0xEFD5D3)

    /// Builds an opaque sRGB colour from a `0xRRGGBB` literal.
    init(mcHex hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

// MARK: - Type

extension Font {
    /// IBM Plex Mono, scaled with Dynamic Type relative to `style`.
    /// Use for numerics only: times, servings, counts, quantities, step numbers.
    /// Weights at or above `.medium` use the 500 face; everything else uses 400.
    static func mcMono(_ style: Font.TextStyle = .body, weight: Font.Weight = .regular) -> Font {
        let heavyWeights: Set<Font.Weight> = [.medium, .semibold, .bold, .heavy, .black]
        let name = heavyWeights.contains(weight) ? "IBMPlexMono-Medium" : "IBMPlexMono-Regular"
        return .custom(name, size: Self.mcBaseSize(for: style), relativeTo: style)
    }

    /// Default (Large) point sizes of the iOS text styles, used as the base for scaling.
    private static let mcBaseSizes: [Font.TextStyle: CGFloat] = [
        .largeTitle: 34, .title: 28, .title2: 22, .title3: 20,
        .headline: 17, .body: 17, .callout: 16, .subheadline: 15,
        .footnote: 13, .caption: 12, .caption2: 11
    ]

    private static func mcBaseSize(for style: Font.TextStyle) -> CGFloat {
        self.mcBaseSizes[style] ?? 17
    }
}

// MARK: - Layout

enum Theme {
    enum Spacing {
        static let xs: CGFloat = 4
        static let sm: CGFloat = 8
        static let md: CGFloat = 16
        static let lg: CGFloat = 24
        static let xl: CGFloat = 32
        static let xxl: CGFloat = 48
    }

    /// Three radii only: controls (buttons, chips), cards (recipe cards, rows,
    /// tiles, text fields) and panels (grouped surfaces).
    enum Radius {
        static let control: CGFloat = 8
        static let card: CGFloat = 10
        static let panel: CGFloat = 12
    }
}

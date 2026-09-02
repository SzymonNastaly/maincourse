import SwiftUI

extension Color {
    // MARK: - Brand Colors

    /// Primary brand color - adaptive for light/dark mode
    /// Light: #8B5E34 (warm brown), Dark: #B45309 (amber-700)
    static let hauptgangPrimary = Color("HauptgangPrimary")

    /// Primary hover/pressed state - adaptive for light/dark mode
    /// Light: #7A5230, Dark: #92400E (amber-800)
    static let hauptgangPrimaryHover = Color("HauptgangPrimaryHover")

    // MARK: - Backgrounds

    /// Off-white background - #FDFBF7
    static let hauptgangBackground = Color("HauptgangBackground")

    /// Card background - #FFFFFF
    static let hauptgangCard = Color.white

    /// Raised surface background - #F5F2EA (for cards without images, sidebars)
    static let hauptgangSurfaceRaised = Color(red: 245 / 255, green: 242 / 255, blue: 234 / 255)

    /// Subtle border - #E5E0D5
    static let hauptgangBorderSubtle = Color(red: 229 / 255, green: 224 / 255, blue: 213 / 255)

    // MARK: - Text Colors

    /// Primary text - #1F1F1F
    static let hauptgangTextPrimary = Color(red: 31 / 255, green: 31 / 255, blue: 31 / 255)

    /// Secondary text - #6B6B6B
    static let hauptgangTextSecondary = Color(red: 107 / 255, green: 107 / 255, blue: 107 / 255)

    /// Muted text - #9CA3AF
    static let hauptgangTextMuted = Color(red: 156 / 255, green: 163 / 255, blue: 175 / 255)

    // MARK: - Semantic Colors

    /// Error red - #DC2626
    static let hauptgangError = Color(red: 220 / 255, green: 38 / 255, blue: 38 / 255)

    /// Soft error red - #EF4444 (for banners/toasts)
    static let hauptgangErrorSoft = Color(red: 239 / 255, green: 68 / 255, blue: 68 / 255)

    /// Success green - #16A34A
    static let hauptgangSuccess = Color(red: 22 / 255, green: 163 / 255, blue: 74 / 255)

    /// Amber for favorites on dark backgrounds - #FBB424 (Tailwind amber-400)
    static let hauptgangAmber = Color(red: 251 / 255, green: 191 / 255, blue: 36 / 255)
}

// MARK: - Design Tokens

extension Theme {
    enum CornerRadius {
        static let sm: CGFloat = 4
        static let md: CGFloat = 8
        static let lg: CGFloat = 12
        static let xl: CGFloat = 16
    }

    enum Shadow {
        static let sm = ShadowStyle(color: .black.opacity(0.05), radius: 2, offsetY: 1)
        static let md = ShadowStyle(color: .black.opacity(0.1), radius: 4, offsetY: 2)
    }

    struct ShadowStyle {
        let color: Color
        let radius: CGFloat
        let offsetY: CGFloat
    }
}

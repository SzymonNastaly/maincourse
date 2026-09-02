import CryptoKit
import Foundation
import SwiftUI

/// Deterministic placeholder gradient for recipes without a photo. Mirrors
/// `RecipesHelper::PLACEHOLDER_GRADIENTS` and `placeholder_gradient_for` in the
/// web app so the same recipe gets the same colours on every platform.
enum RecipePlaceholderGradient {
    static let stops: [(start: Color, end: Color)] = [
        (Color(mcHex: 0xC9B08F), Color(mcHex: 0xA2794F)),
        (Color(mcHex: 0xBFCFA8), Color(mcHex: 0x7E9560)),
        (Color(mcHex: 0xDEC0A0), Color(mcHex: 0xB8834F)),
        (Color(mcHex: 0xC8BBA6), Color(mcHex: 0x94795C)),
        (Color(mcHex: 0xDCBCAD), Color(mcHex: 0xA96450)),
        (Color(mcHex: 0xD0CABB), Color(mcHex: 0x99907C)),
        (Color(mcHex: 0xDED6C2), Color(mcHex: 0xADA283)),
        (Color(mcHex: 0xC6A8B4), Color(mcHex: 0x8E5F72)),
        (Color(mcHex: 0xE3CE9C), Color(mcHex: 0xC09A44)),
        (Color(mcHex: 0xB7C9A4), Color(mcHex: 0x6F8757))
    ]

    /// `MD5(key)` read as a big-endian integer, modulo the number of gradients —
    /// the same arithmetic as Ruby's `hexdigest.to_i(16) % 10`.
    static func index(for key: String) -> Int {
        let digest = Insecure.MD5.hash(data: Data(key.utf8))
        return digest.reduce(0) { ($0 * 256 + Int($1)) % self.stops.count }
    }

    /// The web's `linear-gradient(150deg, start, end)`, approximated as a
    /// top-left-ish to bottom-right-ish sweep.
    static func gradient(for key: String) -> LinearGradient {
        let pair = self.stops[self.index(for: key)]
        return LinearGradient(
            colors: [pair.start, pair.end],
            startPoint: UnitPoint(x: 0.25, y: 0),
            endPoint: UnitPoint(x: 0.75, y: 1)
        )
    }
}

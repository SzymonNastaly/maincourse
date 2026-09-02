@testable import Hauptgang
import XCTest

final class RecipePlaceholderGradientTests: XCTestCase {
    func testIndexIsDeterministicAndInRange() {
        let first = RecipePlaceholderGradient.index(for: "42")
        let second = RecipePlaceholderGradient.index(for: "42")
        XCTAssertEqual(first, second)
        XCTAssertTrue((0 ..< RecipePlaceholderGradient.stops.count).contains(first))
    }

    func testIndexMatchesWebHelper() {
        // Verified with: ruby -rdigest -e 'puts Digest::MD5.hexdigest("1").to_i(16) % 10'  => 1
        XCTAssertEqual(RecipePlaceholderGradient.index(for: "1"), 1)
        // ruby -rdigest -e 'puts Digest::MD5.hexdigest("7").to_i(16) % 10'  => 5
        XCTAssertEqual(RecipePlaceholderGradient.index(for: "7"), 5)
    }
}

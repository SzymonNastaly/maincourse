@testable import Hauptgang
import XCTest

final class InstructionTimerTests: XCTestCase {
    func testDetailDecodesInstructionTimersAlignedWithSteps() throws {
        let recipe = try self.decodeDetail(timers: """
        "instruction_timers": [
          [{"text": "1 minute", "min_seconds": 60, "max_seconds": 60, "label": "Cook gently", "per_side": false}],
          [{"text": "6–8 minutes", "min_seconds": 360, "max_seconds": 480, "label": "Cook", "per_side": false}],
          [{"text": "3 minutes", "min_seconds": 180, "max_seconds": 180, "label": "Fry", "per_side": true}]
        ],
        """)

        XCTAssertEqual(recipe.timers(forStep: 0), [
            InstructionTimer(text: "1 minute", minSeconds: 60, maxSeconds: 60, label: "Cook gently")
        ])
        let range = try XCTUnwrap(recipe.timers(forStep: 1).first)
        XCTAssertTrue(range.isRange)
        XCTAssertEqual(range.choices, [360, 420, 480])
        XCTAssertTrue(recipe.timers(forStep: 2)[0].perSide)
        XCTAssertEqual(recipe.timers(forStep: 3), [], "a step past the array has no timers")
    }

    func testDetailFromAnOlderServerHasNoTimers() throws {
        let recipe = try self.decodeDetail(timers: "")

        XCTAssertNil(recipe.instructionTimers)
        XCTAssertEqual(recipe.timers(forStep: 0), [])
    }

    func testStepsNotParsedYetHaveEmptyArrays() throws {
        let recipe = try self.decodeDetail(timers: #""instruction_timers": [[], [], []],"#)

        XCTAssertEqual(recipe.instructionTimers?.count, 3)
        XCTAssertEqual(recipe.timers(forStep: 1), [])
    }

    func testRangeChoicesSkipAMiddleThatIsNotAWholeMinute() {
        let short = InstructionTimer(text: "1–2 minutes", minSeconds: 60, maxSeconds: 120, label: "Stir")
        let odd = InstructionTimer(text: "5–8 minutes", minSeconds: 300, maxSeconds: 480, label: "Stir")
        let single = InstructionTimer(text: "5 minutes", minSeconds: 300, maxSeconds: 300, label: "Stir")

        XCTAssertEqual(short.choices, [60, 120])
        XCTAssertEqual(odd.choices, [300, 480])
        XCTAssertEqual(single.choices, [300])
    }

    func testPersistedRecipeKeepsTimersThroughTheCache() throws {
        let recipe = try self.decodeDetail(timers: """
        "instruction_timers": [[{"text": "1 minute", "min_seconds": 60, "max_seconds": 60, "label": "Cook", "per_side": true}], [], []],
        """)

        let cached = PersistedRecipe(from: recipe).toRecipeDetail()

        XCTAssertEqual(cached.instructionTimers, recipe.instructionTimers)
        XCTAssertNil(try PersistedRecipe(from: self.decodeDetail(timers: "")).instructionTimersJson)
    }

    private func decodeDetail(timers: String) throws -> RecipeDetail {
        let payload = """
        {
          "id": 42,
          "name": "Orzo",
          "favorite": false,
          "ingredients": ["300 g orzo"],
          "instructions": ["Cook gently for 1 minute.", "Cook for 6–8 minutes.", "Fry for 3 minutes per side."],
          \(timers)
          "tags": [],
          "created_at": "2026-09-18T12:00:00Z",
          "updated_at": "2026-09-19T12:00:00Z"
        }
        """
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(RecipeDetail.self, from: Data(payload.utf8))
    }
}

@testable import Hauptgang
import SwiftUI
import XCTest

@MainActor
final class RecipeScreenAwakeTests: XCTestCase {
    func testRecipeContentAutomaticallyKeepsScreenAwakeUntilRemoved() async throws {
        let appeared = self.expectation(description: "Recipe appeared")
        let disappeared = self.expectation(description: "Recipe disappeared")
        let sample = try DemoRecipe.load()
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let window = UIWindow(windowScene: scene)
        let host = UIHostingController(rootView: AnyView(
            RecipeDetailContentView(
                recipe: sample.recipeDetail,
                heroImageHeight: 210,
                isIOS26: false,
                currentServings: .constant(nil)
            )
            .environment(\.scenePhase, .active)
            .onAppear { DispatchQueue.main.async { appeared.fulfill() } }
            .onDisappear { DispatchQueue.main.async { disappeared.fulfill() } }
        ))
        window.rootViewController = host
        window.isHidden = false
        defer { window.isHidden = true }

        await self.fulfillment(of: [appeared], timeout: 5)
        XCTAssertTrue(UIApplication.shared.isIdleTimerDisabled)
        host.rootView = AnyView(Color.clear)
        await self.fulfillment(of: [disappeared], timeout: 5)
        XCTAssertFalse(UIApplication.shared.isIdleTimerDisabled)
    }

    func testRecipeReleasesScreenWhenHiddenOrInactiveAndReacquiresOnReturn() {
        let controller = RecipeScreenAwakeController()
        let owner = UUID()
        defer { controller.remove(owner) }

        controller.update(owner, isVisible: true, scenePhase: .active, lowPowerMode: false)
        XCTAssertTrue(UIApplication.shared.isIdleTimerDisabled)

        for phase in [ScenePhase.inactive, .background] {
            controller.update(owner, isVisible: true, scenePhase: phase, lowPowerMode: false)
            XCTAssertFalse(UIApplication.shared.isIdleTimerDisabled)
            controller.update(owner, isVisible: true, scenePhase: .active, lowPowerMode: false)
            XCTAssertTrue(UIApplication.shared.isIdleTimerDisabled)
        }

        controller.update(owner, isVisible: false, scenePhase: .active, lowPowerMode: false)
        XCTAssertFalse(UIApplication.shared.isIdleTimerDisabled)
    }

    func testPowerSavingReleasesScreenAndTurningItOffRestoresWakefulness() {
        let controller = RecipeScreenAwakeController()
        let owner = UUID()
        defer { controller.remove(owner) }

        controller.update(owner, isVisible: true, scenePhase: .active, lowPowerMode: false)
        XCTAssertTrue(UIApplication.shared.isIdleTimerDisabled)
        controller.update(owner, isVisible: true, scenePhase: .active, lowPowerMode: true)
        XCTAssertFalse(UIApplication.shared.isIdleTimerDisabled)
        controller.update(owner, isVisible: true, scenePhase: .active, lowPowerMode: false)
        XCTAssertTrue(UIApplication.shared.isIdleTimerDisabled)
        controller.remove(owner)
        XCTAssertFalse(UIApplication.shared.isIdleTimerDisabled)
    }

    func testDepartingRecipeCannotReleaseAnotherVisibleRecipesRequest() {
        let controller = RecipeScreenAwakeController()
        let first = UUID()
        let second = UUID()
        defer {
            controller.remove(first)
            controller.remove(second)
        }

        controller.update(first, isVisible: true, scenePhase: .active, lowPowerMode: false)
        controller.update(second, isVisible: true, scenePhase: .active, lowPowerMode: false)
        controller.remove(first)
        XCTAssertTrue(UIApplication.shared.isIdleTimerDisabled)
        controller.remove(second)
        XCTAssertFalse(UIApplication.shared.isIdleTimerDisabled)
    }
}

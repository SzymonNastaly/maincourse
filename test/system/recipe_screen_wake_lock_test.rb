require "application_system_test_case"

class RecipeScreenWakeLockTest < ApplicationSystemTestCase
  setup do
    sign_in_through_the_form(users(:one))
    # Headless browsers cannot prove that a physical display stays lit. Replace
    # only the OS-facing API; exercise the real page, Stimulus and Turbo lifecycle.
    page.execute_script <<~JS
      window.wakeLockTest = { requests: [], locks: [], pending: [], mode: "grant", errors: [] }
      window.addEventListener("unhandledrejection", event => wakeLockTest.errors.push(String(event.reason)))
      Object.defineProperty(navigator, "wakeLock", { configurable: true, value: {
        request(type) {
          wakeLockTest.requests.push(type)
          if (wakeLockTest.mode === "deny") return Promise.reject(new DOMException("Power saving", "NotAllowedError"))
          const grant = () => {
            const lock = new EventTarget()
            lock.type = type
            lock.released = false
            lock.release = async () => {
              lock.released = true
              lock.dispatchEvent(new Event("release"))
            }
            wakeLockTest.locks.push(lock)
            return lock
          }
          return wakeLockTest.mode === "pending"
            ? new Promise(resolve => wakeLockTest.pending.push(() => resolve(grant())))
            : Promise.resolve(grant())
        }
      } })
    JS
  end

  test "recipe automatically holds a screen lock and releases it when navigating away" do
    assert_equal [], page.evaluate_script("wakeLockTest.requests")
    open_recipe
    assert_wake_state requests: 1, active: 1
    assert_equal [ "screen" ], page.evaluate_script("wakeLockTest.requests")

    within("main") { click_link "All Recipes", match: :first }
    assert_selector "h1", text: "All Recipes"
    assert_wake_state requests: 1, active: 0

    page.go_back
    assert_selector "h1", text: recipes(:one).name
    assert_wake_state requests: 2, active: 1
  end

  test "hidden recipes release the lock and visible recipes reacquire it" do
    open_recipe
    assert_wake_state requests: 1, active: 1
    set_visibility "hidden"
    assert_wake_state requests: 1, active: 0
    set_visibility "visible"
    assert_wake_state requests: 2, active: 1
  end

  test "a pending grant after navigation is immediately released" do
    page.execute_script 'wakeLockTest.mode = "pending"'
    open_recipe
    assert_wake_state requests: 1, active: 0
    within("main") { click_link "All Recipes", match: :first }
    assert_selector "h1", text: "All Recipes"
    page.execute_script "wakeLockTest.pending.shift()()"
    assert_wake_state requests: 1, active: 0
    assert_equal [ true ], page.evaluate_script("wakeLockTest.locks.map(lock => lock.released)")
  end

  test "a stale pending grant cannot replace the lock from a new visible session" do
    page.execute_script 'wakeLockTest.mode = "pending"'
    open_recipe
    assert_wake_state requests: 1, active: 0
    set_visibility "hidden"
    set_visibility "visible"
    assert_wake_state requests: 2, active: 0
    page.execute_script "wakeLockTest.pending.pop()()"
    assert_wake_state requests: 2, active: 1
    page.execute_script "wakeLockTest.pending.shift()()"
    assert_wake_state requests: 2, active: 1
    assert_equal [ false, true ], page.evaluate_script("wakeLockTest.locks.map(lock => lock.released)")
  end

  test "browser revocation does not immediately request another lock" do
    open_recipe
    assert_wake_state requests: 1, active: 1
    page.execute_script "wakeLockTest.locks[0].release()"
    assert_wake_state requests: 1, active: 0
    # Let any release-handler retry settle before checking the request count.
    page.driver.browser.execute_async_script "requestAnimationFrame(() => requestAnimationFrame(arguments[0]))"
    assert_wake_state requests: 1, active: 0
    set_visibility "hidden"
    set_visibility "visible"
    assert_wake_state requests: 2, active: 1
  end

  test "denied or unavailable wake locks leave the recipe usable" do
    page.execute_script 'wakeLockTest.mode = "deny"'
    open_recipe
    assert_wake_state requests: 1, active: 0
    find("[aria-label='More servings']").click
    assert_text "servings (×1.25)"
    assert_equal [], page.evaluate_script("wakeLockTest.errors")

    within("main") { click_link "All Recipes", match: :first }
    assert_selector "h1", text: "All Recipes"
    page.execute_script 'Object.defineProperty(navigator, "wakeLock", { value: undefined })'
    open_recipe
    find("[aria-label='More servings']").click
    assert_text "servings (×1.25)"
    assert_equal [], page.evaluate_script("wakeLockTest.errors")
  end

  test "Turbo caching releases the lock before the recipe disconnects" do
    open_recipe
    assert_wake_state requests: 1, active: 1
    page.execute_script 'document.dispatchEvent(new Event("turbo:before-cache"))'
    assert_wake_state requests: 1, active: 0
    set_visibility "hidden"
    set_visibility "visible"
    assert_wake_state requests: 1, active: 0
  end

  private
    def open_recipe
      page.execute_script "Turbo.visit(#{recipe_path(recipes(:one)).to_json})"
      assert_selector "h1", text: recipes(:one).name
      wait_for_stimulus("[data-controller~=portion-scaler]")
    end

    def set_visibility(state)
      page.execute_script <<~JS
        Object.defineProperty(document, "visibilityState", { configurable: true, value: #{state.to_json} })
        document.dispatchEvent(new Event("visibilitychange"))
      JS
    end

    def assert_wake_state(requests:, active:)
      expected = [ requests, active ]
      actual = nil
      deadline = Process.clock_gettime(Process::CLOCK_MONOTONIC) + Capybara.default_max_wait_time
      loop do
        actual = page.evaluate_script("[wakeLockTest.requests.length, wakeLockTest.locks.filter(lock => !lock.released).length]")
        break if actual == expected || Process.clock_gettime(Process::CLOCK_MONOTONIC) >= deadline
        sleep 0.05
      end
      assert_equal expected, actual, "Expected [wake requests, active screen locks]"
    end
end

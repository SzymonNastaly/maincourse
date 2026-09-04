require "test_helper"

class ApplicationSystemTestCase < ActionDispatch::SystemTestCase
  DESKTOP_SIZE = [ 1400, 1400 ].freeze
  MOBILE_SIZE = [ 390, 844 ].freeze

  driven_by :selenium, using: :headless_chrome, screen_size: DESKTOP_SIZE

  # A cold run — first browser launch, unwarmed assets — can leave Stimulus a few
  # seconds behind the first page load, which the 2s default doesn't cover.
  Capybara.default_max_wait_time = 5

  # Capybara reuses the browser between tests, so a test that shrinks the window
  # must put it back or every later test runs at phone width.
  def with_mobile_viewport
    resize_window_to(*MOBILE_SIZE)
    yield
  ensure
    resize_window_to(*DESKTOP_SIZE)
  end

  def resize_window_to(width, height)
    page.driver.browser.manage.window.resize_to(width, height)
  end

  # Importmap loads every Stimulus controller as its own module, so server-rendered
  # markup is clickable a moment before the controller behind it is listening. A
  # click that lands in that window hits an element with no handler and is simply
  # dropped — no error, just a test asserting against a page that never changed.
  #
  # Pass the element you are about to drive; this waits for the controllers it
  # names, whether through `data-controller` or the `data-action`s it fires.
  def wait_for_stimulus(selector)
    assert_selector selector, visible: :all

    Timeout.timeout(Capybara.default_max_wait_time) do
      sleep 0.05 until stimulus_connected?(selector)
    end
  end

  def sign_in_through_the_form(user, password: "password")
    visit new_session_path
    fill_in_credentials(user, password)

    # Under load this form occasionally comes back empty between the typing and
    # the click. Both fields are `required`, so the click then submits nothing at
    # all — no POST reaches the server — and the test waits out the clock on a
    # sign-in page it never left. Retype rather than submit a blank form.
    fill_in_credentials(user, password) unless has_field?("email_address", with: user.email_address, wait: 1)

    find("[data-testid=submit]").click

    assert_selector "h1", text: "All Recipes"
  end

  private
    def fill_in_credentials(user, password)
      fill_in "email_address", with: user.email_address
      fill_in "password", with: password
    end

    def stimulus_connected?(selector)
      page.evaluate_script(<<~JS)
        (() => {
          const element = document.querySelector(#{selector.to_json})
          if (!element || !window.Stimulus) return false

          const identifiers = new Set()
          for (const identifier of (element.dataset.controller || "").split(/\\s+/)) {
            if (identifier) identifiers.add(identifier)
          }
          for (const action of (element.dataset.action || "").split(/\\s+/)) {
            const match = action.match(/([\\w-]+)#/)
            if (match) identifiers.add(match[1])
          }

          return [...identifiers].every((identifier) => {
            const scope = element.closest(`[data-controller~="${identifier}"]`)
            return !!(scope && window.Stimulus.getControllerForElementAndIdentifier(scope, identifier))
          })
        })()
      JS
    end
end

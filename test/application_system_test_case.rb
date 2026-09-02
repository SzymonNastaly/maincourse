require "test_helper"

class ApplicationSystemTestCase < ActionDispatch::SystemTestCase
  DESKTOP_SIZE = [ 1400, 1400 ].freeze
  MOBILE_SIZE = [ 390, 844 ].freeze

  driven_by :selenium, using: :headless_chrome, screen_size: DESKTOP_SIZE

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

  def sign_in_through_the_form(user, password: "password")
    visit new_session_path
    fill_in "email_address", with: user.email_address
    fill_in "password", with: password
    find("[data-testid=submit]").click

    assert_selector "h1", text: "All Recipes"
  end
end

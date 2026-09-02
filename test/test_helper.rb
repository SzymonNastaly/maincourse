ENV["RAILS_ENV"] ||= "test"
require_relative "../config/environment"
require "rails/test_help"
require "webmock/minitest"
require "minitest/mock"
require_relative "test_helpers/session_test_helper"
require_relative "test_helpers/cookbook_test_helper"
require_relative "test_helpers/llm_stub_helper"

# Allow localhost connections for system tests (Selenium WebDriver, Capybara)
WebMock.disable_net_connect!(allow_localhost: true)

module ActiveSupport
  class TestCase
    include ActiveJob::TestHelper
    include LlmStubHelper

    # Run tests in parallel with specified workers
    parallelize(workers: :number_of_processors)

    # Setup all fixtures in test/fixtures/*.yml for all tests in alphabetical order.
    fixtures :all

    # Add more helper methods to be used by all tests here...
  end
end

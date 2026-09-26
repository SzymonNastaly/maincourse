require "test_helper"
require "rubocop"
require Rails.root.join("lib/rubocop/cop/maincourse/api_error_contract")

class ApiErrorContractCopTest < ActiveSupport::TestCase
  test "flags raw error payloads and unknown or dynamic codes" do
    [
      'render json: { error: "bad" }, status: :unprocessable_entity',
      'render json: { "error_code" => "forgotten" }',
      'payload = { error_code: "forgotten" }; render json: payload',
      "render json: payload, status: 422",
      'render_api_error "forgotten", status: :bad_request',
      "render_api_error params[:code], status: :bad_request",
      "head :unprocessable_entity"
    ].each { |source| assert offenses(source).any?, source }
  end

  test "accepts registered errors and ordinary success responses" do
    [
      'render_api_error "invalid_request", error: "bad", status: :bad_request',
      "render json: { id: 1 }, status: :created",
      "head :no_content"
    ].each { |source| assert_empty offenses(source), source }
  end

  test "import codes must be registered and diagnostic codes are not wire errors" do
    assert offenses('recipe.update!(import_error_code: "forgotten")', "app/jobs/recipe_import_job.rb").any?
    assert_empty offenses('recipe.update!(import_error_code: "import_failed")', "app/jobs/recipe_import_job.rb")
    assert_empty offenses('logger.warn(error_code: "internal_diagnostic")', "app/jobs/recipe_import_job.rb")
  end

  private

  def offenses(source, path = "app/controllers/api/v1/recipes_controller.rb")
    config = RuboCop::Config.new("AllCops" => { "TargetRubyVersion" => 3.4 })
    cop = RuboCop::Cop::Maincourse::ApiErrorContract.new(config)
    processed = RuboCop::ProcessedSource.new(source, 3.4, Rails.root.join(path).to_s)
    RuboCop::Cop::Commissioner.new([ cop ], [], raise_error: true).investigate(processed).offenses
  end
end

require "test_helper"

class Api::V1::ErrorContractTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    _, token = ApiToken.generate_for(@user)
    @headers = { "Authorization" => "Bearer #{token}" }
  end

  test "auth codes are independent of supported or unsupported requested languages" do
    [ "en", "pl-PL, en;q=0.5", "zz-ZZ", "*", "" ].each do |language|
      post api_v1_session_url, params: { email: @user.email_address, password: "incorrect" },
        headers: { "Accept-Language" => language }, as: :json
      assert_response :unauthorized
      assert_equal "invalid_credentials", response.parsed_body["error_code"]
      assert_equal "Invalid email or password", response.parsed_body["error"]
    end
    assert_equal :en, I18n.locale
  end

  test "validation returns field codes and only safe interpolation parameters" do
    post api_v1_registration_url, params: {
      email: @user.email_address, password: "private-password", password_confirmation: "different-private-password"
    }, as: :json
    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_equal "validation_failed", json["error_code"]
    assert_includes json["error_details"], { "field" => "email_address", "code" => "taken", "params" => {} }
    assert_includes json["error_details"], { "field" => "password_confirmation", "code" => "confirmation", "params" => {} }
    assert_includes json["errors"], "Email address has already been taken"
    refute_includes response.body, "private-password"
  end

  test "length constraints carry numeric counts" do
    patch api_v1_account_url, params: { user: { name: "x" * 51 } }, headers: @headers, as: :json
    assert_response :unprocessable_entity
    assert_equal [ { "field" => "name", "code" => "too_long", "params" => { "count" => 50 } } ],
      response.parsed_body["error_details"]
  end

  test "missing request parameters use the error envelope" do
    patch api_v1_account_url, params: {}, headers: @headers, as: :json
    assert_response :bad_request
    assert_equal "invalid_request", response.parsed_body["error_code"]
  end

  test "multipart image validation uses the same error contract as JSON" do
    post "/api/v1/recipes/extract_from_image", params: {
      image: fixture_file_upload("test/fixtures/files/test_image.png", "text/plain")
    }, headers: @headers
    assert_response :unprocessable_entity
    assert_equal "invalid_image", response.parsed_body["error_code"]

    post "/api/v1/recipes/extract_from_text", params: { text: "x" * 50_001 }, headers: @headers, as: :json
    assert_response :unprocessable_entity
    assert_equal "text_too_long", response.parsed_body["error_code"]
    assert_equal({ "count" => 50_000 }, response.parsed_body["error_params"])
  end

  test "failed imports expose codes independently of prose and preserve recipe names" do
    recipe = recipes(:one)
    recipe.update!(import_status: :failed, import_error_code: "no_recipe_in_photo", error_message: "Arbitrary text")
    get api_v1_recipes_url, headers: @headers, as: :json
    row = response.parsed_body.find { |item| item["id"] == recipe.id }
    assert_equal "no_recipe_in_photo", row["import_error_code"]
    assert_equal "Arbitrary text", row["error_message"]
    assert_equal recipe.name, row["name"]
  end
end

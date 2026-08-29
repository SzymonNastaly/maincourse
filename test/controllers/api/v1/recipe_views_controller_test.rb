require "test_helper"

class Api::V1::RecipeViewsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    _token_record, @raw_token = ApiToken.generate_for(@user)
    @auth_headers = { "Authorization" => "Bearer #{@raw_token}" }
    @recipe = recipes(:one)
  end

  test "create records a batch of views" do
    post api_v1_recipe_views_url,
         params: { views: [
           { recipe_id: @recipe.id, viewed_at: 2.hours.ago.iso8601 },
           { recipe_id: @recipe.id, viewed_at: 1.hour.ago.iso8601 }
         ] },
         headers: @auth_headers, as: :json

    assert_response :no_content
    engagement = RecipeEngagement.find_by(user_id: @user.id, recipe_id: @recipe.id)
    assert_equal 2, engagement.view_count
  end

  test "create defaults a missing viewed_at to now" do
    post api_v1_recipe_views_url,
         params: { views: [ { recipe_id: @recipe.id } ] },
         headers: @auth_headers, as: :json

    assert_response :no_content
    engagement = RecipeEngagement.find_by(user_id: @user.id, recipe_id: @recipe.id)
    assert_operator engagement.last_viewed_at, :>, 5.minutes.ago
  end

  test "create skips recipes the user cannot access" do
    post api_v1_recipe_views_url,
         params: { views: [ { recipe_id: recipes(:two).id } ] },
         headers: @auth_headers, as: :json

    assert_response :no_content
    assert_nil RecipeEngagement.find_by(user_id: @user.id, recipe_id: recipes(:two).id)
  end

  test "create skips unknown recipe ids" do
    post api_v1_recipe_views_url,
         params: { views: [ { recipe_id: 999_999 } ] },
         headers: @auth_headers, as: :json

    assert_response :no_content
  end

  test "create rejects a missing views array" do
    post api_v1_recipe_views_url, params: {}, headers: @auth_headers, as: :json

    assert_response :unprocessable_entity
  end

  test "create requires authentication" do
    post api_v1_recipe_views_url, params: { views: [] }, as: :json

    assert_response :unauthorized
  end
end

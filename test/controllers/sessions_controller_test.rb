require "test_helper"

class SessionsControllerTest < ActionDispatch::IntegrationTest
  setup { @user = User.take }

  test "new" do
    get new_session_path
    assert_response :success
  end

  test "new shows configured OAuth providers as POST forms" do
    oauth_config = Rails.application.config.x.oauth

    oauth_config.stub(:apple_enabled, true) do
      oauth_config.stub(:google_enabled, true) do
        get new_session_path
      end
    end

    assert_select "form[action='/auth/apple'][method='post']"
    assert_select "form[action='/auth/google_oauth2'][method='post']"
    assert_select "[data-turbo='false']", count: 2
  end

  test "new hides OAuth providers on the legacy production host" do
    host! "cook.hauptgang.app"
    oauth_config = Rails.application.config.x.oauth

    Rails.env.stub(:production?, true) do
      oauth_config.stub(:apple_enabled, true) do
        oauth_config.stub(:google_enabled, true) do
          get new_session_path
        end
      end
    end

    assert_response :success
    assert_select "form[action='/auth/apple']", count: 0
    assert_select "form[action='/auth/google_oauth2']", count: 0
  end

  test "create with valid credentials" do
    post session_path, params: { email_address: @user.email_address, password: "password" }

    assert_redirected_to root_path
    assert cookies[:session_id]
  end

  test "create with invalid credentials" do
    post session_path, params: { email_address: @user.email_address, password: "wrong" }

    assert_redirected_to new_session_path
    assert_nil cookies[:session_id]
  end

  test "destroy" do
    sign_in_as(User.take)

    delete session_path

    assert_redirected_to new_session_path
    assert_empty cookies[:session_id]
  end
end

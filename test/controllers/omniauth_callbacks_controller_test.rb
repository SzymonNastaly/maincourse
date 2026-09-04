require "test_helper"

class OmniauthCallbacksControllerTest < ActionDispatch::IntegrationTest
  setup do
    @previous_test_mode = OmniAuth.config.test_mode
    @previous_google_auth = OmniAuth.config.mock_auth[:google_oauth2]
    @previous_apple_auth = OmniAuth.config.mock_auth[:apple]
    OmniAuth.config.test_mode = true
  end

  teardown do
    OmniAuth.config.test_mode = @previous_test_mode
    restore_mock(:google_oauth2, @previous_google_auth)
    restore_mock(:apple, @previous_apple_auth)
  end

  test "requires password sign-in before linking Google to a password account" do
    user = users(:one)
    OmniAuth.config.mock_auth[:google_oauth2] = auth_hash(
      provider: "google_oauth2",
      uid: "google-web-user",
      email: user.email_address,
      name: "Web User"
    )

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      get auth_google_oauth2_callback_path
    end

    assert_redirected_to new_session_path
    assert_match "Sign in with your password", flash[:alert]
    assert user.reload.authenticate("password")
  end

  test "creates an account from an Apple callback and stores the refresh token" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "apple-web-user",
      email: "apple-web@example.com",
      name: "Apple Web User",
      refresh_token: "apple-web-refresh-token"
    )

    assert_difference [ "User.count", "Identity.count", "Session.count" ], 1 do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        post auth_apple_callback_path
      end
    end

    assert_redirected_to root_url
    identity = Identity.find_by!(provider: "apple", uid: "apple-web-user")
    assert_equal "apple-web-refresh-token", identity.apple_refresh_token_for("app.hauptgang.web")
  end

  test "revokes an Apple token when a password account must be linked manually" do
    user = users(:one)
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "apple-link-user",
      email: user.email_address,
      refresh_token: "unused-web-refresh-token"
    )
    apple_client = FakeAppleClient.new

    Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
      Oauth::AppleClient.stub(:new, apple_client) do
        post auth_apple_callback_path
      end
    end

    assert_redirected_to new_session_path
    assert_equal [
      { refresh_token: "unused-web-refresh-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "rejects a provider identity without a verified email" do
    OmniAuth.config.mock_auth[:google_oauth2] = auth_hash(
      provider: "google_oauth2",
      uid: "unverified-web-user",
      email: "unverified@example.com",
      email_verified: false
    )

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      get auth_google_oauth2_callback_path
    end

    assert_redirected_to new_session_path
    assert_match "couldn't sign you in", flash[:alert]
  end

  test "failure redirects to sign in with a generic error" do
    get auth_failure_path

    assert_redirected_to new_session_path
    assert_match "couldn't sign you in", flash[:alert]
  end

  private
    class FakeAppleClient
      attr_reader :revocations

      def initialize
        @revocations = []
      end

      def revoke!(refresh_token:, client_id:)
        revocations << { refresh_token:, client_id: }
      end
    end

    def auth_hash(provider:, uid:, email:, name: nil, email_verified: true, refresh_token: nil)
      OmniAuth::AuthHash.new(
        provider:,
        uid:,
        info: { email:, name:, email_verified: },
        credentials: { token: "provider-token", refresh_token: }
      )
    end

    def restore_mock(provider, value)
      if value
        OmniAuth.config.mock_auth[provider] = value
      else
        OmniAuth.config.mock_auth.delete(provider)
      end
    end
end

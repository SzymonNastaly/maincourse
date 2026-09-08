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

  test "captures confirmed Apple account creation intent in the real OmniAuth request phase" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "apple-web-user",
      email: "apple-web@example.com",
      name: "Apple Web User",
      refresh_token: "rejected-apple-web-refresh-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path

    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "apple-web-user",
      email: "apple-web@example.com",
      name: "Apple Web User",
      refresh_token: "fresh-apple-web-refresh-token"
    )

    assert_difference [ "User.count", "Identity.count", "Session.count" ], 1 do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        post "/auth/apple?allow_account_creation=true"
        assert_redirected_to auth_apple_callback_url
        assert_nil URI.parse(response.location).query

        post auth_apple_callback_path
      end
    end

    assert_redirected_to root_url
    identity = Identity.find_by!(provider: "apple", uid: "apple-web-user")
    assert_equal "fresh-apple-web-refresh-token", identity.apple_refresh_token_for("app.hauptgang.web")
    assert_equal [
      { refresh_token: "rejected-apple-web-refresh-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "an unknown Apple callback redirects to confirmation and revokes its unused token" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "unconfirmed-web-user",
      email: "unconfirmed-web@example.com",
      refresh_token: "unused-unconfirmed-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path
    assert_equal [
      { refresh_token: "unused-unconfirmed-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "does not trust account creation intent added only to the Apple callback" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "tampered-web-user",
      email: "tampered-web@example.com",
      refresh_token: "tampered-refresh-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post auth_apple_callback_path, params: { allow_account_creation: "true" }
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path
  end

  test "consumes confirmed account creation intent after one callback" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "one-time-confirmed-user",
      email: "one-time-confirmed@example.com",
      refresh_token: "confirmed-refresh-token"
    )
    apple_client = FakeAppleClient.new

    Oauth::AppleClient.stub(:new, apple_client) do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        post "/auth/apple?allow_account_creation=true"
        post auth_apple_callback_path
      end
    end

    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "replayed-intent-user",
      email: "replayed-intent@example.com",
      refresh_token: "replayed-refresh-token"
    )

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path
  end

  test "does not accept a nonliteral request-phase confirmation value" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "invalid-web-confirmation",
      email: "invalid-web-confirmation@example.com",
      refresh_token: "invalid-confirmation-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post "/auth/apple?allow_account_creation=1"
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path
  end

  test "signs in a known Apple identity without account creation confirmation" do
    user = users(:one)
    Identity.create!(provider: "apple", uid: "known-web-apple", email: "old-apple@example.com", user:)
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "known-web-apple",
      email: "changed-apple@example.com",
      refresh_token: "known-apple-refresh-token"
    )

    assert_no_difference [ "User.count", "Identity.count" ] do
      assert_difference "Session.count", 1 do
        Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to root_url
    assert_equal "changed-apple@example.com", user.identities.apple.find_by!(uid: "known-web-apple").email
  end

  test "keeps unknown Google account creation unchanged" do
    OmniAuth.config.mock_auth[:google_oauth2] = auth_hash(
      provider: "google_oauth2",
      uid: "new-google-web-user",
      email: "new-google-web@example.com"
    )

    assert_difference [ "User.count", "Identity.count", "Session.count" ], 1 do
      get auth_google_oauth2_callback_path
    end

    assert_redirected_to root_url
  end

  test "confirmation page starts a fresh Apple request without retaining credentials" do
    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Rails.application.config.x.oauth.stub(:apple_enabled, true) do
        get confirm_apple_account_creation_path
      end
    end

    assert_response :success
    assert_select "h1", "Create a separate account?"
    assert_select "a[href='#{new_session_path}']", "Use existing account"
    assert_select "form[action='/auth/apple?allow_account_creation=true'][method='post'][data-turbo='false']"
    assert_select "input[name='id_token']", count: 0
    assert_select "input[name='authorization_code']", count: 0
    assert_select "input[name='refresh_token']", count: 0
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

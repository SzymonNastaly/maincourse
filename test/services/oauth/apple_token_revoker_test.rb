require "test_helper"

class Oauth::AppleTokenRevokerTest < ActiveSupport::TestCase
  test "revokes every stored Apple refresh token with its original client id" do
    identity = Identity.create!(
      user: users(:one),
      provider: "apple",
      uid: "apple-revocation-user",
      email: users(:one).email_address,
      apple_refresh_tokens: {
        "app.hauptgang.ios" => "ios-refresh-token",
        "app.hauptgang.web" => "web-refresh-token"
      }
    )
    client = FakeAppleClient.new

    Oauth::AppleClient.stub(:new, client) do
      Oauth::AppleTokenRevoker.call(identity.user)
    end

    assert_equal [
      { refresh_token: "ios-refresh-token", client_id: "app.hauptgang.ios" },
      { refresh_token: "web-refresh-token", client_id: "app.hauptgang.web" }
    ], client.revocations
  end

  test "reports unexpected provider errors without interrupting deletion" do
    client = Object.new
    client.define_singleton_method(:revoke!) do |**|
      raise OpenSSL::PKey::PKeyError, "invalid key"
    end
    reported = nil

    Rails.error.stub(:report, ->(error, handled:, context:) { reported = [ error, handled, context ] }) do
      Oauth::AppleClient.stub(:new, client) do
        assert_nothing_raised do
          Oauth::AppleTokenRevoker.revoke(refresh_token: "token", client_id: "client", context: { source: "test" })
        end
      end
    end

    assert_instance_of OpenSSL::PKey::PKeyError, reported.first
    assert reported.second
    assert_equal({ source: "test" }, reported.third)
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
end

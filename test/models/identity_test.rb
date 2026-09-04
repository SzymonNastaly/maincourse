require "test_helper"

class IdentityTest < ActiveSupport::TestCase
  test "creates an OAuth-only user without a password" do
    user = nil

    assert_difference [ "User.count", "Identity.count" ], 1 do
      user = Identity.authenticate!(
        provider: "google",
        uid: "google-new-user",
        email: " New.User@example.com ",
        email_verified: true,
        name: "New User"
      )
    end

    assert_equal "new.user@example.com", user.email_address
    assert_equal "New User", user.name
    assert_nil user.password_digest
    assert user.authenticate("anything") == false
  end

  test "does not auto-link a verified identity to an existing password account" do
    user = users(:one)
    session = user.sessions.create!
    token, = ApiToken.generate_for(user)

    assert_no_difference [ "User.count", "Identity.count" ] do
      assert_raises Oauth::LinkRequiredError do
        Identity.authenticate!(
          provider: "google",
          uid: "google-existing-user",
          email: user.email_address.upcase,
          email_verified: true,
          name: "Existing User"
        )
      end
    end

    assert user.reload.authenticate("password")
    assert Session.exists?(session.id)
    assert ApiToken.exists?(token.id)
  end

  test "links another provider to an existing OAuth-only account by email" do
    user = Identity.authenticate!(
      provider: "google",
      uid: "google-oauth-only-user",
      email: "oauth-only@example.com",
      email_verified: true
    )

    assert_no_difference "User.count" do
      result = Identity.authenticate!(
        provider: "apple",
        uid: "apple-oauth-only-user",
        email: user.email_address,
        email_verified: true,
        apple_refresh_token: "apple-refresh-token",
        apple_client_id: "app.hauptgang.ios"
      )

      assert_equal user, result
    end

    assert_equal 2, user.identities.count
  end

  test "finds an existing identity by provider uid before considering email" do
    identity = Identity.create!(
      user: users(:one),
      provider: "google",
      uid: "stable-google-id",
      email: "old@example.com"
    )

    result = Identity.authenticate!(
      provider: "google",
      uid: identity.uid,
      email: "new@example.com",
      email_verified: true
    )

    assert_equal users(:one), result
    assert_equal "new@example.com", identity.reload.email
  end

  test "rejects an unverified email" do
    assert_raises Oauth::Error do
      Identity.authenticate!(
        provider: "google",
        uid: "unverified-google-id",
        email: "unverified@example.com",
        email_verified: false
      )
    end
  end

  test "encrypts Apple refresh tokens and preserves one when Apple omits it later" do
    token = "apple-refresh-token"
    user = Identity.authenticate!(
      provider: "apple",
      uid: "apple-user-id",
      email: "apple@example.com",
      email_verified: true,
      apple_refresh_token: token,
      apple_client_id: "app.hauptgang.ios"
    )
    identity = user.identities.first
    ciphertext = Identity.connection.select_value(
      "SELECT apple_refresh_tokens FROM identities WHERE id = #{identity.id.to_i}"
    )

    assert_not_equal token, ciphertext
    assert_equal token, identity.reload.apple_refresh_token_for("app.hauptgang.ios")

    Identity.authenticate!(
      provider: "apple",
      uid: identity.uid,
      email: identity.email,
      email_verified: true,
      apple_refresh_token: "web-refresh-token",
      apple_client_id: "app.hauptgang.web"
    )

    Identity.authenticate!(
      provider: "apple",
      uid: identity.uid,
      email: identity.email,
      email_verified: true
    )

    identity.reload
    assert_equal token, identity.apple_refresh_token_for("app.hauptgang.ios")
    assert_equal "web-refresh-token", identity.apple_refresh_token_for("app.hauptgang.web")
  end
end

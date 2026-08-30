require "test_helper"

class InviteLinkTest < ActiveSupport::TestCase
  test "uses config.x.canonical_host over https in production" do
    config = Rails.application.config.x
    original_host = config.canonical_host
    config.canonical_host = "sentinel.example.test"

    begin
      Rails.env.stub(:production?, true) do
        assert_equal "https://sentinel.example.test/invite/tok123",
                     InviteLink.url_for("tok123")
      end
    ensure
      config.canonical_host = original_host
    end
  end

  test "raises when config.x.canonical_host is blank in production" do
    config = Rails.application.config.x
    original_host = config.canonical_host
    config.canonical_host = nil

    begin
      Rails.env.stub(:production?, true) do
        assert_raises(RuntimeError) { InviteLink.url_for("tok123") }
      end
    ensure
      config.canonical_host = original_host
    end
  end

  test "uses the custom scheme outside production" do
    assert_equal "hauptgang://invite/tok123", InviteLink.url_for("tok123")
  end

  # Blunt on purpose: this is the one test that actually fails if someone
  # typos or changes the production canonical host literal. The test above
  # only proves InviteLink reads config.x.canonical_host, not that the
  # production value is correct -- so we grep the production environment
  # file directly for the literal the whole domain migration turns on.
  test "production environment sets the canonical host literal" do
    production_config = Rails.root.join("config/environments/production.rb").read
    assert_match(/canonical_host = "app\.getmaincourse\.com"/, production_config)
  end
end

require "test_helper"

class InviteLinkTest < ActiveSupport::TestCase
  test "uses the canonical host over https in production" do
    Rails.env.stub(:production?, true) do
      assert_equal "https://app.getmaincourse.com/invite/tok123",
                   InviteLink.url_for("tok123")
    end
  end

  test "uses the custom scheme outside production" do
    assert_equal "hauptgang://invite/tok123", InviteLink.url_for("tok123")
  end
end

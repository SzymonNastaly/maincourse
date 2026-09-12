require "test_helper"

class DeviceTokenTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
  end

  test "register! creates a new token" do
    record = DeviceToken.register!(user: @user, token: "abc123", environment: "sandbox")

    assert record.persisted?
    assert_equal @user, record.user
    assert_equal "abc123", record.token
    assert_equal "sandbox", record.environment
    assert_equal "apns", record.provider
    assert_not_nil record.last_used_at
  end

  test "register! updates an existing token (re-registration)" do
    DeviceToken.register!(user: @user, token: "abc123", environment: "sandbox")
    other = users(:two)
    record = DeviceToken.register!(user: other, token: "abc123", environment: "production")

    assert_equal 1, DeviceToken.where(token: "abc123").count
    assert_equal other, record.user
    assert_equal "production", record.environment
  end

  test "active scope excludes tokens last used long ago" do
    fresh = DeviceToken.register!(user: @user, token: "fresh", environment: "production")
    stale = DeviceToken.create!(user: @user, token: "stale", environment: "production", last_used_at: 100.days.ago)

    active_ids = DeviceToken.active.pluck(:id)
    assert_includes active_ids, fresh.id
    assert_not_includes active_ids, stale.id
  end

  test "validates environment" do
    record = DeviceToken.new(user: @user, token: "x", environment: "weird")
    assert_not record.valid?
    assert_includes record.errors[:environment], "is not included in the list"
  end

  test "the same opaque token can exist for different providers" do
    apns = DeviceToken.register!(user: @user, token: "shared-value", provider: "apns", environment: "production")
    fcm = DeviceToken.register!(user: @user, token: "shared-value", provider: "fcm", environment: "production")

    assert_not_equal apns.id, fcm.id
    assert_equal 2, DeviceToken.where(token: "shared-value").count
  end

  test "validates provider" do
    record = DeviceToken.new(user: @user, token: "x", provider: "unknown", environment: "production")

    assert_not record.valid?
    assert_includes record.errors[:provider], "is not included in the list"
  end
end

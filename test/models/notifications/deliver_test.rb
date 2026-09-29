require "test_helper"

class Notifications::DeliverTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @recipe = recipes(:one)
    @user.device_tokens.create!(token: "deliver-token", environment: "sandbox")
    @candidate = Notifications::Candidate.new(
      campaign: "resurface", recipe: @recipe, cookbook: @recipe.cookbook,
      body_key: "push.lifecycle.resurface", body_params: { recipe: @recipe.name }
    )
  end

  def stub_push(result)
    pushes = []
    stub = ->(**kwargs) { pushes << kwargs; result }
    Apns::Client.stub(:push, stub) { yield pushes }
  end

  def ok_result
    Apns::Client::Result.new(ok?: true, status: 200, reason: nil)
  end

  test "records the delivery and pushes to each active device" do
    stub_push(ok_result) do |pushes|
      delivery = Notifications::Deliver.new(user: @user, candidate: @candidate).call

      assert_equal 1, pushes.size
      assert_equal "deliver-token", pushes.first[:token]
      assert_equal "resurface", delivery.campaign
      assert_equal @recipe.id, delivery.recipe_id
      assert_not_nil delivery.sent_at
    end
  end

  test "fans out across APNs and FCM registrations" do
    @user.device_tokens.create!(token: "android-token", provider: "fcm", environment: "production")
    apns_pushes = []
    fcm_pushes = []
    apns_stub = lambda do |**kwargs|
      apns_pushes << kwargs
      Apns::Client::Result.new(ok?: true, status: 200, reason: nil)
    end
    fcm_stub = lambda do |**kwargs|
      fcm_pushes << kwargs
      Fcm::Client::Result.new(ok?: true, status: 200, reason: nil)
    end

    Apns::Client.stub(:push, apns_stub) do
      Fcm::Client.stub(:push, fcm_stub) do
        assert_not_nil Notifications::Deliver.new(user: @user, candidate: @candidate).call
      end
    end

    assert_equal [ "deliver-token" ], apns_pushes.pluck(:token)
    assert_equal [ "android-token" ], fcm_pushes.pluck(:token)
  end

  test "an invalid FCM token is pruned without removing another device" do
    fcm = @user.device_tokens.create!(token: "invalid-android", provider: "fcm", environment: "production")
    rejected = Fcm::Client::Result.new(ok?: false, status: 404, reason: "UNREGISTERED")

    stub_push(ok_result) do
      Fcm::Client.stub(:push, rejected) do
        assert_not_nil Notifications::Deliver.new(user: @user, candidate: @candidate).call
      end
    end

    assert_not DeviceToken.exists?(fcm.id)
    assert DeviceToken.exists?(token: "deliver-token", provider: "apns")
  end

  test "one provider outage does not prevent another device from receiving the notification" do
    @user.device_tokens.create!(token: "android-token", provider: "fcm", environment: "production")

    stub_push(ok_result) do
      Fcm::Client.stub(:push, ->(**) { raise Fcm::Client::MissingCredentialsError, "not configured" }) do
        assert_not_nil Notifications::Deliver.new(user: @user, candidate: @candidate).call
      end
    end

    assert_equal 2, @user.device_tokens.count
  end

  test "sends the delivery id and target in the custom payload" do
    stub_push(ok_result) do |pushes|
      delivery = Notifications::Deliver.new(user: @user, candidate: @candidate).call

      custom = pushes.first[:custom]
      assert_equal delivery.id, custom[:delivery_id]
      assert_equal "resurface", custom[:campaign]
      assert_equal @recipe.id, custom[:recipe_id]
    end
  end

  test "marks the recipe as suggested" do
    stub_push(ok_result) do |_pushes|
      Notifications::Deliver.new(user: @user, candidate: @candidate).call

      engagement = RecipeEngagement.find_by(user_id: @user.id, recipe_id: @recipe.id)
      assert_equal 1, engagement.suggested_count
      assert_not_nil engagement.last_suggested_at
    end
  end

  test "destroys a device token APNs rejects as invalid" do
    rejected = Apns::Client::Result.new(ok?: false, status: 410, reason: "Unregistered")

    stub_push(rejected) do |_pushes|
      Notifications::Deliver.new(user: @user, candidate: @candidate).call
    end

    assert_equal 0, @user.device_tokens.count
  end

  test "records nothing and does not mark suggested when every push fails" do
    rejected = Apns::Client::Result.new(ok?: false, status: 410, reason: "Unregistered")

    stub_push(rejected) do |pushes|
      delivery = Notifications::Deliver.new(user: @user, candidate: @candidate).call

      assert_nil delivery
      assert_equal 1, pushes.size
    end

    assert_equal 0, NotificationDelivery.where(user: @user).count
    assert_equal 0, @user.device_tokens.count
    assert_nil RecipeEngagement.find_by(user_id: @user.id, recipe_id: @recipe.id)
  end

  test "records the delivery and marks suggested when at least one push succeeds" do
    delivery = nil

    stub_push(ok_result) do |_pushes|
      delivery = Notifications::Deliver.new(user: @user, candidate: @candidate).call
    end

    assert_not_nil delivery
    assert NotificationDelivery.exists?(delivery.id)
    engagement = RecipeEngagement.find_by(user_id: @user.id, recipe_id: @recipe.id)
    assert_equal 1, engagement.suggested_count
  end

  test "returns nil and records nothing when the user has no active device tokens" do
    @user.device_tokens.destroy_all

    stub_push(ok_result) do |pushes|
      delivery = Notifications::Deliver.new(user: @user, candidate: @candidate).call

      assert_nil delivery
      assert_equal 0, pushes.size
    end

    assert_equal 0, NotificationDelivery.where(user: @user).count
  end

  test "discards a candidate whose cookbook the user is no longer a member of" do
    other_cookbook = cookbooks(:two_personal)
    candidate = Notifications::Candidate.new(
      campaign: "stale_shopping_list", recipe: nil, cookbook: other_cookbook,
      body_key: "push.lifecycle.stale_shopping_list", body_params: { count: 3 }
    )

    stub_push(ok_result) do |pushes|
      delivery = Notifications::Deliver.new(user: @user, candidate: candidate).call

      assert_nil delivery
      assert_equal 0, pushes.size
    end

    assert_equal 0, NotificationDelivery.where(user: @user).count
  end

  test "renders one delivery in each installation's app language" do
    @user.device_tokens.find_by!(token: "deliver-token").update!(language: "de")
    @user.device_tokens.create!(token: "android-token", provider: "fcm", environment: "production", language: "pl")
    @user.device_tokens.create!(token: "old-ipad", environment: "sandbox")
    fcm_pushes = []
    fcm_stub = lambda do |**kwargs|
      fcm_pushes << kwargs
      Fcm::Client::Result.new(ok?: true, status: 200, reason: nil)
    end

    stub_push(ok_result) do |apns_pushes|
      Fcm::Client.stub(:push, fcm_stub) do
        I18n.with_locale(:pl) do
          assert_difference -> { NotificationDelivery.count }, 1 do
            Notifications::Deliver.new(user: @user, candidate: @candidate).call
          end
          assert_equal :pl, I18n.locale
        end
      end

      bodies = apns_pushes.to_h { |push| [ push[:token], push[:aps][:alert][:body] ] }
      assert_equal "Du hast „#{@recipe.name}“ vor einer Weile gespeichert. Diese Woche kochen?", bodies["deliver-token"]
      assert_equal %(You saved "#{@recipe.name}" a while back. Cook it this week?), bodies["old-ipad"]
    end

    assert_equal "Przepis „#{@recipe.name}” czeka już od dłuższego czasu. Ugotujesz go w tym tygodniu?",
                 fcm_pushes.first[:alert][:body]
  end
end

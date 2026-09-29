require "test_helper"

class DeliverPendingNotificationJobTest < ActiveSupport::TestCase
  setup do
    @owner = users(:one)
    @collab = users(:two)
    @cookbook = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: @cookbook, user: @owner, role: :owner)
    CookbookMembership.create!(cookbook: @cookbook, user: @collab, role: :collaborator)
    @device = DeviceToken.register!(user: @collab, token: "device-1", environment: "sandbox")
  end

  test "no-op when pending notification is missing" do
    assert_nothing_raised do
      DeliverPendingNotificationJob.new.perform(999_999)
    end
  end

  test "sends shopping_list push and deletes pending row" do
    pending = PendingNotification.create!(
      cookbook: @cookbook, actor: @owner, recipient: @collab,
      category: "shopping_list",
      payload: [ { "name" => "Milk" }, { "name" => "Bread" } ],
      delivery_scheduled_at: Time.current
    )

    captured = nil
    Apns::Client.stub :push, ->(**kwargs) {
      captured = kwargs
      Apns::Client::Result.new(ok?: true, status: 200, reason: nil)
    } do
      DeliverPendingNotificationJob.new.perform(pending.id)
    end

    assert_nil PendingNotification.find_by(id: pending.id)
    assert_equal "device-1", captured[:token]
    assert_equal "sandbox", captured[:environment]
    assert_match(/Added 2 items/, captured[:aps][:alert][:body])
    assert_equal @cookbook.id, captured[:custom][:cookbook_id]
  end

  test "summarizes meal_plan adds + votes" do
    pending = PendingNotification.create!(
      cookbook: @cookbook, actor: @owner, recipient: @collab,
      category: "meal_plan_activity",
      payload: [
        { "kind" => "entry_added", "recipe_name" => "Pasta" },
        { "kind" => "entry_added", "recipe_name" => "Salad" },
        { "kind" => "vote", "recipe_name" => "Pizza" }
      ],
      delivery_scheduled_at: Time.current
    )

    captured = nil
    Apns::Client.stub :push, ->(**kwargs) {
      captured = kwargs
      Apns::Client::Result.new(ok?: true, status: 200, reason: nil)
    } do
      DeliverPendingNotificationJob.new.perform(pending.id)
    end

    body = captured[:aps][:alert][:body]
    assert_match(/2 recipes/, body)
    assert_match(/voted on 1/, body)
  end

  test "prunes invalid device tokens" do
    pending = PendingNotification.create!(
      cookbook: @cookbook, actor: @owner, recipient: @collab,
      category: "shopping_list", payload: [ { "name" => "X" } ],
      delivery_scheduled_at: Time.current
    )

    Apns::Client.stub :push, ->(**) {
      Apns::Client::Result.new(ok?: false, status: 410, reason: "Unregistered")
    } do
      DeliverPendingNotificationJob.new.perform(pending.id)
    end

    assert_nil DeviceToken.find_by(id: @device.id)
  end

  test "renders shared-cookbook activity in each installation's language" do
    @device.update!(language: "pl")
    @owner.update!(name: "Ana")
    DeviceToken.register!(user: @collab, token: "device-2", environment: "sandbox", language: "de")
    pending = PendingNotification.create!(
      cookbook: @cookbook, actor: @owner, recipient: @collab,
      category: "shopping_list",
      payload: [ { "name" => "Milk" }, { "name" => "Bread" } ],
      delivery_scheduled_at: Time.current
    )

    pushes = []
    Apns::Client.stub :push, ->(**kwargs) {
      pushes << kwargs
      Apns::Client::Result.new(ok?: true, status: 200, reason: nil)
    } do
      DeliverPendingNotificationJob.new.perform(pending.id)
    end

    alerts = pushes.to_h { |push| [ push[:token], push[:aps][:alert] ] }
    assert_equal "Dodano 2 pozycje do listy zakupów", alerts["device-1"][:body]
    assert_equal "Ana w „Shared”", alerts["device-1"][:title]
    assert_equal "2 Einträge zur Einkaufsliste hinzugefügt", alerts["device-2"][:body]
    assert_equal "Ana in „Shared“", alerts["device-2"][:title]
  end
end

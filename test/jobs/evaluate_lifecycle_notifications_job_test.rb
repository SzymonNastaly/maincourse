require "test_helper"

class EvaluateLifecycleNotificationsJobTest < ActiveSupport::TestCase
  setup do
    # The send-window tests below travel to a fixed date (2026-09-15). Anchor every
    # relative ".ago" in this setup to that same date, rather than to whatever the real
    # wall clock is when the suite runs, so recipe/user ages land inside the campaigns'
    # eligibility windows regardless of when this test happens to execute.
    travel_to(Time.utc(2026, 9, 15)) do
      @user = users(:one)
      @cookbook = cookbooks(:one_personal)
      @user.update_columns(time_zone: "Europe/Zurich", last_active_at: 10.days.ago)
      @user.device_tokens.create!(token: "job-token", environment: "sandbox")

      # Make the import follow-up campaign eligible.
      Recipe.where(cookbook: @cookbook).update_all(created_at: 1.year.ago)
      @recipe = @cookbook.recipes.create!(name: "Ramen", user: @user, import_status: :completed)
      @recipe.update_column(:created_at, 3.days.ago)

      users(:two).update_column(:lifecycle_notifications_enabled, false)
    end
  end

  # 17:30 in Europe/Zurich, inside the send window.
  def in_window(&block)
    travel_to(Time.utc(2026, 9, 15, 15, 30), &block)
  end

  def out_of_window(&block)
    travel_to(Time.utc(2026, 9, 15, 8, 30), &block)
  end

  def run_job
    Apns::Client.stub(:push, Apns::Client::Result.new(ok?: true, status: 200, reason: nil)) do
      EvaluateLifecycleNotificationsJob.perform_now
    end
  end

  test "sends one notification inside the send window" do
    in_window do
      assert_difference "NotificationDelivery.count", 1 do
        run_job
      end

      delivery = NotificationDelivery.order(:id).last
      assert_equal @user, delivery.user
      assert_equal "import_follow_up", delivery.campaign
    end
  end

  test "sends nothing outside the send window" do
    out_of_window do
      assert_no_difference "NotificationDelivery.count" do
        run_job
      end
    end
  end

  test "sends nothing to a user active in the last day" do
    in_window do
      @user.update_column(:last_active_at, 2.hours.ago)

      assert_no_difference "NotificationDelivery.count" do
        run_job
      end
    end
  end

  test "sends nothing to a user notified recently" do
    in_window do
      NotificationDelivery.create!(user: @user, campaign: "resurface", sent_at: 1.day.ago)

      assert_no_difference "NotificationDelivery.count" do
        run_job
      end
    end
  end

  test "sends nothing to a user who turned lifecycle notifications off" do
    @user.update_column(:lifecycle_notifications_enabled, false)

    in_window do
      assert_no_difference "NotificationDelivery.count" do
        run_job
      end
    end
  end

  test "sends nothing to a user with no active device" do
    @user.device_tokens.destroy_all

    in_window do
      assert_no_difference "NotificationDelivery.count" do
        run_job
      end
    end
  end

  test "sends at most one notification per user per run" do
    3.times do |i|
      recipe = @cookbook.recipes.create!(name: "Extra #{i}", user: @user, import_status: :completed)
      recipe.update_column(:created_at, 3.days.ago)
    end

    in_window do
      # Make StaleShoppingListCampaign genuinely eligible too, so there is a real second
      # candidate for the loop to (wrongly) fall through to if the one-per-user guard
      # were missing. ImportFollowUpCampaign runs first and will still win.
      @cookbook.shopping_list_items.update_all(created_at: 5.days.ago, updated_at: 5.days.ago)
      3.times do |i|
        item = @cookbook.shopping_list_items.create!(client_id: "extra-item-#{i}", name: "Item #{i}")
        item.update_columns(created_at: 5.days.ago, updated_at: 5.days.ago)
      end
      assert Notifications::StaleShoppingListCampaign.eligible_for(@user).present?,
        "expected the stale shopping list campaign to be genuinely eligible too"

      push_count = 0
      counting_push = lambda do |**_kwargs|
        push_count += 1
        Apns::Client::Result.new(ok?: true, status: 200, reason: nil)
      end

      Apns::Client.stub(:push, counting_push) do
        assert_difference "NotificationDelivery.count", 1 do
          EvaluateLifecycleNotificationsJob.perform_now
        end
      end

      assert_equal 1, push_count
      assert_equal "import_follow_up", NotificationDelivery.order(:id).last.campaign
    end
  end

  test "does not send the resurface campaign while view tracking has no client" do
    # ResurfaceCampaign infers "forgotten" from the absence of a view ping, and nothing
    # sends those yet, so it is deliberately out of CAMPAIGNS. Without that, this recipe
    # would be a resurface candidate: old enough, after VIEW_TRACKING_SINCE, never viewed.
    assert_not_includes EvaluateLifecycleNotificationsJob::CAMPAIGNS, Notifications::ResurfaceCampaign

    travel_to(Time.utc(2026, 9, 15)) do
      @recipe.update_column(:created_at, Time.utc(2026, 8, 30))
      assert Notifications::ResurfaceCampaign.eligible_for(@user).present?,
        "expected the recipe to be a genuine resurface candidate"
    end

    in_window do
      assert_no_difference "NotificationDelivery.count" do
        run_job
      end
    end
  end

  test "one user raising does not stop the others" do
    other = users(:two)
    # Anchor these ages to the same date the window helpers travel to, so the recipe
    # lands inside the import follow-up window rather than wherever the wall clock is.
    travel_to(Time.utc(2026, 9, 15)) do
      other.update_columns(
        lifecycle_notifications_enabled: true, time_zone: "Europe/Zurich", last_active_at: 10.days.ago
      )
      other.device_tokens.create!(token: "job-token-2", environment: "sandbox")
      other_recipe = cookbooks(:two_personal).recipes.create!(name: "Soup", user: other, import_status: :completed)
      other_recipe.update_column(:created_at, 3.days.ago)
    end

    # Capture the real method before stubbing so the non-raising branch calls the
    # original implementation instead of recursing into the stub that replaces it.
    real_eligible_for = Notifications::ImportFollowUpCampaign.method(:eligible_for)
    boom = ->(user) { user == @user ? raise("boom") : real_eligible_for.call(user) }

    in_window do
      Notifications::ImportFollowUpCampaign.stub(:eligible_for, boom) do
        assert_difference "NotificationDelivery.count", 1 do
          run_job
        end
      end

      assert_equal other, NotificationDelivery.order(:id).last.user
    end
  end
end

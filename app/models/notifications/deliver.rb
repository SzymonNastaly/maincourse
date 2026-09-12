module Notifications
  # Sends one candidate to one user: records the delivery, stamps the engagement row,
  # and pushes to every active device. Mirrors the token cleanup in
  # DeliverPendingNotificationJob.
  #
  # Discards candidates whose cookbook the user is no longer a member of — a campaign
  # may select through data (e.g. Recipe#user_id) that outlives cookbook membership,
  # and this is the one place that guards against pointing a notification at a
  # cookbook the user can no longer open.
  #
  # A delivery only "counts" once at least one push actually goes out: the row is
  # recorded and the recipe is marked suggested only after a successful push, so a
  # credentials outage or a user with no active devices does not burn the recipe
  # (permanently, for ImportFollowUpCampaign) or the user's frequency-cap slot on a
  # notification that never arrived. The push needs the delivery's real id for its
  # custom payload before we know whether it will succeed, so the row is created up
  # front and destroyed again via `ensure` if nothing went out. Provider failures are
  # isolated per installation by Push::Fanout, so one unavailable transport does not
  # prevent another device from receiving the notification.
  class Deliver
    def initialize(user:, candidate:)
      @user = user
      @candidate = candidate
    end

    def call
      return nil unless cookbook_still_accessible?

      delivery = record_delivery
      delivered = false
      begin
        delivered = push(delivery)
      ensure
        delivery.destroy! unless delivered
      end
      return nil unless delivered

      mark_suggested
      delivery
    end

    private

    def cookbook_still_accessible?
      return true if @candidate.cookbook.nil?

      @user.cookbooks.exists?(id: @candidate.cookbook.id)
    end

    def record_delivery
      NotificationDelivery.create!(
        user: @user,
        campaign: @candidate.campaign,
        recipe_id: @candidate.recipe&.id,
        cookbook_id: @candidate.cookbook&.id,
        sent_at: Time.current
      )
    end

    def mark_suggested
      return if @candidate.recipe.nil?

      RecipeEngagement.mark_suggested!(user: @user, recipe: @candidate.recipe)
    end

    # Returns true if at least one push actually went out.
    def push(delivery)
      alert = { title: @candidate.title, body: @candidate.body }
      custom = {
        campaign: @candidate.campaign,
        delivery_id: delivery.id,
        recipe_id: @candidate.recipe&.id,
        cookbook_id: @candidate.cookbook&.id
      }.compact

      Push::Fanout.call(device_tokens: @user.device_tokens.active, alert: alert, custom: custom)
    end
  end
end

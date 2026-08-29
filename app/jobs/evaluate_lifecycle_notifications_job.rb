# Runs hourly. For each user whose local time is inside the send window, evaluates the
# campaigns in priority order and sends at most one notification.
class EvaluateLifecycleNotificationsJob < ApplicationJob
  queue_as :default

  CAMPAIGNS = [
    Notifications::ImportFollowUpCampaign,
    Notifications::StaleShoppingListCampaign,
    Notifications::ResurfaceCampaign
  ].freeze

  # The hour, in the user's own time zone, when someone is deciding what to cook and can
  # still act on it.
  SEND_HOUR = 17
  # Someone already using the app does not need to be told to use the app.
  ACTIVE_SUPPRESSION = 24.hours
  # Frequency cap, shared across every campaign.
  FREQUENCY_CAP = 4.days

  def perform
    User.where(lifecycle_notifications_enabled: true).find_each do |user|
      evaluate(user)
    rescue StandardError => e
      Rails.logger.error("EvaluateLifecycleNotificationsJob failed for user #{user.id}: #{e.class}: #{e.message}")
    end
  end

  private

  def evaluate(user)
    return unless in_send_window?(user)
    return unless user.device_tokens.active.exists?
    return if recently_active?(user)
    return if recently_notified?(user)

    # Deliver may discard a candidate whose cookbook the user is no longer a member of
    # (see Notifications::Deliver). Treat that as if the campaign had returned nil and
    # fall through to the next one.
    CAMPAIGNS.each do |campaign|
      candidate = campaign.eligible_for(user)
      next if candidate.nil?

      delivery = Notifications::Deliver.new(user: user, candidate: candidate).call
      return unless delivery.nil?
    end
  end

  def in_send_window?(user)
    zone = ActiveSupport::TimeZone[user.time_zone.to_s]
    return false if zone.nil?

    Time.current.in_time_zone(zone).hour == SEND_HOUR
  end

  def recently_active?(user)
    user.last_active_at.present? && user.last_active_at > ACTIVE_SUPPRESSION.ago
  end

  def recently_notified?(user)
    user.notification_deliveries.sent_since(FREQUENCY_CAP.ago).exists?
  end
end

namespace :notifications do
  desc "Print which lifecycle notification each user would receive, without sending anything"
  task preview: :environment do
    campaigns = EvaluateLifecycleNotificationsJob::CAMPAIGNS

    User.find_each do |user|
      candidate = campaigns.lazy.filter_map { |campaign| campaign.eligible_for(user) }.first
      local_hour = ActiveSupport::TimeZone[user.time_zone.to_s]&.then { |zone| Time.current.in_time_zone(zone).hour }

      puts "user #{user.id} <#{user.email_address}>"
      puts "  time zone:  #{user.time_zone} (local hour #{local_hour || '?'})"
      puts "  enabled:    #{user.lifecycle_notifications_enabled}"
      puts "  devices:    #{user.device_tokens.active.count} active"
      puts "  last active: #{user.last_active_at || 'never'}"
      puts "  last sent:  #{user.notification_deliveries.order(:sent_at).last&.sent_at || 'never'}"

      if candidate
        puts "  -> #{candidate.campaign}: #{candidate.body}"
      else
        puts "  -> no campaign matches"
      end
      puts
    end
  end
end

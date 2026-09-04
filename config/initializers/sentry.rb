Sentry.init do |config|
  config.dsn = Rails.application.credentials.dig(:sentry, :dsn)
  config.breadcrumbs_logger = [ :active_support_logger, :http_logger ]
  config.traces_sample_rate = 0.1
  config.profiles_sample_rate = 0.1

  config.enabled_environments = %w[production]

  # GDPR: do not store PII
  config.send_default_pii = false

  config.before_send = lambda do |event, _hint|
    # Strip IP addresses — replace with a fixed placeholder
    event.user&.ip_address = nil
    event.request&.headers&.delete("Cookie")
    event.request&.headers&.delete("Authorization")
    event
  end
end

require Rails.root.join("lib/omniauth/strategies/main_course_apple")
require Rails.root.join("lib/oauth/android_apple_failure_endpoint")

credentials = Rails.application.credentials

google_client_id = credentials.dig(:google, :client_id)
google_client_secret = credentials.dig(:google, :client_secret)
apple_services_id = credentials.dig(:apple, :services_id)
apple_team_id = credentials.dig(:apple, :team_id)
apple_key_id = credentials.dig(:apple, :key_id)
apple_private_key = credentials.dig(:apple, :private_key)

google_configured = google_client_id.present? && google_client_secret.present?
apple_configured = [ apple_services_id, apple_team_id, apple_key_id, apple_private_key ].all?(&:present?)

Rails.application.config.x.oauth.google_enabled = google_configured
Rails.application.config.x.oauth.apple_enabled = apple_configured

# Register test strategies even before local provider credentials are installed.
if google_configured || Rails.env.test?
  Rails.application.config.middleware.use OmniAuth::Builder do
    provider :google_oauth2,
      google_client_id || "test-google-client-id",
      google_client_secret || "test-google-client-secret",
      scope: "email,profile",
      prompt: "select_account",
      access_type: "online"
  end
end

if apple_configured || Rails.env.test?
  Rails.application.config.middleware.use OmniAuth::Builder do
    provider OmniAuth::Strategies::MainCourseApple,
      apple_services_id || "test-apple-services-id",
      "",
      scope: "email name",
      team_id: apple_team_id || "TESTTEAMID",
      key_id: apple_key_id || "TESTKEYID",
      pem: apple_private_key || "unused-in-test",
      authorized_client_ids: [ apple_services_id || "test-apple-services-id" ]
  end
end

OmniAuth.config.allowed_request_methods = [ :post ]
OmniAuth.config.logger = Rails.logger
OmniAuth.config.on_failure = Oauth::AndroidAppleFailureEndpoint
if Rails.env.production?
  OmniAuth.config.full_host = "https://#{Rails.application.config.x.canonical_host}"
end

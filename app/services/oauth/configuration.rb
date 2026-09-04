module Oauth
  module Configuration
    module_function

    def google_client_ids
      [ credentials.dig(:google, :client_id), credentials.dig(:google, :ios_client_id) ].compact_blank.uniq
    end

    def apple_bundle_id
      fetch!(:apple, :bundle_id)
    end

    def apple_services_id
      fetch!(:apple, :services_id)
    end

    def apple_team_id
      fetch!(:apple, :team_id)
    end

    def apple_key_id
      fetch!(:apple, :key_id)
    end

    def apple_private_key
      fetch!(:apple, :private_key)
    end

    def fetch!(*keys)
      credentials.dig(*keys).presence || raise(UnavailableError, "Missing OAuth credential: #{keys.join(".")}")
    end

    def credentials
      Rails.application.credentials
    end
  end
end

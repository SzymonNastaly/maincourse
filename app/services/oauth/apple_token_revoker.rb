module Oauth
  class AppleTokenRevoker
    def self.call(user)
      user.identities.apple.find_each do |identity|
        begin
          identity.apple_refresh_tokens.each do |client_id, refresh_token|
            revoke(refresh_token:, client_id:, context: { identity_id: identity.id })
          end
        rescue StandardError => error
          Rails.error.report(error, handled: true, context: { identity_id: identity.id })
        end
      end
    end

    def self.revoke(refresh_token:, client_id:, context: {})
      AppleClient.new.revoke!(
        refresh_token:,
        client_id:
      )
    rescue StandardError => error
      Rails.error.report(error, handled: true, context:)
    end
  end
end

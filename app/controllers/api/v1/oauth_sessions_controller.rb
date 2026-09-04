module Api
  module V1
    class OauthSessionsController < BaseController
      skip_before_action :authenticate_with_token!, only: :create
      unless Rails.env.local?
        rate_limit to: 10, within: 3.minutes, only: :create, with: -> {
          render json: { error: "Too many login attempts. Try again later." }, status: :too_many_requests
        }
      end

      def create
        identity_persisted = false
        raise Oauth::Error, "Missing OAuth nonce" if params[:nonce].blank?

        identity = case params[:provider]
        when "apple" then apple_identity
        when "google" then google_identity
        else raise Oauth::Error, "Unsupported OAuth provider"
        end

        user = Identity.authenticate!(**identity)
        identity_persisted = true
        OnboardingResponse.link_to_user!(device_id: params[:onboarding_device_id], user: user)
        render_authenticated_user(user)
      rescue Oauth::LinkRequiredError
        render json: {
          error: "An account already exists for this email. Sign in with your password instead.",
          error_code: "account_link_required"
        }, status: :conflict
      rescue Oauth::UnavailableError => error
        Rails.error.report(error, handled: true, context: { provider: params[:provider] })
        render json: {
          error: "OAuth provider is temporarily unavailable",
          error_code: "oauth_unavailable"
        }, status: :service_unavailable
      rescue Oauth::Error, ActiveRecord::RecordInvalid, ActiveRecord::RecordNotUnique => error
        Rails.logger.info("OAuth login failed: #{error.message}")
        render json: {
          error: "Could not authenticate with that provider",
          error_code: "oauth_failed"
        }, status: :unauthorized
      ensure
        revoke_pending_apple_token unless identity_persisted
      end

      private
        def google_identity
          Oauth::IdTokenVerifier.verify!(
            provider: "google",
            token: params[:id_token],
            audiences: Oauth::Configuration.google_client_ids,
            nonce: params[:nonce]
          ).merge(provider: "google")
        end

        def apple_identity
          client_id = Oauth::Configuration.apple_bundle_id
          expected_nonce = Digest::SHA256.hexdigest(params[:nonce].to_s)
          client_identity = Oauth::IdTokenVerifier.verify!(
            provider: "apple",
            token: params[:id_token],
            audiences: [ client_id ],
            nonce: expected_nonce
          )
          token_response = Oauth::AppleClient.new.exchange_code!(
            code: params[:authorization_code],
            client_id:
          )
          @pending_apple_refresh_token = token_response["refresh_token"]
          @pending_apple_client_id = client_id
          server_identity = Oauth::IdTokenVerifier.verify!(
            provider: "apple",
            token: token_response["id_token"],
            audiences: [ client_id ],
            nonce: nil
          )
          raise Oauth::Error, "Apple identity mismatch" unless client_identity[:uid] == server_identity[:uid]
          ensure_apple_refresh_token!(server_identity[:uid], token_response["refresh_token"])

          server_identity.merge(
            provider: "apple",
            name: params[:name],
            apple_refresh_token: token_response["refresh_token"],
            apple_client_id: client_id
          )
        end

        def ensure_apple_refresh_token!(uid, refresh_token)
          return if refresh_token.present?
          return if Identity.find_by(provider: "apple", uid:)&.apple_refresh_token_for(Oauth::Configuration.apple_bundle_id).present?

          raise Oauth::Error, "Apple did not return a refresh token"
        end

        def revoke_pending_apple_token
          refresh_token = @pending_apple_refresh_token
          @pending_apple_refresh_token = nil
          return if refresh_token.blank?

          Oauth::AppleTokenRevoker.revoke(
            refresh_token:,
            client_id: @pending_apple_client_id
          )
        end
    end
  end
end

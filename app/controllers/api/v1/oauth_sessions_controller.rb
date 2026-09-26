module Api
  module V1
    class OauthSessionsController < BaseController
      skip_before_action :authenticate_with_token!, only: :create
      unless Rails.env.local?
        rate_limit to: 10, within: 3.minutes, only: :create, with: -> {
          render_api_error "rate_limited", error: "Too many login attempts. Try again later.", status: :too_many_requests
        }
      end

      def create
        account_creation_intent_present = params.key?(:allow_account_creation)
        identity_persisted = false
        raise Oauth::Error, "Missing OAuth nonce" if params[:nonce].blank?

        identity = case params[:provider]
        when "apple" then apple_identity
        when "google" then google_identity
        else raise Oauth::Error, "Unsupported OAuth provider"
        end

        user = Identity.authenticate!(
          **identity,
          allow_account_creation: params[:allow_account_creation] == true
        )
        identity_persisted = true
        OnboardingResponse.link_to_user!(device_id: params[:onboarding_device_id], user: user)
        render_authenticated_user(user)
      rescue Oauth::AccountCreationConfirmationRequiredError
        if account_creation_intent_present
          render_api_error "apple_account_creation_confirmation_required", error: "This Apple sign-in isn't linked to a MainCourse account. Confirm creating a new account to continue.", status: :conflict
        else
          render_api_error "app_update_required", error: "Update the app to create a new Apple account, or use the prior sign-in method to access an existing account.", status: :unprocessable_entity
        end
      rescue Oauth::LinkRequiredError
        render_api_error "account_link_required", error: "An account already exists for this email. Sign in with your password instead.", status: :conflict
      rescue Oauth::UnavailableError => error
        Rails.error.report(error, handled: true, context: { provider: params[:provider] })
        render_api_error "oauth_unavailable", error: "OAuth provider is temporarily unavailable", status: :service_unavailable
      rescue Oauth::Error, ActiveRecord::RecordInvalid, ActiveRecord::RecordNotUnique => error
        Rails.logger.info("OAuth login failed: #{error.message}")
        render_api_error "oauth_failed", error: "Could not authenticate with that provider", status: :unauthorized
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

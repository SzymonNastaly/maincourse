class OmniauthCallbacksController < ApplicationController
  allow_unauthenticated_access
  skip_forgery_protection only: :create

  def create
    provider = refresh_token = apple_client_id = nil
    identity_persisted = false
    auth = request.env.fetch("omniauth.auth")
    provider = normalized_provider(auth.provider)
    refresh_token = auth.credentials.refresh_token if provider == "apple"
    apple_client_id = Oauth::Configuration.apple_services_id if provider == "apple"
    ensure_apple_refresh_token!(auth.uid, refresh_token) if provider == "apple"

    user = Identity.authenticate!(
      provider:,
      uid: auth.uid,
      email: auth.info.email,
      email_verified: auth.info.email_verified,
      name: auth.info.name,
      apple_refresh_token: refresh_token,
      apple_client_id:
    )
    identity_persisted = true

    start_new_session_for(user)
    redirect_to after_authentication_url
  rescue Oauth::LinkRequiredError
    redirect_to new_session_path,
      alert: "An account already exists for this email. Sign in with your password instead."
  rescue Oauth::UnavailableError => error
    Rails.error.report(error, handled: true, context: { provider: })
    redirect_to new_session_path, alert: "That sign-in provider is temporarily unavailable. Please try again later."
  rescue KeyError, Oauth::Error, ActiveRecord::RecordInvalid, ActiveRecord::RecordNotUnique => error
    Rails.logger.info("OAuth callback failed: #{error.message}")
    redirect_to new_session_path, alert: "We couldn't sign you in with that provider. Please try again."
  ensure
    if provider == "apple" && refresh_token.present? && apple_client_id.present? && !identity_persisted
      Oauth::AppleTokenRevoker.revoke(refresh_token:, client_id: apple_client_id)
    end
  end

  def failure
    redirect_to new_session_path, alert: "We couldn't sign you in with that provider. Please try again."
  end

  private
    def normalized_provider(provider)
      return "google" if provider == "google_oauth2"
      return "apple" if provider == "apple"

      raise Oauth::Error, "Unsupported OAuth provider"
    end

    def ensure_apple_refresh_token!(uid, refresh_token)
      return if refresh_token.present?
      return if Identity.find_by(provider: "apple", uid:)&.apple_refresh_token_for(Oauth::Configuration.apple_services_id).present?

      raise Oauth::Error, "Apple did not return a refresh token"
    end
end

class OmniauthCallbacksController < ApplicationController
  allow_unauthenticated_access
  skip_forgery_protection only: :create
  layout "authentication", only: :confirm_account_creation

  def create
    auth = request.env.fetch("omniauth.auth")
    provider = normalized_provider(auth.provider)
    android_transaction = request.env.fetch("omniauth.params", {})["android_transaction"]
    if provider == "apple" && android_transaction.present?
      return complete_android_apple_handoff(auth, android_transaction)
    end

    refresh_token = apple_client_id = nil
    identity_persisted = false
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
      apple_client_id:,
      allow_account_creation: request.env.fetch("omniauth.params", {})["allow_account_creation"] == "true"
    )
    identity_persisted = true

    start_new_session_for(user)
    redirect_to after_authentication_url
  rescue Oauth::AccountCreationConfirmationRequiredError
    redirect_to confirm_apple_account_creation_path
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

  def confirm_account_creation
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

    def complete_android_apple_handoff(auth, handle)
      prevent_android_response_storage
      transaction = AppleAuthTransaction.find_by_handle(handle)
      return render_android_error unless transaction

      refresh_token = auth.credentials.refresh_token
      apple_client_id = nil
      identity_persisted = false
      confirmation_required = false
      exchange_code = nil

      begin
        apple_client_id = Oauth::Configuration.apple_services_id
        transaction.with_lock do
          transaction.authorizable!
          begin
            user = Identity.authenticate!(
              provider: "apple",
              uid: auth.uid,
              email: auth.info.email,
              email_verified: auth.info.email_verified,
              name: auth.info.name,
              apple_refresh_token: refresh_token,
              apple_client_id:,
              allow_account_creation: transaction.confirmation_required? &&
                request.env.fetch("omniauth.params", {})["allow_account_creation"] == "true"
            )
            ensure_apple_refresh_token!(auth.uid, refresh_token)
            exchange_code = transaction.authorize!(user:)
            identity_persisted = true
          rescue Oauth::AccountCreationConfirmationRequiredError
            transaction.mark_confirmation_required! unless transaction.confirmation_required?
            confirmation_required = true
          end
        end

        if confirmation_required
          redirect_to android_apple_confirm_account_creation_path(transaction_id: handle)
        else
          redirect_to android_return_uri(transaction, handle, exchange_code:), allow_other_host: true
        end
      rescue AppleAuthTransaction::AuthorizationStateError
        error = transaction.expires_at <= Time.current ? "transaction_expired" : "transaction_unavailable"
        redirect_to android_return_uri(transaction, handle, error:), allow_other_host: true
      rescue Oauth::LinkRequiredError
        redirect_to android_return_uri(transaction, handle, error: "account_link_required"), allow_other_host: true
      rescue Oauth::UnavailableError => error
        Rails.error.report(error, handled: true, context: { provider: "apple" })
        redirect_to android_return_uri(transaction, handle, error: "provider_unavailable"), allow_other_host: true
      rescue KeyError, Oauth::Error, ActiveRecord::RecordInvalid, ActiveRecord::RecordNotUnique
        redirect_to android_return_uri(transaction, handle, error: "authentication_failed"), allow_other_host: true
      ensure
        if refresh_token.present? && apple_client_id.present? && !identity_persisted
          Oauth::AppleTokenRevoker.revoke(refresh_token:, client_id: apple_client_id)
        end
      end
    end

    def android_return_uri(transaction, handle, exchange_code: nil, error: nil)
      if error && !AppleAuthTransaction::HANDOFF_ERROR_CODES.include?(error)
        raise ArgumentError, "Unknown Android Apple handoff error"
      end

      uri = URI.parse(transaction.return_uri)
      query = { transaction_id: handle }
      query[:exchange_code] = exchange_code if exchange_code
      query[:error] = error if error
      uri.query = Rack::Utils.build_query(query)
      uri.to_s
    end

    def render_android_error
      @heading = "Apple sign-in link unavailable"
      @message = "This Apple sign-in link is invalid or no longer available. Return to MainCourse and start again."
      render "android/apple_authentications/error", status: :bad_request, layout: "authentication"
    end

    def prevent_android_response_storage
      response.headers["Cache-Control"] = "no-store"
      response.headers["Referrer-Policy"] = "no-referrer"
    end
end

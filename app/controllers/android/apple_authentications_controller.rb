module Android
  class AppleAuthenticationsController < ApplicationController
    allow_unauthenticated_access
    before_action :prevent_response_storage
    layout "authentication"

    def sign_in
      return render_unavailable unless load_transaction
      return redirect_with_error("transaction_expired") if expired?
      return redirect_with_error("transaction_unavailable") unless authorizable?
      return redirect_to confirmation_path if @transaction.confirmation_required?

      @setup_issue = if !request.ssl?
        :https
      elsif !Rails.application.config.x.oauth.apple_enabled
        :provider
      elsif Rails.env.production? && request.host != Rails.application.config.x.canonical_host
        :host
      end

      render status: :service_unavailable if @setup_issue == :provider
    end

    def confirm_account_creation
      return render_unavailable unless load_transaction
      return redirect_with_error("transaction_expired") if expired?
      return redirect_with_error("transaction_unavailable") unless authorizable?
      return render_unavailable unless @transaction.confirmation_required?

      @setup_issue = if !request.ssl?
        :https
      elsif !Rails.application.config.x.oauth.apple_enabled
        :provider
      elsif Rails.env.production? && request.host != Rails.application.config.x.canonical_host
        :host
      end

      render status: :service_unavailable if @setup_issue == :provider
    end

    def cancel
      return render_unavailable unless load_transaction

      redirect_with_error("cancelled")
    end

    def fallback
      @heading = "Return to MainCourse"
      @message = "Open the MainCourse app to finish signing in. If you keep seeing this page, App Links are not configured for this installation yet."
      render :error
    end

    private
      def load_transaction
        @transaction_id = params[:transaction_id]
        @transaction = AppleAuthTransaction.find_by_handle(@transaction_id)
      end

      def expired?
        @transaction.expires_at <= Time.current
      end

      def authorizable?
        @transaction.authorizable!
        true
      rescue AppleAuthTransaction::AuthorizationStateError
        false
      end

      def render_unavailable
        @heading = "Apple sign-in link unavailable"
        @message = "This Apple sign-in link is invalid or no longer available. Return to MainCourse and start again."
        render :error, status: :bad_request
      end

      def redirect_with_error(error)
        redirect_to return_uri(error:), allow_other_host: true
      end

      def confirmation_path
        android_apple_confirm_account_creation_path(transaction_id: @transaction_id)
      end

      def return_uri(error:)
        raise ArgumentError unless AppleAuthTransaction::HANDOFF_ERROR_CODES.include?(error)

        uri = URI.parse(@transaction.return_uri)
        uri.query = Rack::Utils.build_query(transaction_id: @transaction_id, error:)
        uri.to_s
      end

      def prevent_response_storage
        response.headers["Cache-Control"] = "no-store"
        response.headers["Referrer-Policy"] = "no-referrer"
      end
  end
end

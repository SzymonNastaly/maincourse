module Api
  module V1
    class AppleAuthTransactionsController < BaseController
      skip_before_action :authenticate_with_token!
      skip_before_action :set_current_cookbook!
      before_action :prevent_response_storage

      unless Rails.env.local?
        rate_limit to: 10, within: 3.minutes, only: [ :create, :exchange ], with: -> {
          render json: { error: "Too many Apple sign-in attempts. Try again later." }, status: :too_many_requests
        }
      end

      def create
        unless Rails.application.config.x.oauth.apple_enabled
          return render json: { error: "Apple sign-in is temporarily unavailable" }, status: :service_unavailable
        end

        transaction, raw_handle = AppleAuthTransaction.start!(
          code_challenge: params[:code_challenge],
          callback: params[:callback]
        )
        render json: {
          transaction_id: raw_handle,
          browser_url: "#{request.base_url}/android/apple/sign_in?#{Rack::Utils.build_query(transaction_id: raw_handle)}",
          expires_at: transaction.expires_at
        }, status: :created
      rescue AppleAuthTransaction::InvalidStartError, ActiveRecord::RecordInvalid, ActiveRecord::RecordNotUnique
        render json: { error: "Invalid Apple authentication request" }, status: :bad_request
      end

      def exchange
        transaction = AppleAuthTransaction.find_by_handle(params[:transaction_id])
        raise AppleAuthTransaction::ExchangeError unless transaction

        payload = transaction.exchange!(
          exchange_code: params[:exchange_code],
          code_verifier: params[:code_verifier],
          device_name: params[:device_name],
          onboarding_device_id: params[:onboarding_device_id]
        )
        render json: payload, status: :created
      rescue AppleAuthTransaction::ExchangeError
        render json: { error: "Could not authenticate with Apple" }, status: :bad_request
      end

      private
        def prevent_response_storage
          response.headers["Cache-Control"] = "no-store"
          response.headers["Referrer-Policy"] = "no-referrer"
        end
    end
  end
end

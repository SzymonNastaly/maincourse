module Api
  module V1
    class BaseController < ActionController::API
      include ActionController::HttpAuthentication::Token::ControllerMethods
      include ApiErrorRendering

      before_action :authenticate_with_token!
      before_action :set_current_cookbook!
      after_action :record_user_activity

      rescue_from ActionController::ParameterMissing do
        render_api_error "invalid_request", error: "Invalid request", status: :bad_request
      end
      rescue_from ActiveRecord::RecordInvalid do |error|
        render_validation_errors(error.record)
      end

      private

      # Keep legacy prose for installed clients; new clients render codes locally.
      # Do not expose validation values (passwords, tokens, etc.) in the metadata.
      def render_validation_errors(record)
        render_api_error "validation_failed", errors: record.errors.full_messages,
          error_details: ApiErrorContract.validation_details(record.errors), status: :unprocessable_entity
      end

      attr_reader :current_user, :current_api_token, :current_cookbook

      def authenticate_with_token!
        authenticate_with_http_token do |token, _options|
          @current_api_token = ApiToken.find_by_raw_token(token)
          if @current_api_token
            @current_api_token.touch_last_used!
            @current_user = @current_api_token.user
          end
        end

        render_unauthorized unless current_user
      end

      def set_current_cookbook!
        return unless current_user

        cookbook_id = request.headers["X-Cookbook-Id"]
        if cookbook_id.present?
          @current_cookbook = current_user.cookbooks.find_by(id: cookbook_id)
          render_api_error "forbidden", error: "Forbidden", status: :forbidden unless @current_cookbook
        else
          @current_cookbook = current_user.personal_cookbook
        end
      end

      def render_unauthorized
        render_api_error "unauthorized", error: "Unauthorized", status: :unauthorized
      end

      def render_authenticated_user(user)
        token_record, raw_token = ApiToken.generate_for(user, name: params[:device_name])
        render json: {
          token: raw_token,
          expires_at: token_record.expires_at,
          user: {
            id: user.id,
            name: user.name,
            email: user.email_address,
            lifecycle_notifications_enabled: user.lifecycle_notifications_enabled
          }
        }, status: :created
      end

      def record_user_activity
        current_user.touch_last_active! if current_user && !current_user.destroyed?
      end
    end
  end
end

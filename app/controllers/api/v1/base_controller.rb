module Api
  module V1
    class BaseController < ActionController::API
      include ActionController::HttpAuthentication::Token::ControllerMethods

      before_action :authenticate_with_token!
      before_action :set_current_cookbook!
      after_action :record_user_activity

      private

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
          render json: { error: "Forbidden" }, status: :forbidden unless @current_cookbook
        else
          @current_cookbook = current_user.personal_cookbook
        end
      end

      def render_unauthorized
        render json: { error: "Unauthorized" }, status: :unauthorized
      end

      def record_user_activity
        current_user.touch_last_active! if current_user && !current_user.destroyed?
      end
    end
  end
end

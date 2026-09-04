module Api
  module V1
    class SessionsController < BaseController
      skip_before_action :authenticate_with_token!, only: :create
      unless Rails.env.local?
        rate_limit to: 10, within: 3.minutes, only: :create, with: -> {
          render json: { error: "Too many login attempts. Try again later." }, status: :too_many_requests
        }
      end

      def create
        user = User.authenticate_by(
          email_address: params[:email],
          password: params[:password]
        )

        if user
          OnboardingResponse.link_to_user!(device_id: params[:onboarding_device_id], user: user)
          render_authenticated_user(user)
        else
          render json: { error: "Invalid email or password" }, status: :unauthorized
        end
      end

      def destroy
        current_api_token.revoke!
        render json: { message: "Logged out successfully" }, status: :ok
      end
    end
  end
end

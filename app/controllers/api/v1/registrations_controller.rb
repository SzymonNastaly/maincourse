module Api
  module V1
    class RegistrationsController < BaseController
      skip_before_action :authenticate_with_token!, only: :create
      unless Rails.env.local?
        rate_limit to: 10, within: 3.minutes, only: :create, with: -> {
          render json: { error: "Too many signup attempts. Try again later." }, status: :too_many_requests
        }
      end

      def create
        user = User.new(
          name: params[:name],
          email_address: params[:email],
          password: params[:password],
          password_confirmation: params[:password_confirmation]
        )

        if user.save
          OnboardingResponse.link_to_user!(device_id: params[:onboarding_device_id], user: user)
          render_authenticated_user(user)
        else
          render json: { errors: user.errors.full_messages }, status: :unprocessable_entity
        end
      end
    end
  end
end

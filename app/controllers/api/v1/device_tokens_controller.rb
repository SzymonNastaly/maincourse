module Api
  module V1
    class DeviceTokensController < BaseController
      skip_before_action :set_current_cookbook!

      def create
        token = params[:token].to_s
        environment = params[:environment].to_s.presence || "production"

        unless DeviceToken::ENVIRONMENTS.include?(environment)
          return render json: { error: "Invalid environment" }, status: :unprocessable_entity
        end

        if token.blank?
          return render json: { error: "token is required" }, status: :unprocessable_entity
        end

        store_time_zone(params[:time_zone])

        record = DeviceToken.register!(user: current_user, token: token, environment: environment)
        render json: { id: record.id, token: record.token, environment: record.environment }, status: :created
      end

      def destroy
        DeviceToken.where(user_id: current_user.id, token: params[:token]).destroy_all
        head :no_content
      end

      private

      # The client sends an IANA identifier (e.g. "Europe/Zurich"). Unknown values are
      # ignored rather than rejected — a bad time zone should not block push
      # registration, it just means the user keeps the UTC default.
      def store_time_zone(value)
        identifier = value.to_s.strip
        return if identifier.blank?
        return unless ActiveSupport::TimeZone[identifier]

        current_user.update_column(:time_zone, identifier)
      end
    end
  end
end

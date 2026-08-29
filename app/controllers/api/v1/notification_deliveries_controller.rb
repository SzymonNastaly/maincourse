module Api
  module V1
    # The client posts here when a lifecycle notification is tapped, and again with
    # action_taken if the user then does what the notification asked.
    class NotificationDeliveriesController < BaseController
      skip_before_action :set_current_cookbook!

      def opened
        delivery = current_user.notification_deliveries.find(params[:id])
        delivery.opened_at ||= Time.current
        delivery.action_taken = params[:action_taken] if params[:action_taken].present?
        delivery.save!
        head :no_content
      rescue ActiveRecord::RecordNotFound
        render json: { error: "Notification delivery not found" }, status: :not_found
      end
    end
  end
end

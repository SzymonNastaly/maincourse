module Api
  module V1
    class AccountsController < BaseController
      def update
        if current_user.update(account_params)
          render json: {
            user: {
              id: current_user.id,
              name: current_user.name,
              email: current_user.email_address,
              lifecycle_notifications_enabled: current_user.lifecycle_notifications_enabled
            }
          }, status: :ok
        else
          render json: { errors: current_user.errors.full_messages }, status: :unprocessable_entity
        end
      end

      def destroy
        current_user.destroy!
        head :no_content
      end

      private

      def account_params
        params.expect(user: [ :name, :lifecycle_notifications_enabled ])
      end
    end
  end
end

module Api
  module V1
    class AccountsController < BaseController
      def update
        # A closed set of languages, not free-form input: reject it as a request
        # error rather than adding a validation field to the error contract.
        if account_params.key?(:communication_language) &&
            I18n.available_locales.map(&:to_s).exclude?(account_params[:communication_language])
          return render_api_error "invalid_request", error: "Invalid communication language", status: :unprocessable_entity
        end

        if current_user.update(account_params)
          render json: {
            user: {
              id: current_user.id,
              name: current_user.name,
              email: current_user.email_address,
              lifecycle_notifications_enabled: current_user.lifecycle_notifications_enabled,
              communication_language: current_user.communication_language
            }
          }, status: :ok
        else
          render_validation_errors(current_user)
        end
      end

      def destroy
        Oauth::AppleTokenRevoker.call(current_user)
        current_user.destroy!
        head :no_content
      end

      private

      def account_params
        params.expect(user: [ :name, :lifecycle_notifications_enabled, :communication_language ])
      end
    end
  end
end

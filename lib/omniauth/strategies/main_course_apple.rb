require "omniauth/strategies/apple"

module OmniAuth
  module Strategies
    class MainCourseApple < Apple
      option :name, "apple"

      def request_phase
        response = super
        if request.GET["android_transaction"].to_s.present?
          response[1]["Cache-Control"] = "no-store"
          response[1]["Referrer-Policy"] = "no-referrer"
        end
        response
      end

      def callback_phase
        android_handoff = android_handoff?
        super
      ensure
        if android_handoff
          session.delete("omniauth.state")
          session.delete("omniauth.nonce")
        end
      end

      private
        def verify_claims!(id_token)
          super
          verify_nonce!(id_token) if android_handoff? && !id_token[:nonce_supported]
        end

        def android_handoff?
          env.fetch("omniauth.params", {})["android_transaction"].to_s.present?
        end
    end
  end
end

module Oauth
  class AndroidAppleFailureEndpoint
    CANCELLATION_ERRORS = %w[access_denied user_cancelled_authorize].freeze

    def self.call(env)
      strategy = env["omniauth.error.strategy"]
      request_phase = strategy&.on_request_path?
      apple_request_phase = strategy&.name == "apple" && request_phase
      handle = captured_handle(env, apple_request_phase:)
      clear_request_phase_session(env, apple: apple_request_phase) if request_phase
      transaction = AppleAuthTransaction.find_by_handle(handle) if strategy&.name == "apple"

      return default_failure(env, strategy:, request_phase:) unless transaction

      error_type = env["omniauth.error.type"].to_s
      error = CANCELLATION_ERRORS.include?(error_type) ? "cancelled" : "authentication_failed"
      raise ArgumentError unless AppleAuthTransaction::HANDOFF_ERROR_CODES.include?(error)

      uri = URI.parse(transaction.return_uri)
      uri.query = Rack::Utils.build_query(transaction_id: handle, error: error)
      Rack::Response.new(
        [ "Redirecting to MainCourse..." ],
        302,
        "Location" => uri.to_s,
        "Cache-Control" => "no-store",
        "Referrer-Policy" => "no-referrer"
      ).finish
    end

    def self.captured_handle(env, apple_request_phase:)
      return env["omniauth.params"]["android_transaction"] if env["omniauth.params"]
      return unless apple_request_phase

      env.fetch("rack.session", {}).fetch("omniauth.params", {})["android_transaction"]
    end
    private_class_method :captured_handle

    def self.clear_request_phase_session(env, apple:)
      session = env["rack.session"]
      return unless session

      session.delete("omniauth.params")
      if apple
        session.delete("omniauth.state")
        session.delete("omniauth.nonce")
      end
    end
    private_class_method :clear_request_phase_session

    def self.default_failure(env, strategy:, request_phase:)
      env["omniauth.error.type"] = :authentication_failed if strategy&.name == "apple" && request_phase
      response = OmniAuth::FailureEndpoint.call(env)
      if strategy&.name == "apple"
        response[1]["Cache-Control"] = "no-store"
        response[1]["Referrer-Policy"] = "no-referrer"
      end
      response
    end
    private_class_method :default_failure
  end
end

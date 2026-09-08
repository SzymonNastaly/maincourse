module Oauth
  class AndroidAppleFailureEndpoint
    CANCELLATION_ERRORS = %w[access_denied user_cancelled_authorize].freeze

    def self.call(env)
      strategy = env["omniauth.error.strategy"]
      handle = env.fetch("omniauth.params", {})["android_transaction"]
      transaction = AppleAuthTransaction.find_by_handle(handle) if strategy&.name == "apple"

      return OmniAuth::FailureEndpoint.call(env) unless transaction

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
  end
end

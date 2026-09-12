module Push
  # Selects the transport for one app installation and normalizes provider-specific
  # responses for the fan-out layer.
  class Dispatcher
    Result = Data.define(:ok?, :invalid_token?, :status, :reason)

    APNS_INVALID_TOKEN_REASONS = %w[
      BadDeviceToken Unregistered DeviceTokenNotForTopic TopicDisallowed
    ].freeze
    FCM_INVALID_TOKEN_REASONS = %w[UNREGISTERED INVALID_ARGUMENT].freeze

    def self.push(device_token:, alert:, custom:)
      case device_token.provider
      when "apns"
        result = Apns::Client.push(
          token: device_token.token,
          environment: device_token.environment,
          aps: { alert: alert },
          custom: custom
        )
        Result.new(
          ok?: result.ok?,
          invalid_token?: !result.ok? && APNS_INVALID_TOKEN_REASONS.include?(result.reason),
          status: result.status,
          reason: result.reason
        )
      when "fcm"
        result = Fcm::Client.push(token: device_token.token, alert: alert, custom: custom)
        Result.new(
          ok?: result.ok?,
          invalid_token?: !result.ok? && FCM_INVALID_TOKEN_REASONS.include?(result.reason),
          status: result.status,
          reason: result.reason
        )
      else
        raise ArgumentError, "Unsupported push provider: #{device_token.provider.inspect}"
      end
    end
  end
end

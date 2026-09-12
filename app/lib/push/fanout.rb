module Push
  # Delivers one logical notification to every active app installation in the
  # relation. A failure on one device does not prevent delivery to the others.
  class Fanout
    def self.call(device_tokens:, alert:, custom:)
      delivered = false

      device_tokens.find_each do |device_token|
        begin
          result = Dispatcher.push(device_token: device_token, alert: alert, custom: custom)
          delivered ||= result.ok?
          device_token.destroy! if result.invalid_token?
        rescue StandardError => error
          Rails.logger.error(
            "Push delivery failed for device token #{device_token.id}: #{error.class}: #{error.message}"
          )
        end
      end

      delivered
    end
  end
end

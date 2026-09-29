module Push
  # Delivers one logical notification to every active app installation in the
  # relation. A failure on one device does not prevent delivery to the others.
  #
  # The block builds the alert (`{ title:, body: }`) and runs once per installation
  # language, inside that locale, so each device receives the text in its own app
  # language. It must be free of side effects.
  class Fanout
    def self.call(device_tokens:, custom:, &build_alert)
      delivered = false
      alerts = Hash.new { |cache, locale| cache[locale] = I18n.with_locale(locale, &build_alert) }

      device_tokens.find_each do |device_token|
        begin
          result = Dispatcher.push(device_token: device_token, alert: alerts[device_token.locale], custom: custom)
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

require "faraday"
require "googleauth"
require "stringio"

module Fcm
  # Minimal FCM HTTP v1 client. Firebase has no official Ruby Admin SDK, so this
  # combines Google's supported OAuth library with the app's existing HTTP client.
  class Client
    Result = Data.define(:ok?, :status, :reason)

    SCOPE = "https://www.googleapis.com/auth/firebase.messaging".freeze
    REMINDERS_CHANNEL = "recipe_reminders".freeze
    ACTIVITY_CHANNEL = "cookbook_activity".freeze

    class MissingCredentialsError < StandardError; end

    class << self
      def push(token:, alert:, custom: {})
        response = connection.post(send_path) do |request|
          request.headers["Authorization"] = "Bearer #{access_token}"
          request.headers["Content-Type"] = "application/json"
          request.body = JSON.generate(message_payload(token: token, alert: alert, custom: custom))
        end

        if response.success?
          Result.new(ok?: true, status: response.status, reason: nil)
        else
          Result.new(ok?: false, status: response.status, reason: error_reason(response.body))
        end
      rescue Faraday::Error => error
        Result.new(ok?: false, status: nil, reason: error.class.name)
      end

      def reset!
        @authorizer = nil
        @connection = nil
      end

      private

      def message_payload(token:, alert:, custom:)
        data = custom.compact.transform_keys(&:to_s).transform_values(&:to_s)
        channel_id = data.key?("campaign") ? REMINDERS_CHANNEL : ACTIVITY_CHANNEL

        {
          message: {
            fid: token,
            notification: {
              title: alert.fetch(:title),
              body: alert.fetch(:body)
            },
            data: data,
            android: {
              notification: {
                channel_id: channel_id,
                sound: "default"
              }
            }
          }
        }
      end

      def connection
        @connection ||= Faraday.new(url: "https://fcm.googleapis.com")
      end

      def send_path
        "/v1/projects/#{credentials.fetch(:project_id)}/messages:send"
      end

      def access_token
        authorizer.fetch_access_token!.fetch("access_token")
      end

      def authorizer
        @authorizer ||= Google::Auth::ServiceAccountCredentials.make_creds(
          json_key_io: StringIO.new(JSON.generate(credentials.fetch(:service_account))),
          scope: SCOPE
        )
      end

      def credentials
        value = Rails.application.credentials.firebase
        if value.blank?
          raise MissingCredentialsError, "Rails.application.credentials.firebase is not configured"
        end

        config = value.is_a?(Hash) ? value.with_indifferent_access : value.to_h.with_indifferent_access
        service_account = JSON.parse(config.fetch(:service_account_json))
        unless service_account.is_a?(Hash)
          raise MissingCredentialsError, "firebase.service_account_json must contain a JSON object"
        end

        {
          project_id: config.fetch(:project_id),
          service_account: service_account.with_indifferent_access
        }.with_indifferent_access
      rescue JSON::ParserError, KeyError, TypeError => error
        raise MissingCredentialsError,
          "Rails credentials must contain firebase.project_id and valid firebase.service_account_json",
          cause: error
      end

      def error_reason(body)
        error = JSON.parse(body).fetch("error", {})
        fcm_detail = Array(error["details"]).find do |detail|
          detail["@type"] == "type.googleapis.com/google.firebase.fcm.v1.FcmError"
        end
        fcm_detail&.fetch("errorCode", nil) || error["status"] || "unknown"
      rescue JSON::ParserError
        "unknown"
      end
    end
  end
end

require "test_helper"

class Fcm::ClientTest < ActiveSupport::TestCase
  setup do
    Fcm::Client.reset!
    @credentials = { project_id: "maincourse-test", service_account: {} }.with_indifferent_access
  end

  teardown do
    Fcm::Client.reset!
  end

  test "sends notification and string custom data through HTTP v1" do
    stub_request(:post, "https://fcm.googleapis.com/v1/projects/maincourse-test/messages:send")
      .with(headers: { "Authorization" => "Bearer access-token" })
      .to_return(status: 200, body: JSON.generate(name: "message-id"))

    result = with_stubbed_authorization do
      Fcm::Client.push(
        token: "registration-token",
        alert: { title: "Hauptgang", body: "Cook it this week?" },
        custom: { campaign: "resurface", delivery_id: 42 }
      )
    end

    assert result.ok?
    assert_requested(:post, "https://fcm.googleapis.com/v1/projects/maincourse-test/messages:send") do |sent|
      body = JSON.parse(sent.body).fetch("message")
      body["fid"] == "registration-token" &&
        body.dig("data", "delivery_id") == "42" &&
        body.dig("android", "notification", "channel_id") == "recipe_reminders"
    end
  end

  test "returns the FCM-specific invalid-token reason" do
    stub_request(:post, "https://fcm.googleapis.com/v1/projects/maincourse-test/messages:send")
      .to_return(
        status: 404,
        body: JSON.generate(
          error: {
            status: "NOT_FOUND",
            details: [ {
              "@type" => "type.googleapis.com/google.firebase.fcm.v1.FcmError",
              "errorCode" => "UNREGISTERED"
            } ]
          }
        )
      )

    result = with_stubbed_authorization do
      Fcm::Client.push(token: "old", alert: { title: "H", body: "B" })
    end

    assert_not result.ok?
    assert_equal 404, result.status
    assert_equal "UNREGISTERED", result.reason
  end

  test "reads the existing Firebase credentials JSON" do
    firebase = {
      project_id: "maincourse-test",
      service_account_json: JSON.generate(type: "service_account", client_email: "push@example.test")
    }.with_indifferent_access

    Rails.application.credentials.stub(:firebase, firebase) do
      credentials = Fcm::Client.send(:credentials)

      assert_equal "maincourse-test", credentials.fetch(:project_id)
      assert_equal "service_account", credentials.dig(:service_account, :type)
      assert_equal "push@example.test", credentials.dig(:service_account, :client_email)
    end
  end

  test "reports malformed Firebase credentials without exposing their contents" do
    firebase = { project_id: "maincourse-test", service_account_json: "not-json" }.with_indifferent_access

    error = Rails.application.credentials.stub(:firebase, firebase) do
      assert_raises(Fcm::Client::MissingCredentialsError) { Fcm::Client.send(:credentials) }
    end

    assert_equal(
      "Rails credentials must contain firebase.project_id and valid firebase.service_account_json",
      error.message
    )
  end

  private

  def with_stubbed_authorization(&block)
    Fcm::Client.stub(:credentials, @credentials) do
      Fcm::Client.stub(:access_token, "access-token", &block)
    end
  end
end

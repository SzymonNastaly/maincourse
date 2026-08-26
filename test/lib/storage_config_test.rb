require "test_helper"

class StorageConfigTest < ActiveSupport::TestCase
  def storage_config
    @storage_config ||= YAML.safe_load(
      ERB.new(File.read(Rails.root.join("config/storage.yml"))).result,
      aliases: true
    )
  end

  test "r2 service is defined and uses S3" do
    assert storage_config.key?("r2"), "expected an :r2 service in config/storage.yml"
    assert_equal "S3", storage_config.dig("r2", "service")
  end

  test "r2 uses the EU jurisdiction endpoint" do
    endpoint = storage_config.dig("r2", "endpoint")
    assert_match(/\.eu\.r2\.cloudflarestorage\.com\z/, endpoint,
      "R2 must use the EU-jurisdiction endpoint")
  end

  test "r2 region is auto" do
    assert_equal "auto", storage_config.dig("r2", "region")
  end

  # Guards defect D3: aws-sdk-s3 >= 1.178 sends CRC32 checksum headers that
  # R2 rejects. Without these two keys every Active Storage upload fails.
  test "r2 disables aws-sdk default checksum behaviour" do
    assert_equal "when_required", storage_config.dig("r2", "request_checksum_calculation")
    assert_equal "when_required", storage_config.dig("r2", "response_checksum_validation")
  end
end

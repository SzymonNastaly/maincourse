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

  # Defect D3 predicted that aws-sdk-s3 >= 1.178 CRC32 checksum headers would be
  # rejected by R2. Tested 2026-08-26: it did not reproduce -- uploads succeed
  # without these keys, multipart included. They are kept as insurance against
  # SDK/R2 behaviour drift, and this test pins them so the choice stays deliberate.
  test "r2 disables aws-sdk default checksum behaviour" do
    assert_equal "when_required", storage_config.dig("r2", "request_checksum_calculation")
    assert_equal "when_required", storage_config.dig("r2", "response_checksum_validation")
  end
end

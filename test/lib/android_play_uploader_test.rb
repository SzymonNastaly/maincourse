require "test_helper"

class AndroidPlayUploaderTest < ActiveSupport::TestCase
  setup do
    @fastlane_directory = Rails.root.join("maincourse-android/fastlane")
    @environment = {
      "MAINCOURSE_AAB" => "/tmp/maincourse-0.2.0-2.aab",
      "MAINCOURSE_VERSION_NAME" => "0.2.0",
      "MAINCOURSE_VERSION_CODE" => "2",
      "MAINCOURSE_GIT_SHA" => "abc1234",
      "MAINCOURSE_PLAY_TRACK" => "alpha",
      "SUPPLY_JSON_KEY" => "/tmp/play-service-account.json"
    }
  end

  test "uploads the requested bundle as a completed closed-test release" do
    harness = FastfileHarness.new(version_codes: [ 1 ])

    with_environment(@environment) { harness.load_and_run(@fastlane_directory) }

    assert_equal "com.getmaincourse.app", harness.configured_package_name
    assert_equal [ {
      package_name: "com.getmaincourse.app",
      aab: "/tmp/maincourse-0.2.0-2.aab",
      json_key: "/tmp/play-service-account.json",
      track: "alpha",
      release_status: "completed",
      release_name: "0.2.0 (abc1234)",
      skip_upload_apk: true,
      skip_upload_metadata: true,
      skip_upload_images: true,
      skip_upload_screenshots: true,
      skip_upload_changelogs: true
    } ], harness.uploads
  end

  test "does not upload a version code already present on the track" do
    harness = FastfileHarness.new(version_codes: [ 1, 2 ])

    with_environment(@environment) { harness.load_and_run(@fastlane_directory) }

    assert_empty harness.uploads
  end

  test "requires release environment before contacting Google Play" do
    harness = FastfileHarness.new(version_codes: [ 1 ])

    error = assert_raises(KeyError) do
      with_environment(@environment.except("MAINCOURSE_AAB")) do
        harness.load_and_run(@fastlane_directory)
      end
    end

    assert_match "MAINCOURSE_AAB", error.message
    assert_empty harness.queries
    assert_empty harness.uploads
  end

  private

  def with_environment(values)
    previous = values.keys.to_h { |key| [ key, ENV[key] ] }
    values.each { |key, value| ENV[key] = value }
    missing_keys = @environment.keys - values.keys
    missing_previous = missing_keys.to_h { |key| [ key, ENV.delete(key) ] }
    yield
  ensure
    previous&.each { |key, value| value.nil? ? ENV.delete(key) : ENV[key] = value }
    missing_previous&.each { |key, value| value.nil? ? ENV.delete(key) : ENV[key] = value }
  end

  class FastfileHarness
    attr_reader :configured_package_name, :queries, :uploads

    def initialize(version_codes:)
      @version_codes = version_codes
      @lanes = {}
      @queries = []
      @uploads = []
    end

    def load_and_run(directory)
      instance_eval(File.read(directory.join("Appfile")), directory.join("Appfile").to_s)
      instance_eval(File.read(directory.join("Fastfile")), directory.join("Fastfile").to_s)
      instance_exec(&@lanes.fetch(:closed_test))
    end

    def package_name(value)
      @configured_package_name = value
    end

    def default_platform(*) = nil

    def platform(*)
      yield
    end

    def desc(*) = nil

    def lane(name, &block)
      @lanes[name] = block
    end

    def google_play_track_version_codes(**options)
      @queries << options
      @version_codes
    end

    def upload_to_play_store(**options)
      @uploads << options
    end
  end
end

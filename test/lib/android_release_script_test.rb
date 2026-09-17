require "test_helper"
require "fileutils"
require "open3"
require "tmpdir"

class AndroidReleaseScriptTest < ActiveSupport::TestCase
  FINGERPRINT = "04:2C:E7:F1:37:6B:01:4F:2C:F2:A1:ED:47:73:97:2C:C3:F3:A6:BC:83:51:F4:1D:4F:41:F1:E4:23:A5:33:FA"

  setup do
    @temporary_directory = Dir.mktmpdir("android-release-test")
    @repository = File.join(@temporary_directory, "repository")
    @command_log = File.join(@temporary_directory, "commands.log")
    FileUtils.mkdir_p(File.join(@repository, "bin"))
    FileUtils.mkdir_p(File.join(@repository, "maincourse-android", "app", "build", "outputs", "bundle", "release"))
    FileUtils.mkdir_p(File.join(@repository, "maincourse-android", "fake-jdk", "bin"))

    FileUtils.cp(Rails.root.join("bin/android-release"), File.join(@repository, "bin/android-release"))
    write("maincourse-android/version.properties", "versionName=0.1.0\nversionCode=1\n")
    write("maincourse-android/release.properties", "playTrack=alpha\nuploadCertificateSha256=#{FINGERPRINT}\n")
    write("maincourse-android/keystore.properties", "storeFile=upload-keystore.jks\nstorePassword=secret\nkeyAlias=maincourse-upload\nkeyPassword=secret\n")
    write("maincourse-android/upload-keystore.jks", "not-a-real-keystore")
    write("maincourse-android/play-service-account.json", "{}")
    write("maincourse-android/Gemfile", "source \"https://rubygems.org\"\n")
    write("maincourse-android/Gemfile.lock", "GEM\n")
    write(".gitignore", "maincourse-android/keystore.properties\nmaincourse-android/upload-keystore.jks\nmaincourse-android/play-service-account.json\nmaincourse-android/releases/\nmaincourse-android/**/build/\n")
    write_fake_commands
    git("init", "-q")
    git("config", "user.email", "test@example.com")
    git("config", "user.name", "Test")
    git("add", ".")
    git("commit", "-qm", "fixture")
  end

  teardown do
    FileUtils.remove_entry(@temporary_directory)
  end

  test "rejects a malformed version before running qualification" do
    _output, error, status = run_release("version-two")

    assert_not status.success?
    assert_match "X.Y.Z", error
    assert_not File.exist?(@command_log)
  end

  test "rejects unrelated working tree changes" do
    write("unrelated.txt", "mine\n")

    _output, error, status = run_release("0.2.0")

    assert_not status.success?
    assert_match "working tree", error
    assert_not File.exist?(@command_log)
  end

  test "rejects missing Play credentials and a mismatched upload certificate" do
    FileUtils.rm(File.join(@repository, "maincourse-android/play-service-account.json"))
    _output, missing_error, missing_status = run_release("0.2.0")

    assert_not missing_status.success?
    assert_match "play-service-account.json", missing_error

    write("maincourse-android/play-service-account.json", "{}")
    _output, mismatch_error, mismatch_status = run_release("0.2.0", "FAKE_CERT_FINGERPRINT" => "AA:BB")

    assert_not mismatch_status.success?
    assert_match "fingerprint", mismatch_error
    assert_not File.exist?(@command_log)
  end

  test "rejects missing signing material before changing the version" do
    FileUtils.rm(File.join(@repository, "maincourse-android/keystore.properties"))

    _output, error, status = run_release("0.2.0")

    assert_not status.success?
    assert_match "keystore.properties", error
    assert_equal "versionName=0.1.0\nversionCode=1\n", read("maincourse-android/version.properties")
    assert_not File.exist?(@command_log)
  end

  test "restores the version and never uploads when qualification fails" do
    _output, error, status = run_release("0.2.0", "FAKE_TEST_EXIT" => "9")

    assert_not status.success?
    assert_match "qualification", error
    assert_equal "versionName=0.1.0\nversionCode=1\n", read("maincourse-android/version.properties")
    assert_includes File.read(@command_log), "android-test"
    assert_not_includes File.read(@command_log), "fastlane"
  end

  test "rejects a bundle signed by a different certificate" do
    _output, error, status = run_release("0.2.0", "FAKE_AAB_FINGERPRINT" => "AA:BB")

    assert_not status.success?
    assert_match "bundle signing certificate", error
    assert_equal "versionName=0.1.0\nversionCode=1\n", read("maincourse-android/version.properties")
    assert_not_includes File.read(@command_log), "fastlane"
  end

  test "uploads a verified bundle and leaves the incremented version to commit" do
    output, error, status = run_release("0.2.0")

    assert status.success?, error
    assert_equal "versionName=0.2.0\nversionCode=2\n", read("maincourse-android/version.properties")
    artifact = File.join(@repository, "maincourse-android/releases/maincourse-0.2.0-2.aab")
    assert File.exist?(artifact)
    assert File.exist?("#{artifact}.sha256")
    assert_match "Uploaded MainCourse 0.2.0 (2) to alpha", output
    log = File.read(@command_log)
    assert_includes log, "android-test"
    assert_includes log, ":app:testReleaseUnitTest :app:verifyReleaseConfiguration :app:bundleRelease"
    assert_includes log, "fastlane android closed_test"
    assert_includes log, "MAINCOURSE_PLAY_TRACK=alpha"
    assert_includes log, "MAINCOURSE_VERSION_CODE=2"
  end

  test "preserves a failed upload and retries the exact same artifact without incrementing" do
    _output, _error, first_status = run_release("0.2.0", "FAKE_UPLOAD_EXIT" => "7")
    assert_not first_status.success?
    artifact = File.join(@repository, "maincourse-android/releases/maincourse-0.2.0-2.aab")
    original_checksum = File.read("#{artifact}.sha256")

    output, error, retry_status = run_release("0.2.0")

    assert retry_status.success?, error
    assert_equal "versionName=0.2.0\nversionCode=2\n", read("maincourse-android/version.properties")
    assert_equal original_checksum, File.read("#{artifact}.sha256")
    assert_match "Retrying verified", output
  end

  private

  def run_release(version, extra_environment = {})
    environment = {
      "COMMAND_LOG" => @command_log,
      "FIXTURE_ROOT" => @repository,
      "JAVA_HOME" => File.join(@repository, "maincourse-android/fake-jdk"),
      "FAKE_CERT_FINGERPRINT" => FINGERPRINT,
      "PATH" => "#{File.join(@repository, "fake-bin")}:#{ENV.fetch("PATH")}"
    }.merge(extra_environment)
    Open3.capture3(environment, File.join(@repository, "bin/android-release"), version, chdir: @repository)
  end

  def write_fake_commands
    write_executable("bin/android-test", <<~SH)
      #!/bin/bash
      echo "android-test $*" >> "$COMMAND_LOG"
      exit "${FAKE_TEST_EXIT:-0}"
    SH
    write_executable("bin/android-gradle", <<~SH)
      #!/bin/bash
      echo "android-gradle $*" >> "$COMMAND_LOG"
      mkdir -p "$FIXTURE_ROOT/maincourse-android/app/build/outputs/bundle/release"
      printf 'verified bundle' > "$FIXTURE_ROOT/maincourse-android/app/build/outputs/bundle/release/app-release.aab"
      exit "${FAKE_GRADLE_EXIT:-0}"
    SH
    write_executable("maincourse-android/fake-jdk/bin/keytool", <<~'SH')
      #!/bin/bash
      if [[ " $* " == *" -jarfile "* ]]; then
        printf 'Signer #1:\nCertificate fingerprints:\n\t SHA256: %s\n' "${FAKE_AAB_FINGERPRINT:-$FAKE_CERT_FINGERPRINT}"
      else
        printf 'Alias name: maincourse-upload\n\t SHA256: %s\n' "$FAKE_CERT_FINGERPRINT"
      fi
    SH
    write_executable("maincourse-android/fake-jdk/bin/jarsigner", <<~SH)
      #!/bin/bash
      echo "jarsigner $*" >> "$COMMAND_LOG"
      exit "${FAKE_JARSIGNER_EXIT:-0}"
    SH
    write_executable("fake-bin/jarsigner", <<~SH)
      #!/bin/bash
      exit 99
    SH
    write_executable("fake-bin/bundle", <<~SH)
      #!/bin/bash
      echo "bundle $* MAINCOURSE_PLAY_TRACK=$MAINCOURSE_PLAY_TRACK MAINCOURSE_VERSION_CODE=$MAINCOURSE_VERSION_CODE" >> "$COMMAND_LOG"
      exit "${FAKE_UPLOAD_EXIT:-0}"
    SH
  end

  def write_executable(path, contents)
    write(path, contents)
    FileUtils.chmod("+x", File.join(@repository, path))
  end

  def write(path, contents)
    absolute_path = File.join(@repository, path)
    FileUtils.mkdir_p(File.dirname(absolute_path))
    File.write(absolute_path, contents)
  end

  def read(path)
    File.read(File.join(@repository, path))
  end

  def git(*arguments)
    system("git", *arguments, chdir: @repository, exception: true, out: File::NULL)
  end
end

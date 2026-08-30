require "test_helper"

# The iOS entitlements files are not part of the Rails app, but this is a
# Ruby test suite that runs in bin/ci -- unlike Xcode tests, it runs on every
# push regardless of whether anyone remembers to run the iOS suite. That
# matters here because forgetting this one is a security problem, not just a
# broken build: see docs/superpowers/specs/2026-08-30-getmaincourse-domain-migration-design.md.
class IosEntitlementsTest < ActiveSupport::TestCase
  LEGACY_HOST = "cook.hauptgang.app"

  # The date the legacy hauptgang.app domain lapses and stops being ours to
  # protect. On or after this date, an associated-domain entry naming it is a
  # takeover vector: whoever registers the expired domain can serve an
  # apple-app-site-association file claiming this app's ID and hijack
  # universal links / shared web credentials for any install still trusting
  # that entitlement.
  ENTITLEMENT_REMOVAL_DEADLINE = Date.new(2026, 12, 1)

  ENTITLEMENTS_FILES = %w[
    hauptgang-ios/Hauptgang/Hauptgang.entitlements
    hauptgang-ios/Hauptgang/Hauptgang.Release.entitlements
  ].freeze

  test "legacy cook.hauptgang.app associated-domain entries are removed once the domain expires" do
    if Date.current >= ENTITLEMENT_REMOVAL_DEADLINE
      ENTITLEMENTS_FILES.each do |relative_path|
        contents = Rails.root.join(relative_path).read
        refute_includes contents, LEGACY_HOST,
          "#{relative_path} must no longer reference #{LEGACY_HOST}: that domain is expiring " \
          "and once someone else registers it, they can claim this app's associated domains " \
          "(applinks/webcredentials) and hijack universal links and shared web credentials for " \
          "any install still trusting this entitlement. Remove the applinks:#{LEGACY_HOST} and " \
          "webcredentials:#{LEGACY_HOST} entries. See " \
          "docs/superpowers/specs/2026-08-30-getmaincourse-domain-migration-design.md."
      end
    else
      # Before the deadline, the legacy entries are expected and required --
      # this keeps the test from being vacuously green with nothing to check.
      ENTITLEMENTS_FILES.each do |relative_path|
        contents = Rails.root.join(relative_path).read
        assert_includes contents, "applinks:#{LEGACY_HOST}",
          "expected #{relative_path} to still carry the legacy applinks entry before the removal deadline"
        assert_includes contents, "webcredentials:#{LEGACY_HOST}",
          "expected #{relative_path} to still carry the legacy webcredentials entry before the removal deadline"
      end
    end
  end
end

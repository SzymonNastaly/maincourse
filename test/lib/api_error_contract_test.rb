require "test_helper"
require "open3"
require "tmpdir"
require "fileutils"

class ApiErrorContractTest < ActiveSupport::TestCase
  test "unknown codes and invalid parameter schemas are rejected" do
    assert_raises(ArgumentError) { ApiErrorContract.validate!("errors", "forgotten_code") }
    assert_raises(ArgumentError) { ApiErrorContract.validate!("errors", "text_too_long", count: "50") }
    assert_raises(ArgumentError) { ApiErrorContract.validate!("errors", "text_too_long", secret: "value") }
    assert_equal "text_too_long", ApiErrorContract.validate!("errors", "text_too_long", count: 50)
    errors = ActiveModel::Errors.new(User.new)
    errors.add(:new_field, :blank)
    assert_raises(ArgumentError) { ApiErrorContract.validation_details(errors) }
  end

  test "CI rejects stale generation and then requires mappings in both clients" do
    with_contract_copy do |root|
      manifest = root.join("config/api_errors.yml")
      data = YAML.safe_load_file(manifest)
      data["errors"]["forgotten_code"] = { "meaning" => "Test new code", "params" => {} }
      File.write(manifest, YAML.dump(data))
      output, status = run_contract(root, "--check")
      refute status.success?
      assert_includes output, "stale"
      _, status = run_contract(root, "--generate")
      assert status.success?
      output, status = run_contract(root, "--check")
      refute status.success?
      assert_includes output, "forgotten_code"

      swift = root.join("hauptgang-ios/Hauptgang/Models/ApiErrorCode+Message.swift")
      File.write(swift, File.read(swift).sub("switch self {", "switch self {\ncase .forgotten_code: return String(localized: \"Name is required\", bundle: bundle, locale: locale)"))
      output, status = run_contract(root, "--check")
      refute status.success?, "Updating only Swift must still fail for Kotlin"
      assert_includes output, "forgotten_code"
    end
  end

  test "CI rejects catch-all known-code mappings and missing resources" do
    with_contract_copy do |root|
      swift = root.join("hauptgang-ios/Hauptgang/Models/ApiErrorCode+Message.swift")
      original = File.read(swift)
      File.write(swift, original.sub("switch self {", "switch self {\ndefault: return \"forgotten\""))
      output, status = run_contract(root, "--check")
      refute status.success?
      assert_includes output, "must not have a fallback"
      File.write(swift, original)

      %w[hauptgang-ios/Hauptgang/Resources/Localizable.xcstrings hauptgang-ios/ImportRecipeExtension/Localizable.xcstrings].each do |path|
        catalog = root.join(path)
        original = File.read(catalog)
        data = JSON.parse(original)
        data["strings"].delete("Enter a recipe URL.")
        File.write(catalog, JSON.generate(data))
        output, status = run_contract(root, "--check")
        refute status.success?
        assert_includes output, "missing"
        File.write(catalog, original)
      end
      resources = root.join("maincourse-android/app/src/main/res/values/api_errors.xml")
      File.write(resources, File.read(resources).sub(/<string name="api_error_url_required">.*?<\/string>/, ""))
      output, status = run_contract(root, "--check")
      refute status.success?
      assert_includes output, "api_error_url_required"
    end
  end

  private

  def with_contract_copy
    Dir.mktmpdir("api-error-contract") do |directory|
      root = Pathname(directory)
      paths = %w[bin/api-error-contract lib/api_error_contract.rb config/api_errors.yml]
      paths += Dir["hauptgang-ios/Hauptgang/Models/*Code*swift"]
      paths += Dir["hauptgang-ios/{Hauptgang/Resources,ImportRecipeExtension}/Localizable.xcstrings"]
      paths += Dir["maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/*Code*.kt"]
      paths += Dir["maincourse-android/app/src/main/res/values/*.xml"]
      paths.each do |path|
        FileUtils.mkdir_p(root.join(path).dirname)
        FileUtils.cp(Rails.root.join(path), root.join(path))
      end
      yield root
    end
  end

  def run_contract(root, argument)
    Open3.capture2e(RbConfig.ruby, root.join("bin/api-error-contract").to_s, argument)
  end
end

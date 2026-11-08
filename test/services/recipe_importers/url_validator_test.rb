require "test_helper"

module RecipeImporters
  class UrlValidatorTest < ActiveSupport::TestCase
    # ===================
    # VALID URLS
    # ===================

    test "accepts valid http URL" do
      result = validate_url("http://example.com/recipe", resolve_to: "93.184.216.34")

      assert result.success?
      assert_nil result.error
    end

    test "accepts valid https URL" do
      result = validate_url("https://example.com/recipe", resolve_to: "93.184.216.34")

      assert result.success?
    end

    test "accepts URL with allowed port 8080" do
      result = validate_url("https://example.com:8080/recipe", resolve_to: "93.184.216.34")

      assert result.success?
    end

    test "accepts URL with allowed port 8443" do
      result = validate_url("https://example.com:8443/recipe", resolve_to: "93.184.216.34")

      assert result.success?
    end

    # ===================
    # BLANK/NIL URLS
    # ===================

    test "rejects blank URL" do
      result = UrlValidator.new("").validate

      assert_not result.success?
      assert_equal "URL cannot be blank", result.error
    end

    test "rejects nil URL" do
      result = UrlValidator.new(nil).validate

      assert_not result.success?
      assert_equal "URL cannot be blank", result.error
    end

    # ===================
    # INVALID SCHEMES
    # ===================

    test "rejects file:// scheme" do
      result = UrlValidator.new("file:///etc/passwd").validate

      assert_not result.success?
      assert_match(/only http and https/i, result.error)
    end

    test "rejects ftp:// scheme" do
      result = UrlValidator.new("ftp://example.com/file").validate

      assert_not result.success?
      assert_match(/only http and https/i, result.error)
    end

    test "rejects javascript: scheme" do
      result = UrlValidator.new("javascript:alert(1)").validate

      assert_not result.success?
      assert_match(/only http and https/i, result.error)
    end

    test "rejects data: scheme" do
      result = UrlValidator.new("data:text/html,<h1>test</h1>").validate

      assert_not result.success?
    end

    # ===================
    # LOCALHOST VARIATIONS
    # ===================

    test "rejects localhost hostname" do
      result = UrlValidator.new("http://localhost/admin").validate

      assert_not result.success?
      assert_match(/hostname is not allowed/i, result.error)
    end

    test "rejects LOCALHOST (case insensitive)" do
      result = UrlValidator.new("http://LOCALHOST/admin").validate

      assert_not result.success?
      assert_match(/hostname is not allowed/i, result.error)
    end

    test "rejects 127.0.0.1" do
      result = validate_url("http://127.0.0.1/admin", resolve_to: "127.0.0.1")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects 127.0.0.2 (other loopback)" do
      result = validate_url("http://127.0.0.2/admin", resolve_to: "127.0.0.2")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects IPv6 loopback ::1" do
      result = validate_url("http://[::1]/admin", resolve_to: "::1")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    # ===================
    # PRIVATE IP RANGES
    # ===================

    test "rejects 10.x.x.x private IP" do
      result = validate_url("http://10.0.0.1/admin", resolve_to: "10.0.0.1")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects 172.16.x.x private IP" do
      result = validate_url("http://172.16.0.1/admin", resolve_to: "172.16.0.1")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects 172.31.x.x private IP (edge of range)" do
      result = validate_url("http://172.31.255.255/admin", resolve_to: "172.31.255.255")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects 192.168.x.x private IP" do
      result = validate_url("http://192.168.1.1/admin", resolve_to: "192.168.1.1")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    # ===================
    # LINK-LOCAL ADDRESSES
    # ===================

    test "rejects 169.254.x.x link-local" do
      result = validate_url("http://169.254.169.254/latest/meta-data", resolve_to: "169.254.169.254")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    # ===================
    # SPECIAL ADDRESSES
    # ===================

    test "rejects 0.0.0.0" do
      result = validate_url("http://0.0.0.0/admin", resolve_to: "0.0.0.0")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects carrier-grade NAT address" do
      result = validate_url("https://example.com/", resolve_to: "100.64.0.1")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects unique local IPv6 address" do
      result = validate_url("https://example.com/", resolve_to: "fd00:ec2::254")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects IPv4-mapped form of a private address" do
      result = validate_url("https://example.com/", resolve_to: "::ffff:169.254.169.254")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    # ===================
    # BLOCKED HOSTNAMES
    # ===================

    test "rejects .local hostname" do
      result = UrlValidator.new("http://server.local/admin").validate

      assert_not result.success?
      assert_match(/hostname is not allowed/i, result.error)
    end

    test "rejects .internal hostname" do
      result = UrlValidator.new("http://api.internal/admin").validate

      assert_not result.success?
      assert_match(/hostname is not allowed/i, result.error)
    end

    test "rejects .localhost hostname" do
      result = UrlValidator.new("http://app.localhost/admin").validate

      assert_not result.success?
      assert_match(/hostname is not allowed/i, result.error)
    end

    # ===================
    # USERINFO IN URL
    # ===================

    test "rejects URL with username" do
      result = UrlValidator.new("http://user@example.com/page").validate

      assert_not result.success?
      assert_match(/username or password/i, result.error)
    end

    test "rejects URL with username and password" do
      result = UrlValidator.new("http://user:pass@example.com/page").validate

      assert_not result.success?
      assert_match(/username or password/i, result.error)
    end

    # ===================
    # MISSING/BLANK HOST
    # ===================

    test "rejects URL with blank host" do
      result = UrlValidator.new("http:///path").validate

      assert_not result.success?
      assert_match(/valid host/i, result.error)
    end

    # ===================
    # SUSPICIOUS PORTS
    # ===================

    test "rejects SSH port 22" do
      result = UrlValidator.new("http://example.com:22/").validate

      assert_not result.success?
      assert_match(/port 22 is not allowed/i, result.error)
    end

    test "rejects SMTP port 25" do
      result = UrlValidator.new("http://example.com:25/").validate

      assert_not result.success?
      assert_match(/port 25 is not allowed/i, result.error)
    end

    test "rejects arbitrary high port" do
      result = UrlValidator.new("http://example.com:9999/").validate

      assert_not result.success?
      assert_match(/port 9999 is not allowed/i, result.error)
    end

    # ===================
    # DNS RESOLUTION
    # ===================

    test "rejects hostname that resolves to private IP" do
      result = validate_url("http://evil.example.com/", resolve_to: "192.168.1.1")

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects hostname when any resolved address is private" do
      result = validate_url("https://example.com/", resolve_to: [ "93.184.216.34", "10.0.0.1" ])

      assert_not result.success?
      assert_match(/private or internal/i, result.error)
    end

    test "rejects hostname that cannot be resolved" do
      result = validate_url("http://nonexistent.example.com/", resolve_to: nil)

      assert_not result.success?
      assert_match(/could not resolve/i, result.error)
    end

    # ===================
    # INVALID URL FORMAT
    # ===================

    test "rejects malformed URL" do
      result = UrlValidator.new("not a valid url at all").validate

      assert_not result.success?
    end

    private

    def validate_url(url, resolve_to:)
      resolver = StubResolver.new(resolve_to)
      UrlValidator.new(url, resolver: resolver).validate
    end

    class StubResolver
      def initialize(ips)
        @ips = Array(ips)
      end

      def getaddresses(_host)
        @ips
      end
    end
  end
end

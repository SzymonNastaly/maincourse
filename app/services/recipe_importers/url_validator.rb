require "ipaddr"
require "resolv"

module RecipeImporters
  # Validates URLs before fetching to prevent SSRF attacks
  # Blocks private IPs, localhost, and suspicious URLs
  class UrlValidator
    Result = Data.define(:success?, :error)

    class BlockedRedirect < Faraday::Error; end

    # A follow_redirects callback, which Faraday runs before requesting each hop.
    REJECT_UNSAFE_REDIRECT = lambda do |_response_env, redirect_env|
      validation = new(redirect_env.url.to_s).validate
      raise BlockedRedirect, validation.error unless validation.success?
    end

    ALLOWED_SCHEMES = %w[http https].freeze
    ALLOWED_PORTS = [ 80, 443, 8080, 8443 ].freeze

    BLOCKED_HOSTNAME_PATTERNS = [
      /\Alocalhost\z/i,
      /\.local\z/i,
      /\.internal\z/i,
      /\.localhost\z/i
    ].freeze

    PRIVATE_IP_RANGES = [
      IPAddr.new("127.0.0.0/8"),      # Loopback
      IPAddr.new("::1/128"),          # IPv6 loopback
      IPAddr.new("10.0.0.0/8"),       # Private class A
      IPAddr.new("172.16.0.0/12"),    # Private class B
      IPAddr.new("192.168.0.0/16"),   # Private class C
      IPAddr.new("100.64.0.0/10"),    # Carrier-grade NAT
      IPAddr.new("fc00::/7"),         # Unique local IPv6
      IPAddr.new("169.254.0.0/16"),   # Link-local IPv4
      IPAddr.new("fe80::/10"),        # Link-local IPv6
      IPAddr.new("0.0.0.0/32"),       # Unspecified IPv4
      IPAddr.new("::/128")            # Unspecified IPv6
    ].freeze

    def initialize(url, resolver: Resolv)
      @url = url
      @resolver = resolver
    end

    def validate
      return failure("URL cannot be blank") if @url.blank?

      uri = parse_uri
      return failure("Invalid URL format") unless uri

      return failure("Only http and https URLs are allowed") unless valid_scheme?(uri)
      host = uri.host
      return failure("URL must have a valid host") if host.blank?
      return failure("URLs with username or password are not allowed") if has_userinfo?(uri)
      return failure("Port #{uri.port} is not allowed") unless valid_port?(uri)
      return failure("This hostname is not allowed") if blocked_hostname?(host)

      resolved_ips = @resolver.getaddresses(host)
      return failure("Could not resolve hostname") if resolved_ips.empty?
      return failure("URLs pointing to private or internal addresses are not allowed") if resolved_ips.any? { |ip| private_ip?(ip) }

      success
    end

    private

    def parse_uri
      URI.parse(@url)
    rescue URI::InvalidURIError
      nil
    end

    def valid_scheme?(uri)
      ALLOWED_SCHEMES.include?(uri.scheme&.downcase)
    end

    def has_userinfo?(uri)
      uri.userinfo.present? || uri.user.present? || uri.password.present?
    end

    def valid_port?(uri)
      port = uri.port
      return true if port.nil?

      ALLOWED_PORTS.include?(port)
    end

    def blocked_hostname?(host)
      BLOCKED_HOSTNAME_PATTERNS.any? { |pattern| pattern.match?(host) }
    end

    def private_ip?(ip_string)
      ip = IPAddr.new(ip_string).native
      PRIVATE_IP_RANGES.any? { |range| range.include?(ip) }
    rescue IPAddr::InvalidAddressError
      true
    end

    def success
      Result.new(success?: true, error: nil)
    end

    def failure(message)
      Result.new(success?: false, error: message)
    end
  end
end

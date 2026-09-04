module Oauth
  class IdTokenVerifier
    PROVIDERS = {
      "apple" => {
        issuers: [ "https://appleid.apple.com" ],
        jwks_url: "https://appleid.apple.com/auth/keys"
      },
      "google" => {
        issuers: [ "https://accounts.google.com", "accounts.google.com" ],
        jwks_url: "https://www.googleapis.com/oauth2/v3/certs"
      }
    }.freeze
    CLOCK_SKEW = 60
    JWKS_TTL = 1.hour

    def self.verify!(provider:, token:, audiences:, nonce:)
      new(provider:, token:, audiences:, nonce:).verify!
    end

    def initialize(provider:, token:, audiences:, nonce:)
      @provider = provider.to_s
      @token = token.to_s
      @audiences = Array(audiences).compact_blank
      @nonce = nonce.to_s
    end

    def verify!
      configuration = PROVIDERS.fetch(provider) { raise Error, "Unsupported OAuth provider" }
      raise Error, "Missing identity token" if token.blank?
      raise UnavailableError, "OAuth provider is not configured" if audiences.empty?

      payload = decode(configuration)
      validate_claims!(payload, configuration)

      {
        uid: payload.fetch("sub"),
        email: payload.fetch("email"),
        email_verified: truthy?(payload["email_verified"]),
        name: payload["name"]
      }
    rescue JWT::DecodeError, KeyError => error
      raise Error, "Invalid identity token: #{error.message}"
    end

    private
      attr_reader :provider, :token, :audiences, :nonce

      def decode(configuration)
        JWT.decode(
          token,
          nil,
          true,
          algorithms: [ "RS256" ],
          jwks: jwks_loader(configuration),
          verify_expiration: true,
          exp_leeway: CLOCK_SKEW
        ).first
      end

      def validate_claims!(payload, configuration)
        raise Error, "Invalid token issuer" unless configuration[:issuers].include?(payload["iss"])
        raise Error, "Invalid token audience" if (Array(payload["aud"]) & audiences).empty?
        raise Error, "Invalid token subject" if payload["sub"].blank?
        raise Error, "Provider did not return an email" if payload["email"].blank?
        raise Error, "Provider did not verify the email" unless truthy?(payload["email_verified"])
        raise Error, "Identity token has no expiry" unless payload["exp"].present?
        raise Error, "Identity token has no issued-at time" unless payload["iat"].present?
        raise Error, "Identity token was issued in the future" if payload["iat"].to_i > Time.current.to_i + CLOCK_SKEW
        if nonce.present?
          raise Error, "Invalid OAuth nonce" unless secure_match?(payload["nonce"].to_s, nonce)
        end
      end

      def jwks_loader(configuration)
        lambda do |options|
          Rails.cache.delete(cache_key) if options[:invalidate]
          jwks(configuration)
        end
      end

      def jwks(configuration)
        Rails.cache.fetch(cache_key, expires_in: JWKS_TTL) do
          response = Faraday.get(configuration[:jwks_url])
          raise UnavailableError, "Could not fetch provider signing keys" unless response.success?

          JSON.parse(response.body)
        end
      rescue Faraday::Error, JSON::ParserError => error
        raise UnavailableError, "Could not fetch provider signing keys: #{error.message}"
      end

      def cache_key
        "oauth/#{provider}/jwks"
      end

      def truthy?(value)
        value == true || value == "true"
      end

      def secure_match?(actual, expected)
        actual.bytesize == expected.bytesize && ActiveSupport::SecurityUtils.secure_compare(actual, expected)
      end
  end
end

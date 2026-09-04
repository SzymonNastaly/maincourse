module Oauth
  class AppleClient
    ISSUER = "https://appleid.apple.com"
    TOKEN_URL = "#{ISSUER}/auth/token"
    REVOKE_URL = "#{ISSUER}/auth/revoke"
    CLIENT_SECRET_TTL = 5.minutes

    def initialize(
      team_id: Configuration.apple_team_id,
      key_id: Configuration.apple_key_id,
      private_key: Configuration.apple_private_key
    )
      @team_id = team_id
      @key_id = key_id
      @private_key = private_key
    end

    def exchange_code!(code:, client_id:)
      response = post(TOKEN_URL, {
        client_id:,
        client_secret: client_secret(client_id:),
        code:,
        grant_type: "authorization_code"
      })
      body = parse_response(response)
      raise Error, "Apple did not return an identity token" if body["id_token"].blank?

      body
    end

    def revoke!(refresh_token:, client_id:)
      response = post(REVOKE_URL, {
        client_id:,
        client_secret: client_secret(client_id:),
        token: refresh_token,
        token_type_hint: "refresh_token"
      })
      parse_response(response)
      true
    end

    private
      def client_secret(client_id:)
        now = Time.current.to_i
        JWT.encode(
          {
            iss: team_id,
            iat: now,
            exp: now + CLIENT_SECRET_TTL.to_i,
            aud: ISSUER,
            sub: client_id
          },
          OpenSSL::PKey::EC.new(private_key),
          "ES256",
          { kid: key_id }
        )
      rescue OpenSSL::PKey::PKeyError, JWT::EncodeError => error
        raise UnavailableError, "Apple credentials are invalid: #{error.message}"
      end

      def post(url, params)
        Faraday.post(url, URI.encode_www_form(params), "Content-Type" => "application/x-www-form-urlencoded")
      rescue Faraday::Error => error
        raise UnavailableError, "Apple request failed: #{error.message}"
      end

      def parse_response(response)
        body = response.body.present? ? JSON.parse(response.body) : {}
        return body if response.success?

        message = body["error_description"].presence || body["error"].presence || "HTTP #{response.status}"
        configuration_error = %w[invalid_client unauthorized_client].include?(body["error"])
        error_class = response.status >= 500 || configuration_error ? UnavailableError : Error
        raise error_class, "Apple request failed: #{message}"
      rescue JSON::ParserError
        raise UnavailableError, "Apple returned an invalid response"
      end

      attr_reader :team_id, :key_id, :private_key
  end
end

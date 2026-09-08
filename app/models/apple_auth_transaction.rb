class AppleAuthTransaction < ApplicationRecord
  class InvalidStartError < StandardError; end
  class AuthorizationStateError < StandardError; end
  class ExchangeError < StandardError; end

  RELEASE_RETURN_URI = "https://app.getmaincourse.com/android/auth/apple".freeze
  DEBUG_RETURN_URI = "com.getmaincourse.app.debug:/oauth/apple".freeze
  CODE_CHALLENGE_PATTERN = /\A[A-Za-z0-9_-]{43}\z/
  CODE_VERIFIER_PATTERN = /\A[A-Za-z0-9\-._~]{43,128}\z/
  DIGEST_PATTERN = /\A[0-9a-f]{64}\z/
  TRANSACTION_LIFETIME = 5.minutes
  EXCHANGE_LIFETIME = 1.minute
  SQLITE_BUSY_RETRIES = 3

  belongs_to :user, optional: true

  validates :handle_digest, presence: true, uniqueness: true, format: { with: DIGEST_PATTERN }
  validates :exchange_digest, uniqueness: true, allow_nil: true, format: { with: DIGEST_PATTERN }
  validates :code_challenge, presence: true, format: { with: CODE_CHALLENGE_PATTERN }
  validates :return_uri, inclusion: { in: [ RELEASE_RETURN_URI, DEBUG_RETURN_URI ] }
  validates :expires_at, presence: true

  def self.start!(code_challenge:, callback:)
    raise InvalidStartError unless code_challenge.is_a?(String) && CODE_CHALLENGE_PATTERN.match?(code_challenge)

    return_uri = return_uri_for(callback)
    where("expires_at < ?", 1.day.ago).delete_all
    raw_handle = generate_opaque_value
    transaction = create!(
      handle_digest: digest(raw_handle),
      code_challenge: code_challenge,
      return_uri: return_uri,
      expires_at: TRANSACTION_LIFETIME.from_now
    )
    [ transaction, raw_handle ]
  end

  def self.find_by_handle(raw_handle)
    return unless raw_handle.is_a?(String) && raw_handle.present?

    find_by(handle_digest: digest(raw_handle))
  end

  def authorizable!
    if expires_at <= Time.current || authorized_at.present? || consumed_at.present? || user_id.present? || exchange_digest.present?
      raise AuthorizationStateError
    end

    self
  end

  # The caller must hold this record's row lock while performing any Identity
  # side effects and invoking this method.
  def authorize!(user:)
    authorizable!
    raw_exchange_code = self.class.send(:generate_opaque_value)
    update!(
      user: user,
      exchange_digest: self.class.send(:digest, raw_exchange_code),
      authorized_at: Time.current
    )
    raw_exchange_code
  end

  # The caller must hold this record's row lock. A second browser grant may
  # proceed only after this state has been set by the first callback.
  def mark_confirmation_required!
    authorizable!
    raise AuthorizationStateError if confirmation_required_at.present?

    update!(confirmation_required_at: Time.current)
    self
  end

  def confirmation_required?
    confirmation_required_at.present?
  end

  def exchange_expires_at
    return unless authorized_at

    [ authorized_at + EXCHANGE_LIFETIME, expires_at ].min
  end

  def exchange!(exchange_code:, code_verifier:, device_name:, onboarding_device_id:)
    attempts = 0

    begin
      with_lock do
        validate_exchange!(exchange_code:, code_verifier:)
        token_record, raw_token = ApiToken.generate_for(user, name: device_name)
        OnboardingResponse.link_to_user!(device_id: onboarding_device_id, user: user)
        update!(consumed_at: Time.current)
        session_payload(token_record:, raw_token:)
      end
    rescue ActiveRecord::StatementInvalid => error
      attempts += 1
      if sqlite_busy?(error) && attempts <= SQLITE_BUSY_RETRIES
        sleep(0.01 * attempts)
        retry
      end
      raise
    end
  end

  private
    def self.return_uri_for(callback)
      raise InvalidStartError unless callback.is_a?(String)

      case callback
      when "release" then RELEASE_RETURN_URI
      when "debug"
        raise InvalidStartError unless Rails.env.local?

        DEBUG_RETURN_URI
      else
        raise InvalidStartError
      end
    end

    def self.generate_opaque_value
      SecureRandom.urlsafe_base64(32, false)
    end

    def self.digest(value)
      Digest::SHA256.hexdigest(value)
    end

    def validate_exchange!(exchange_code:, code_verifier:)
      raise ExchangeError unless authorized_at && user_id && exchange_digest
      raise ExchangeError if consumed_at || exchange_expires_at <= Time.current
      raise ExchangeError unless valid_opaque_value?(exchange_code)
      raise ExchangeError unless CODE_VERIFIER_PATTERN.match?(code_verifier.to_s) && code_verifier.is_a?(String)

      supplied_exchange_digest = self.class.send(:digest, exchange_code)
      raise ExchangeError unless ActiveSupport::SecurityUtils.secure_compare(supplied_exchange_digest, exchange_digest)

      supplied_challenge = Base64.urlsafe_encode64(Digest::SHA256.digest(code_verifier), padding: false)
      raise ExchangeError unless ActiveSupport::SecurityUtils.secure_compare(supplied_challenge, code_challenge)
    end

    def valid_opaque_value?(value)
      value.is_a?(String) && CODE_CHALLENGE_PATTERN.match?(value)
    end

    def sqlite_busy?(error)
      error.cause&.class&.name == "SQLite3::BusyException"
    end

    def session_payload(token_record:, raw_token:)
      {
        token: raw_token,
        expires_at: token_record.expires_at,
        user: {
          id: user.id,
          name: user.name,
          email: user.email_address,
          lifecycle_notifications_enabled: user.lifecycle_notifications_enabled
        }
      }
    end
end

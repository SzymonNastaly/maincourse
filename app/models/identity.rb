class Identity < ApplicationRecord
  PROVIDERS = %w[apple google].freeze

  belongs_to :user

  serialize :apple_refresh_tokens, coder: JSON, type: Hash
  encrypts :apple_refresh_tokens

  normalizes :provider, with: ->(provider) { provider.to_s.strip.downcase }
  normalizes :uid, with: ->(uid) { uid.to_s.strip }
  normalizes :email, with: ->(email) { email.to_s.strip.downcase }

  validates :provider, inclusion: { in: PROVIDERS }
  validates :uid, presence: true, uniqueness: { scope: :provider }
  validates :email, presence: true

  scope :apple, -> { where(provider: "apple") }

  def self.authenticate!(provider:, uid:, email:, email_verified:, name: nil,
                         apple_refresh_token: nil, apple_client_id: nil)
    provider = normalize_value_for(:provider, provider)
    uid = normalize_value_for(:uid, uid)
    email = normalize_value_for(:email, email)

    unless PROVIDERS.include?(provider) && uid.present? && email.present? && email_verified
      raise Oauth::Error, "The provider did not return a verified identity"
    end
    if apple_refresh_token.present? && apple_client_id.blank?
      raise Oauth::Error, "Apple refresh token has no client identifier"
    end

    transaction do
      identity = find_by(provider: provider, uid: uid)
      return update_existing_identity!(identity, email:, apple_refresh_token:, apple_client_id:) if identity

      attributes = identity_attributes(
        provider:, uid:, email:, apple_refresh_token:, apple_client_id:
      )
      user = User.find_by(email_address: email)

      if user
        if user.password_digest.present?
          raise Oauth::LinkRequiredError, "A password account already exists for this email"
        end
        user.update!(name: normalized_name(name, email)) if user.name.blank?
        user.identities.create!(attributes)
      else
        user = User.new(email_address: email, name: normalized_name(name, email))
        user.identities.build(attributes)
        user.save!
      end

      user
    end
  end

  def apple_refresh_token_for(client_id)
    apple_refresh_tokens[client_id]
  end

  class << self
    private
      def update_existing_identity!(identity, email:, apple_refresh_token:, apple_client_id:)
        attributes = { email: email }
        if apple_refresh_token.present?
          attributes[:apple_refresh_tokens] = identity.apple_refresh_tokens.merge(
            apple_client_id => apple_refresh_token
          )
        end
        identity.update!(attributes)
        identity.user
      end

      def identity_attributes(provider:, uid:, email:, apple_refresh_token:, apple_client_id:)
        attributes = { provider:, uid:, email: }
        if apple_refresh_token.present?
          attributes[:apple_refresh_tokens] = { apple_client_id => apple_refresh_token }
        end
        attributes
      end

      def normalized_name(name, email)
        name.to_s.strip.presence&.first(50) || email.split("@", 2).first.tr("._-", " ").titleize.first(50)
      end
  end
end

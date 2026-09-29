class DeviceToken < ApplicationRecord
  ACTIVE_WINDOW = 90.days

  PROVIDERS = %w[apns fcm].freeze
  ENVIRONMENTS = %w[production sandbox].freeze

  belongs_to :user

  validates :token, presence: true, uniqueness: { scope: :provider }
  validates :provider, inclusion: { in: PROVIDERS }
  validates :environment, inclusion: { in: ENVIRONMENTS }

  scope :active, -> { where("last_used_at IS NULL OR last_used_at > ?", ACTIVE_WINDOW.ago) }

  # Each installation reports its own app language: a phone and an iPad on the same
  # account can differ. A registration without one (older app versions) or with a
  # language we do not ship is stored as nil and receives English.
  def self.register!(user:, token:, environment:, provider: "apns", language: nil)
    record = find_or_initialize_by(provider: provider, token: token)
    record.user = user
    record.environment = environment
    record.language = supported_language(language)
    record.last_used_at = Time.current
    record.save!
    record
  end

  # Accepts a language tag such as "de", "pl-PL" or "en_GB" and returns the matching
  # shipped Rails locale as a string, or nil.
  def self.supported_language(value)
    value.to_s.strip.downcase.split(/[-_]/).first.presence_in(I18n.available_locales.map(&:to_s))
  end

  def locale
    language&.to_sym || I18n.default_locale
  end
end

class PasswordsMailer < ApplicationMailer
  # Communication language is independent of the requesting browser. Until the
  # account-level preference ships, keep the entire English email consistent.
  around_action :with_communication_locale

  def reset(user)
    @user = user
    mail subject: "Reset your password", to: user.email_address
  end

  private

  def with_communication_locale(&action)
    I18n.with_locale(:en, &action)
  end
end

class PasswordsMailer < ApplicationMailer
  def reset(user)
    @user = user
    # Render in the account's communication language, never the requesting browser's.
    I18n.with_locale(user.communication_locale) do
      mail to: user.email_address
    end
  end
end

class PasswordsController < ApplicationController
  allow_unauthenticated_access
  layout "authentication", only: [ :new, :edit ]
  before_action :set_user_by_token, only: %i[ edit update ]
  rate_limit to: 10, within: 3.minutes, only: :create, with: -> { redirect_to new_password_path, alert: t("web.flash.try_later") }

  def new
  end

  def create
    if user = User.find_by(email_address: params[:email_address])
      PasswordsMailer.reset(user).deliver_later
    end

    redirect_to new_session_path, notice: t("web.flash.password_reset_sent")
  end

  def edit
  end

  def update
    if params[:password].present? && @user.update(params.permit(:password, :password_confirmation))
      @user.sessions.destroy_all
      redirect_to new_session_path, notice: t("web.flash.password_reset")
    else
      redirect_to edit_password_path(params[:token]), alert: t("web.flash.passwords_mismatch")
    end
  end

  private
    def set_user_by_token
      @user = User.find_by_password_reset_token!(params[:token])
    rescue ActiveSupport::MessageVerifier::InvalidSignature
      redirect_to new_password_path, alert: t("web.flash.password_link_invalid")
    end
end

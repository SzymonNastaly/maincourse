class RegistrationsController < ApplicationController
  allow_unauthenticated_access
  layout "authentication", only: [ :new ]
  def new
    @user = User.new
  end

  def create
    # Start with the language the sign-up page was shown in; Settings changes it later.
    @user = User.new(user_params.merge(communication_language: I18n.locale.to_s))
    if @user.save
        start_new_session_for @user
        redirect_to root_path
    else
      render :new, status: :unprocessable_entity
    end
  end

  private
  def user_params
    params.expect(user: [ :name, :email_address, :password, :password_confirmation ])
  end
end

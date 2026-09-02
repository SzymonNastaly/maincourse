class SettingsController < ApplicationController
  def edit
    @user = Current.user
  end

  def update
    @user = Current.user

    if @user.update(settings_params)
      redirect_to edit_settings_path, notice: "Settings updated."
    else
      render :edit, status: :unprocessable_entity
    end
  end

  private

  def settings_params
    params.expect(user: [ :name, :lifecycle_notifications_enabled ])
  end
end

class AccountsController < ApplicationController
  CONFIRMATION_PHRASE = "DELETE".freeze

  def show
    @user = Current.user
  end

  def destroy
    unless params[:confirmation].to_s.strip == CONFIRMATION_PHRASE
      return redirect_to account_path, alert: "Type #{CONFIRMATION_PHRASE} to confirm."
    end

    user = Current.user
    terminate_session
    # Ownership of shared cookbooks transfers to the oldest remaining
    # collaborator; see User#handle_owned_cookbooks!.
    user.destroy!

    redirect_to new_session_path, notice: "Your account has been deleted.", status: :see_other
  end
end

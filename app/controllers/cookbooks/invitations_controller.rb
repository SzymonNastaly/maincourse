module Cookbooks
  class InvitationsController < ApplicationController
    # POST /cookbooks/:cookbook_id/invitation
    def create
      cookbook = Current.user.cookbooks.find(params[:cookbook_id])

      if cookbook.personal?
        return redirect_to cookbooks_path, alert: "You cannot invite anyone to your personal cookbook."
      end

      unless cookbook.owner?(Current.user)
        return redirect_to cookbooks_path, alert: "Only the owner can invite people."
      end

      # One active invite at a time, same as the API.
      cookbook.cookbook_invitations.pending.update_all(status: :expired)
      cookbook.cookbook_invitations.create!(inviter: Current.user)

      redirect_to cookbooks_path, notice: "Invite link created."
    rescue ActiveRecord::RecordNotFound
      redirect_to cookbooks_path, alert: "Cookbook not found."
    end
  end
end

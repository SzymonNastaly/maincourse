module Cookbooks
  class InvitationsController < ApplicationController
    # POST /cookbooks/:cookbook_id/invitation
    def create
      cookbook = Current.user.cookbooks.find(params[:cookbook_id])

      if cookbook.personal?
        return redirect_to cookbooks_path, alert: t("web.flash.cannot_invite_personal")
      end

      unless cookbook.owner?(Current.user)
        return redirect_to cookbooks_path, alert: t("web.flash.only_owner_invite")
      end

      # One active invite at a time, same as the API.
      cookbook.cookbook_invitations.pending.update_all(status: :expired)
      cookbook.cookbook_invitations.create!(inviter: Current.user)

      redirect_to cookbooks_path, notice: t("web.flash.invite_created")
    rescue ActiveRecord::RecordNotFound
      redirect_to cookbooks_path, alert: t("web.flash.cookbook_not_found")
    end
  end
end

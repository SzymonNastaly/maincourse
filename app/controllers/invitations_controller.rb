# The landing page for a cookbook invite link. Also the target of the iOS
# universal link, so it must keep working for signed-out visitors.
class InvitationsController < ApplicationController
  allow_unauthenticated_access only: :show
  layout "authentication"

  # Signed-out visitors still need Current.user resolved so the page can tell
  # "sign in first" from "you are already a member".
  prepend_before_action :resume_session, only: :show
  before_action :set_invitation

  def show
    # Coming back here after signing in is the whole point of the link.
    session[:return_to_after_authenticating] = request.url if Current.user.nil?

    @already_member = signed_in_member?
    @blocked_by_existing_shared = Current.user.present? &&
                                  Current.user.shared_cookbook.present? &&
                                  !@already_member
  end

  def accept
    return redirect_to invite_path(params[:token]), alert: "This invitation is no longer available." unless @invitation&.pending? && !@invitation.time_expired?

    if signed_in_member?
      switch_cookbook(@invitation.cookbook)
      return redirect_to recipes_path, notice: "You are already in #{@invitation.cookbook.name}."
    end

    joined = Current.user.with_lock do
      next nil if Current.user.shared_cookbook.present?

      CookbookInvitation.transaction do
        CookbookMembership.create!(cookbook: @invitation.cookbook, user: Current.user, role: :collaborator)
        @invitation.accepted!
      end

      @invitation.cookbook
    end

    if joined.nil?
      return redirect_to invite_path(params[:token]),
                         alert: "You already have a shared cookbook. Leave it before joining another."
    end

    switch_cookbook(joined)
    redirect_to recipes_path, notice: "You joined #{joined.name}."
  end

  def reject
    @invitation&.rejected! if @invitation&.pending?
    redirect_to recipes_path
  end

  private

  def set_invitation
    @token = params[:token]
    @invitation = CookbookInvitation.includes(:cookbook, :inviter).find_by(token: @token)
  end

  def signed_in_member?
    Current.user.present? && @invitation.present? &&
      @invitation.cookbook.cookbook_memberships.exists?(user: Current.user)
  end
end
